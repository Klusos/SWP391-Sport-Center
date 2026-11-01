-- SQL Server 2016+ manual Phase 7.1 AuditLog migration.
-- Back up SportCenterDB and stop application writers before execution.
-- Run as one standalone batch. This script is transactional, rerunnable, and non-destructive.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDB'
    THROW 50721, 'Select SportCenterDB before running the AuditLog migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50722, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50723, 'Expected dbo.users before creating dbo.audit_log.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users')
               AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id)
    THROW 50724, 'dbo.users.id must be SQL Server INT.', 1;
IF OBJECT_ID(N'dbo.audit_log') IS NOT NULL AND OBJECT_ID(N'dbo.audit_log', N'U') IS NULL
    THROW 50725, 'dbo.audit_log is occupied by a non-table object.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase7.AuditLog.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50726, 'Could not acquire the AuditLog migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.audit_log', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.audit_log (
            id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_audit_log PRIMARY KEY,
            actor_user_id INT NULL,
            actor_role VARCHAR(30) NOT NULL,
            action VARCHAR(50) NOT NULL,
            entity_type VARCHAR(60) NOT NULL,
            entity_id VARCHAR(64) NULL,
            description NVARCHAR(500) NOT NULL,
            created_at DATETIME2(6) NOT NULL,
            CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
        );
    END
    ELSE
    BEGIN
        IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.audit_log', N'U')) <> 8
            THROW 50727, 'Existing dbo.audit_log has an unexpected column count; inspect before migration.', 1;
        IF EXISTS (
            SELECT 1
            FROM (VALUES
                (N'id', N'bigint', 8, 0, 1),
                (N'actor_user_id', N'int', 4, 1, 0),
                (N'actor_role', N'varchar', 30, 0, 0),
                (N'action', N'varchar', 50, 0, 0),
                (N'entity_type', N'varchar', 60, 0, 0),
                (N'entity_id', N'varchar', 64, 1, 0),
                (N'description', N'nvarchar', 1000, 0, 0),
                (N'created_at', N'datetime2', 8, 0, 0)
            ) expected(column_name, type_name, max_length, is_nullable, is_identity)
            LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.audit_log', N'U') AND c.name = expected.column_name
            LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
            WHERE c.column_id IS NULL OR t.name <> expected.type_name OR t.is_user_defined <> 0
                OR c.user_type_id <> c.system_type_id OR c.max_length <> expected.max_length
                OR c.is_nullable <> expected.is_nullable OR c.is_identity <> expected.is_identity
                OR c.is_computed = 1 OR c.generated_always_type <> 0
                OR (expected.column_name = N'created_at' AND c.scale <> 6)
        )
            THROW 50728, 'Existing dbo.audit_log column metadata is incompatible; inspect before migration.', 1;
        IF NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = OBJECT_ID(N'dbo.audit_log') AND i.is_primary_key = 1 AND i.is_unique = 1
              AND i.is_disabled = 0 AND c.name = N'id'
              AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                   AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
            THROW 50729, 'Existing dbo.audit_log requires an enabled single-column id primary key.', 1;
        IF EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.audit_log')
              AND (fk.name = N'fk_audit_log_actor' OR EXISTS (
                  SELECT 1 FROM sys.foreign_key_columns fkc
                  JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
                  WHERE fkc.constraint_object_id = fk.object_id AND pc.name = N'actor_user_id'))
              AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.delete_referential_action <> 0
                   OR fk.update_referential_action <> 0 OR fk.referenced_object_id <> OBJECT_ID(N'dbo.users')
                   OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) <> 1
                   OR NOT EXISTS (
                       SELECT 1 FROM sys.foreign_key_columns fkc
                       JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
                       JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
                       WHERE fkc.constraint_object_id = fk.object_id AND pc.name = N'actor_user_id' AND rc.name = N'id'))
        )
            THROW 50730, 'An existing AuditLog actor foreign key is incompatible.', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
        JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.audit_log') AND fk.referenced_object_id = OBJECT_ID(N'dbo.users')
          AND pc.name = N'actor_user_id' AND rc.name = N'id' AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    )
    BEGIN
        IF OBJECT_ID(N'dbo.fk_audit_log_actor', N'F') IS NOT NULL
            THROW 50731, 'Expected fk_audit_log_actor name is used by an incompatible object.', 1;
        ALTER TABLE dbo.audit_log WITH CHECK ADD CONSTRAINT fk_audit_log_actor
            FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id);
    END;

    DECLARE @expectedIndexKeys TABLE (
        index_name SYSNAME NOT NULL,
        key_ordinal TINYINT NOT NULL,
        column_name SYSNAME NOT NULL,
        is_descending BIT NOT NULL,
        PRIMARY KEY (index_name, key_ordinal)
    );
    INSERT @expectedIndexKeys VALUES
        (N'ix_audit_log_created', 1, N'created_at', 1),
        (N'ix_audit_log_created', 2, N'id', 1),
        (N'ix_audit_log_actor_created', 1, N'actor_user_id', 0),
        (N'ix_audit_log_actor_created', 2, N'created_at', 1),
        (N'ix_audit_log_actor_created', 3, N'id', 1),
        (N'ix_audit_log_entity', 1, N'entity_type', 0),
        (N'ix_audit_log_entity', 2, N'entity_id', 0),
        (N'ix_audit_log_entity', 3, N'created_at', 1),
        (N'ix_audit_log_entity', 4, N'id', 1);

    IF EXISTS (
        SELECT 1 FROM (SELECT DISTINCT index_name FROM @expectedIndexKeys) expected
        JOIN sys.indexes i ON i.object_id = OBJECT_ID(N'dbo.audit_log') AND i.name = expected.index_name
        WHERE i.type <> 2 OR i.is_unique = 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1 OR i.has_filter = 1
           OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
               AND k.index_id = i.index_id AND k.key_ordinal > 0)
              <> (SELECT COUNT(*) FROM @expectedIndexKeys k WHERE k.index_name = expected.index_name)
           OR EXISTS (
                SELECT 1 FROM @expectedIndexKeys ek
                LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                    AND ic.key_ordinal = ek.key_ordinal
                LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ek.index_name = expected.index_name
                  AND (ic.column_id IS NULL OR c.name <> ek.column_name OR ic.is_descending_key <> ek.is_descending)
           )
           OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                      AND inc.index_id = i.index_id AND inc.is_included_column = 1)
    )
        THROW 50732, 'A same-named AuditLog index has incompatible key columns or index metadata.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.audit_log') AND name = N'ix_audit_log_created')
        CREATE INDEX ix_audit_log_created ON dbo.audit_log(created_at DESC, id DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.audit_log') AND name = N'ix_audit_log_actor_created')
        CREATE INDEX ix_audit_log_actor_created ON dbo.audit_log(actor_user_id ASC, created_at DESC, id DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.audit_log') AND name = N'ix_audit_log_entity')
        CREATE INDEX ix_audit_log_entity ON dbo.audit_log(entity_type ASC, entity_id ASC, created_at DESC, id DESC);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
