-- SQL Server 2016+ incremental schema repair for the existing ClassSession entity.
-- The repository does not contain the core SportCenter schema bootstrap. First
-- provision the established dbo.[class] and dbo.facility tables (including INT
-- IDENTITY id primary keys) from the approved core-schema source. Then run this
-- as one standalone batch in SportCenterDB before starting Hibernate validation.
-- Back up the database and stop application writers. No object or data is dropped.
-- Compatible reruns validate and retain the existing table; incompatible schemas
-- fail for manual review instead of being altered or silently accepted.
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
    THROW 50301, 'Select SportCenterDB before running the ClassSession schema repair.', 1;
IF @@TRANCOUNT <> 0
    THROW 50302, 'Run this script without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT, @message NVARCHAR(2048), @sql NVARCHAR(MAX);
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.ClassSession.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50303, 'Could not acquire the ClassSession schema lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.[class]', N'U') IS NULL OR OBJECT_ID(N'dbo.facility', N'U') IS NULL
        THROW 50304, 'Expected established dbo.[class] and dbo.facility core tables.', 1;

    -- Both JPA target entities use INT IDENTITY primary keys.
    IF EXISTS (
        SELECT 1
        FROM (VALUES (OBJECT_ID(N'dbo.[class]', N'U')), (OBJECT_ID(N'dbo.facility', N'U'))) t(object_id)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.columns c
            WHERE c.object_id = t.object_id AND c.name = N'id'
              AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
              AND c.is_nullable = 0 AND c.is_identity = 1 AND c.is_computed = 0
              AND c.generated_always_type = 0
        ) OR NOT EXISTS (
            SELECT 1
            FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = t.object_id AND i.is_primary_key = 1 AND i.is_unique = 1
              AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.ignore_dup_key = 0
              AND ic.key_ordinal = 1 AND c.name = N'id'
              AND (SELECT COUNT(*) FROM sys.index_columns k
                   WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
    ) THROW 50305, 'Core [class]/facility id columns must be INT IDENTITY primary keys.', 1;

    IF OBJECT_ID(N'dbo.class_session') IS NOT NULL
       AND OBJECT_ID(N'dbo.class_session', N'U') IS NULL
        THROW 50306, 'dbo.class_session is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.class_session', N'U') IS NULL
    BEGIN
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.class_session (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_class_session PRIMARY KEY,
                class_id INT NOT NULL,
                facility_id INT NULL,
                start_time DATETIME NOT NULL,
                end_time DATETIME NOT NULL,
                status VARCHAR(30) NOT NULL,
                CONSTRAINT fk_class_session_class FOREIGN KEY (class_id) REFERENCES dbo.[class](id),
                CONSTRAINT fk_class_session_facility FOREIGN KEY (facility_id) REFERENCES dbo.facility(id)
            );';
    END;

    -- Hibernate mapping after the configured snake_case physical naming strategy:
    -- Integer -> INT; LocalDateTime with columnDefinition=datetime -> DATETIME;
    -- String(30) -> VARCHAR(30). Nullable facilityId is intentional.
    DECLARE @columns TABLE (
        column_name SYSNAME PRIMARY KEY,
        type_name SYSNAME,
        byte_length SMALLINT,
        time_scale TINYINT,
        nullable BIT,
        identity_column BIT
    );
    INSERT @columns VALUES
        (N'id', N'int', 4, 0, 0, 1),
        (N'class_id', N'int', 4, 0, 0, 0),
        (N'facility_id', N'int', 4, 0, 1, 0),
        (N'start_time', N'datetime', 8, 3, 0, 0),
        (N'end_time', N'datetime', 8, 3, 0, 0),
        (N'status', N'varchar', 30, 0, 0, 0);

    SELECT TOP (1) @message = N'Incompatible/missing column: dbo.class_session.' + e.column_name
    FROM @columns e
    LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.class_session', N'U') AND c.name = e.column_name
    WHERE c.column_id IS NULL OR c.user_type_id <> c.system_type_id
       OR c.system_type_id <> TYPE_ID(e.type_name) OR c.max_length <> e.byte_length
       OR c.scale <> e.time_scale OR c.is_nullable <> e.nullable
       OR c.is_identity <> e.identity_column OR c.is_computed = 1 OR c.generated_always_type <> 0;
    IF @message IS NOT NULL THROW 50307, @message, 1;

    IF EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.class_session', N'U')
          AND NOT EXISTS (SELECT 1 FROM @columns e WHERE e.column_name = c.name)
    ) THROW 50308, 'dbo.class_session has unexpected columns; inspect the existing schema manually.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.identity_columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.class_session', N'U')
          AND (CONVERT(BIGINT, c.seed_value) <> 1 OR CONVERT(BIGINT, c.increment_value) <> 1)
    ) THROW 50309, 'dbo.class_session.id must use IDENTITY(1,1).', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.class_session') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.ignore_dup_key = 0 AND ic.key_ordinal = 1 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    ) THROW 50310, 'dbo.class_session requires an enabled single-column id primary key.', 1;

    DECLARE @foreignKeys TABLE (
        row_id INT IDENTITY PRIMARY KEY,
        column_name SYSNAME,
        referenced_table SYSNAME,
        constraint_name SYSNAME
    );
    INSERT @foreignKeys (column_name, referenced_table, constraint_name) VALUES
        (N'class_id', N'class', N'fk_class_session_class'),
        (N'facility_id', N'facility', N'fk_class_session_facility');

    -- Any existing relationship on these columns must be the expected trusted,
    -- enabled, single-column FK with NO ACTION semantics.
    IF EXISTS (
        SELECT 1
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fc ON fc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.class_session')
          AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.is_not_for_replication = 1
               OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
               OR (SELECT COUNT(*) FROM sys.foreign_key_columns x
                   WHERE x.constraint_object_id = fk.object_id) <> 1
               OR NOT EXISTS (
                   SELECT 1 FROM @foreignKeys e
                   WHERE c.name = e.column_name
                     AND fc.referenced_object_id = OBJECT_ID(N'dbo.' + QUOTENAME(e.referenced_table), N'U')
                     AND COL_NAME(fc.referenced_object_id, fc.referenced_column_id) = N'id'
               ))
    ) THROW 50311, 'dbo.class_session has an incompatible, disabled, untrusted, or cascading foreign key.', 1;

    DECLARE @row INT = 1, @column SYSNAME, @reference SYSNAME, @constraint SYSNAME;
    WHILE @row <= (SELECT COUNT(*) FROM @foreignKeys)
    BEGIN
        SELECT @column = column_name, @reference = referenced_table, @constraint = constraint_name
        FROM @foreignKeys WHERE row_id = @row;
        IF NOT EXISTS (
            SELECT 1 FROM sys.foreign_key_columns fc
            JOIN sys.columns c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
            WHERE fc.parent_object_id = OBJECT_ID(N'dbo.class_session') AND c.name = @column
              AND fc.referenced_object_id = OBJECT_ID(N'dbo.' + QUOTENAME(@reference), N'U')
              AND COL_NAME(fc.referenced_object_id, fc.referenced_column_id) = N'id'
        )
        BEGIN
            IF OBJECT_ID(N'dbo.' + @constraint) IS NOT NULL
                THROW 50312, 'Expected ClassSession foreign-key name is already occupied.', 1;
            SET @sql = N'ALTER TABLE dbo.class_session WITH CHECK ADD CONSTRAINT ' + QUOTENAME(@constraint)
                + N' FOREIGN KEY (' + QUOTENAME(@column) + N') REFERENCES dbo.'
                + QUOTENAME(@reference) + N'(id);';
            EXEC sys.sp_executesql @sql;
        END;
        SET @row += 1;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- ClassSession declares no secondary @Index mappings. The primary key above is
-- the only required JPA index; no speculative performance indexes are added.
