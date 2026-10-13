-- SQL Server 2016+ manual migration for Phase 1.5.
-- Back up SportCenterDB and stop application writers before running.
-- Run as one standalone batch, without GO or an outer transaction.
-- This script is idempotent; it never drops tables, columns, indexes, or data.
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
    THROW 50101, 'Select SportCenterDB before running the Phase 1.5 migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50102, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT, @sql NVARCHAR(MAX), @message NVARCHAR(2048);
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase1_5.Consultation.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50103, 'Could not acquire the Phase 1.5 migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
       OR OBJECT_ID(N'dbo.sport', N'U') IS NULL
       OR OBJECT_ID(N'dbo.skill_assessment', N'U') IS NULL
        THROW 50104, 'Expected dbo.users, dbo.sport, and dbo.skill_assessment tables.', 1;

    IF OBJECT_ID(N'dbo.consultation_request') IS NOT NULL
       AND OBJECT_ID(N'dbo.consultation_request', N'U') IS NULL
        THROW 50105, 'dbo.consultation_request is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.consultation_request', N'U') IS NULL
    BEGIN
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.consultation_request (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_consultation_request PRIMARY KEY,
                member_id INT NULL,
                name NVARCHAR(100) NOT NULL,
                phone NVARCHAR(20) NOT NULL,
                email NVARCHAR(150) NULL,
                sport_id INT NOT NULL,
                age INT NULL,
                experience NVARCHAR(MAX) NULL,
                note NVARCHAR(MAX) NULL,
                status VARCHAR(20) NOT NULL CONSTRAINT df_consultation_request_status DEFAULT (''PENDING''),
                created_at DATETIME2(6) NOT NULL CONSTRAINT df_consultation_request_created DEFAULT (SYSDATETIME()),
                handled_by INT NULL,
                handled_at DATETIME2(6) NULL,
                resolution_note NVARCHAR(1000) NULL,
                completed_at DATETIME2(6) NULL,
                cancelled_at DATETIME2(6) NULL,
                CONSTRAINT ck_consultation_request_status CHECK (status IN (''PENDING'', ''IN_PROGRESS'', ''COMPLETED'', ''CANCELLED'')),
                CONSTRAINT ck_consultation_request_age CHECK (age IS NULL OR age BETWEEN 1 AND 120),
                CONSTRAINT ck_consultation_request_name CHECK (LEN(LTRIM(RTRIM(name))) > 0),
                CONSTRAINT ck_consultation_request_phone CHECK (LEN(LTRIM(RTRIM(phone))) > 0)
            );';
    END;

    IF COL_LENGTH(N'dbo.consultation_request', N'id') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'member_id') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'name') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'phone') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'email') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'sport_id') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'age') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'experience') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'note') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'status') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'created_at') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'handled_by') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'handled_at') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'resolution_note') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'completed_at') IS NULL
       OR COL_LENGTH(N'dbo.consultation_request', N'cancelled_at') IS NULL
        THROW 50106, 'Existing dbo.consultation_request is incomplete; inspect it before migration.', 1;

    DECLARE @expectedColumns TABLE (
        column_name SYSNAME PRIMARY KEY,
        type_name SYSNAME,
        byte_length SMALLINT,
        time_scale TINYINT,
        nullable BIT,
        identity_column BIT
    );
    INSERT @expectedColumns VALUES
        (N'id', N'int', 4, 0, 0, 1),
        (N'member_id', N'int', 4, 0, 1, 0),
        (N'name', N'nvarchar', 200, 0, 0, 0),
        (N'phone', N'nvarchar', 40, 0, 0, 0),
        (N'email', N'nvarchar', 300, 0, 1, 0),
        (N'sport_id', N'int', 4, 0, 0, 0),
        (N'age', N'int', 4, 0, 1, 0),
        (N'experience', N'nvarchar', -1, 0, 1, 0),
        (N'note', N'nvarchar', -1, 0, 1, 0),
        (N'status', N'varchar', 20, 0, 0, 0),
        (N'created_at', N'datetime2', 8, 6, 0, 0),
        (N'handled_by', N'int', 4, 0, 1, 0),
        (N'handled_at', N'datetime2', 8, 6, 1, 0),
        (N'resolution_note', N'nvarchar', 2000, 0, 1, 0),
        (N'completed_at', N'datetime2', 8, 6, 1, 0),
        (N'cancelled_at', N'datetime2', 8, 6, 1, 0);
    -- SQL Server/Hibernate schemas may use DATETIME2(7) for LocalDateTime,
    -- while this migration creates DATETIME2(6). Accept either scale only for
    -- the four timestamp columns; keep every other column check exact.
    IF EXISTS (
        SELECT 1 FROM @expectedColumns e
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.consultation_request') AND c.name = e.column_name
        WHERE c.column_id IS NULL OR c.system_type_id <> TYPE_ID(e.type_name)
           OR c.user_type_id <> c.system_type_id OR c.max_length <> e.byte_length
           OR (c.scale <> e.time_scale AND NOT (
                   e.column_name IN (N'created_at', N'handled_at', N'completed_at', N'cancelled_at')
                   AND c.scale IN (6, 7)
               ))
           OR c.is_nullable <> e.nullable
           OR c.is_identity <> e.identity_column OR c.is_computed = 1 OR c.generated_always_type <> 0
    )
        THROW 50110, 'Existing dbo.consultation_request column metadata does not match the migration contract.', 1;
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.consultation_request') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    )
        THROW 50111, 'dbo.consultation_request must have an enabled single-column id primary key.', 1;

    IF COL_LENGTH(N'dbo.skill_assessment', N'consultation_request_id') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.skill_assessment ADD consultation_request_id INT NULL;';
    ELSE IF NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.skill_assessment')
          AND name = N'consultation_request_id'
          AND system_type_id = TYPE_ID(N'int') AND is_nullable = 1
          AND is_computed = 0 AND generated_always_type = 0
    )
        THROW 50107, 'skill_assessment.consultation_request_id must be a nullable INT column.', 1;

    DECLARE @foreignKeys TABLE (
        row_id INT IDENTITY PRIMARY KEY,
        table_name SYSNAME,
        column_name SYSNAME,
        referenced_table SYSNAME,
        constraint_name SYSNAME
    );
    INSERT @foreignKeys (table_name, column_name, referenced_table, constraint_name) VALUES
        (N'consultation_request', N'member_id', N'users', N'fk_consultation_member'),
        (N'consultation_request', N'sport_id', N'sport', N'fk_consultation_sport'),
        (N'consultation_request', N'handled_by', N'users', N'fk_consultation_handler'),
        (N'skill_assessment', N'consultation_request_id', N'consultation_request', N'fk_assessment_consultation');

    DECLARE @n INT = 1, @table SYSNAME, @column SYSNAME, @reference SYSNAME, @constraint SYSNAME;
    WHILE @n <= (SELECT COUNT(*) FROM @foreignKeys)
    BEGIN
        SELECT @table = table_name, @column = column_name, @reference = referenced_table,
               @constraint = constraint_name
        FROM @foreignKeys WHERE row_id = @n;

        IF EXISTS (
            SELECT 1
            FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fc ON fc.constraint_object_id = fk.object_id
            JOIN sys.columns c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.' + @table)
              AND c.name = @column
              AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1
                   OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
                   OR fc.referenced_object_id <> OBJECT_ID(N'dbo.' + @reference)
                   OR COL_NAME(fc.referenced_object_id, fc.referenced_column_id) <> N'id'
                   OR (SELECT COUNT(*) FROM sys.foreign_key_columns x
                       WHERE x.constraint_object_id = fk.object_id) <> 1)
        )
            THROW 50108, 'An incompatible foreign key exists on a Phase 1.5 relationship column.', 1;

        IF NOT EXISTS (
            SELECT 1
            FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fc ON fc.constraint_object_id = fk.object_id
            JOIN sys.columns c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.' + @table)
              AND c.name = @column
              AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND fc.referenced_object_id = OBJECT_ID(N'dbo.' + @reference)
              AND COL_NAME(fc.referenced_object_id, fc.referenced_column_id) = N'id'
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x
                   WHERE x.constraint_object_id = fk.object_id) = 1
        )
        BEGIN
            IF OBJECT_ID(N'dbo.' + @constraint, N'F') IS NOT NULL
                THROW 50109, 'Expected foreign-key name is already used by an incompatible object.', 1;
            SET @sql = N'ALTER TABLE dbo.' + QUOTENAME(@table) + N' WITH CHECK ADD CONSTRAINT '
                + QUOTENAME(@constraint) + N' FOREIGN KEY (' + QUOTENAME(@column) + N') REFERENCES dbo.'
                + QUOTENAME(@reference) + N'(id);';
            EXEC sys.sp_executesql @sql;
        END;
        SET @n += 1;
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ck_consultation_request_status')
        ALTER TABLE dbo.consultation_request WITH CHECK ADD CONSTRAINT ck_consultation_request_status
            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ck_consultation_request_age')
        ALTER TABLE dbo.consultation_request WITH CHECK ADD CONSTRAINT ck_consultation_request_age
            CHECK (age IS NULL OR age BETWEEN 1 AND 120);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ck_consultation_request_name')
        ALTER TABLE dbo.consultation_request WITH CHECK ADD CONSTRAINT ck_consultation_request_name
            CHECK (LEN(LTRIM(RTRIM(name))) > 0);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ck_consultation_request_phone')
        ALTER TABLE dbo.consultation_request WITH CHECK ADD CONSTRAINT ck_consultation_request_phone
            CHECK (LEN(LTRIM(RTRIM(phone))) > 0);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ix_consultation_member_created')
        CREATE INDEX ix_consultation_member_created ON dbo.consultation_request(member_id, created_at DESC, id DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ix_consultation_status_created')
        CREATE INDEX ix_consultation_status_created ON dbo.consultation_request(status, created_at DESC, id DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.consultation_request') AND name = N'ix_consultation_handler_status')
        CREATE INDEX ix_consultation_handler_status ON dbo.consultation_request(handled_by, status);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.skill_assessment') AND name = N'ix_assessment_consultation')
        CREATE INDEX ix_assessment_consultation ON dbo.skill_assessment(consultation_request_id);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
