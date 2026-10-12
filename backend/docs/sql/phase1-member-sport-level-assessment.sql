-- SQL Server 2016+ manual migration. Back up SportCenterDB and stop writers first.
-- Verified JPA physical names use default snake_case and dbo: User -> users,
-- Sport -> sport, Coach -> coach; Integer IDs -> INT; LocalDateTime -> DATETIME2(6).
-- Run as one standalone batch (no GO/outer transaction). Requires DDL/metadata
-- permissions. No data/index is dropped. Review strict failures before retrying.
-- Only supported column upgrade: notes VARCHAR(1000) NULL -> NVARCHAR(1000) NULL.
-- Required SET options must also hold on JDBC sessions that modify filtered indexes.
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
    THROW 50001, 'Select SportCenterDB; review this guard for renamed deployments.', 1;
IF @@TRANCOUNT <> 0
    THROW 50002, 'Run the migration without an existing transaction.', 1;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT, @message NVARCHAR(2048), @sql NVARCHAR(MAX), @rows BIGINT;
    EXEC @lockResult = sys.sp_getapplock @Resource = N'SportCenter.Phase1.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0 THROW 50003, 'Cannot acquire migration lock; retry with writers stopped.', 1;

    DECLARE @references TABLE (table_name SYSNAME PRIMARY KEY);
    INSERT @references VALUES (N'users'), (N'sport'), (N'coach');
    IF EXISTS (
        SELECT 1 FROM @references r
        LEFT JOIN sys.tables t ON t.object_id = OBJECT_ID(N'dbo.' + r.table_name, N'U')
        LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = N'id'
        WHERE t.object_id IS NULL OR t.is_memory_optimized = 1 OR t.temporal_type <> 0
           OR c.column_id IS NULL OR c.system_type_id <> TYPE_ID(N'int')
           OR c.user_type_id <> c.system_type_id OR c.is_nullable <> 0 OR c.is_identity <> 1
           OR NOT EXISTS (
               SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic
                 ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
               WHERE i.object_id = t.object_id AND i.is_primary_key = 1 AND i.is_disabled = 0
                 AND i.is_unique = 1 AND i.is_hypothetical = 0 AND i.ignore_dup_key = 0
                 AND ic.column_id = c.column_id
                 AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                      AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
           )
    ) THROW 50004, 'Expected dbo.users/sport/coach with enabled single INT IDENTITY primary keys.', 1;

    IF (OBJECT_ID(N'dbo.member_sport_level') IS NOT NULL AND OBJECT_ID(N'dbo.member_sport_level', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.skill_assessment') IS NOT NULL AND OBJECT_ID(N'dbo.skill_assessment', N'U') IS NULL)
        THROW 50005, 'A Phase 1 table name is occupied by a non-table object.', 1;
    -- Dynamic DDL avoids binding to missing/incompatible columns in the same batch.
    IF OBJECT_ID(N'dbo.member_sport_level', N'U') IS NULL
        EXEC sys.sp_executesql N'CREATE TABLE dbo.member_sport_level (
            id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
            member_id INT NOT NULL, sport_id INT NOT NULL, level VARCHAR(20) NOT NULL,
            verified_by_coach_id INT NULL, verified_at DATETIME2(6) NULL,
            updated_at DATETIME2(6) NOT NULL
        );';
    IF OBJECT_ID(N'dbo.skill_assessment', N'U') IS NULL
        EXEC sys.sp_executesql N'CREATE TABLE dbo.skill_assessment (
            id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
            member_id INT NOT NULL, sport_id INT NOT NULL,
            assigned_coach_id INT NOT NULL, requested_by_user_id INT NOT NULL,
            status VARCHAR(20) NOT NULL, assessed_level VARCHAR(20) NULL,
            notes NVARCHAR(1000) NULL, created_at DATETIME2(6) NOT NULL,
            assessed_at DATETIME2(6) NULL, cancelled_at DATETIME2(6) NULL
        );';
    EXEC sys.sp_executesql N'SELECT @n = COUNT_BIG(*) FROM dbo.member_sport_level WITH (TABLOCKX, HOLDLOCK);', N'@n BIGINT OUTPUT', @n = @rows OUTPUT;
    EXEC sys.sp_executesql N'SELECT @n = COUNT_BIG(*) FROM dbo.skill_assessment WITH (TABLOCKX, HOLDLOCK);', N'@n BIGINT OUTPUT', @n = @rows OUTPUT;

    DECLARE @columns TABLE (
        table_name SYSNAME, column_name SYSNAME, type_name SYSNAME,
        byte_length SMALLINT, time_scale TINYINT, nullable BIT, identity_column BIT,
        PRIMARY KEY (table_name, column_name)
    );
    -- NVARCHAR max_length is bytes: 1000 UTF-16 units = 2000 bytes.
    INSERT @columns VALUES
        (N'member_sport_level', N'id', N'int', 4, 0, 0, 1),
        (N'member_sport_level', N'member_id', N'int', 4, 0, 0, 0),
        (N'member_sport_level', N'sport_id', N'int', 4, 0, 0, 0),
        (N'member_sport_level', N'level', N'varchar', 20, 0, 0, 0),
        (N'member_sport_level', N'verified_by_coach_id', N'int', 4, 0, 1, 0),
        (N'member_sport_level', N'verified_at', N'datetime2', 8, 6, 1, 0),
        (N'member_sport_level', N'updated_at', N'datetime2', 8, 6, 0, 0),
        (N'skill_assessment', N'id', N'int', 4, 0, 0, 1),
        (N'skill_assessment', N'member_id', N'int', 4, 0, 0, 0),
        (N'skill_assessment', N'sport_id', N'int', 4, 0, 0, 0),
        (N'skill_assessment', N'assigned_coach_id', N'int', 4, 0, 0, 0),
        (N'skill_assessment', N'requested_by_user_id', N'int', 4, 0, 0, 0),
        (N'skill_assessment', N'status', N'varchar', 20, 0, 0, 0),
        (N'skill_assessment', N'assessed_level', N'varchar', 20, 0, 1, 0),
        (N'skill_assessment', N'notes', N'nvarchar', 2000, 0, 1, 0),
        (N'skill_assessment', N'created_at', N'datetime2', 8, 6, 0, 0),
        (N'skill_assessment', N'assessed_at', N'datetime2', 8, 6, 1, 0),
        (N'skill_assessment', N'cancelled_at', N'datetime2', 8, 6, 1, 0);
    SELECT TOP (1) @message = N'Incompatible/missing column: dbo.' + e.table_name + N'.' + e.column_name
    FROM @columns e LEFT JOIN sys.columns c
      ON c.object_id = OBJECT_ID(N'dbo.' + e.table_name, N'U') AND c.name = e.column_name
    WHERE c.column_id IS NULL OR c.user_type_id <> c.system_type_id
       OR c.is_computed = 1 OR c.generated_always_type <> 0
       OR c.is_nullable <> e.nullable OR c.is_identity <> e.identity_column OR c.scale <> e.time_scale
       OR NOT ((c.system_type_id = TYPE_ID(e.type_name) AND c.max_length = e.byte_length)
               OR (e.table_name = N'skill_assessment' AND e.column_name = N'notes'
                   AND c.system_type_id = TYPE_ID(N'varchar') AND c.max_length = 1000));
    IF @message IS NOT NULL THROW 50006, @message, 1;
    IF EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id
        WHERE t.object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND NOT EXISTS (SELECT 1 FROM @columns e WHERE e.table_name = t.name AND e.column_name = c.name)
    ) THROW 50007, 'Unexpected Phase 1 columns; explicitly review schema before migrating.', 1;
    IF EXISTS (
        SELECT 1 FROM sys.tables t
        WHERE t.object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND (t.is_memory_optimized = 1 OR t.temporal_type <> 0)
    ) THROW 50008, 'Temporal/memory-optimized Phase 1 tables are unsupported.', 1;
    IF EXISTS (
        SELECT 1 FROM sys.identity_columns c
        WHERE c.object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND (CONVERT(BIGINT, c.seed_value) <> 1 OR CONVERT(BIGINT, c.increment_value) <> 1)
    ) THROW 50009, 'Phase 1 IDs must use IDENTITY(1,1).', 1;
    IF EXISTS (
        SELECT 1 FROM (VALUES (OBJECT_ID(N'dbo.member_sport_level')), (OBJECT_ID(N'dbo.skill_assessment'))) t(object_id)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic
              ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = t.object_id AND i.is_primary_key = 1 AND i.is_disabled = 0
                 AND i.is_unique = 1 AND i.is_hypothetical = 0 AND i.ignore_dup_key = 0 AND c.name = N'id'
              AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                   AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
    ) THROW 50010, 'Phase 1 primary keys must be enabled single-column id keys.', 1;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.skill_assessment')
               AND name = N'notes' AND system_type_id = TYPE_ID(N'varchar'))
        EXEC sys.sp_executesql N'ALTER TABLE dbo.skill_assessment ALTER COLUMN notes NVARCHAR(1000) NULL;';
    -- Conversion cannot recover characters already lost during earlier VARCHAR writes.

    DECLARE @foreignKeys TABLE (
        row_id INT IDENTITY PRIMARY KEY, table_name SYSNAME, column_name SYSNAME,
        referenced_table SYSNAME, constraint_name SYSNAME
    );
    INSERT @foreignKeys (table_name, column_name, referenced_table, constraint_name) VALUES
        (N'member_sport_level', N'member_id', N'users', N'fk_msl_member'),
        (N'member_sport_level', N'sport_id', N'sport', N'fk_msl_sport'),
        (N'member_sport_level', N'verified_by_coach_id', N'coach', N'fk_msl_coach'),
        (N'skill_assessment', N'member_id', N'users', N'fk_sa_member'),
        (N'skill_assessment', N'sport_id', N'sport', N'fk_sa_sport'),
        (N'skill_assessment', N'assigned_coach_id', N'coach', N'fk_sa_coach'),
        (N'skill_assessment', N'requested_by_user_id', N'users', N'fk_sa_requester');
    -- Hibernate-generated FK names are accepted when semantics match exactly.
    IF EXISTS (
        SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fc ON fc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
        WHERE fk.parent_object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.is_not_for_replication = 1
               OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
               OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) <> 1
               OR NOT EXISTS (SELECT 1 FROM @foreignKeys e
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.' + e.table_name) AND c.name = e.column_name
                     AND fc.referenced_object_id = OBJECT_ID(N'dbo.' + e.referenced_table)
                     AND COL_NAME(fc.referenced_object_id, fc.referenced_column_id) = N'id'))
    ) THROW 50011, 'Incorrect/cascading/disabled/untrusted Phase 1 foreign key; repair explicitly.', 1;
    DECLARE @n INT = 1, @table SYSNAME, @column SYSNAME, @reference SYSNAME, @constraint SYSNAME;
    WHILE @n <= (SELECT COUNT(*) FROM @foreignKeys)
    BEGIN
        SELECT @table = table_name, @column = column_name, @reference = referenced_table, @constraint = constraint_name
        FROM @foreignKeys WHERE row_id = @n;
        IF NOT EXISTS (
            SELECT 1 FROM sys.foreign_key_columns fc WHERE fc.parent_object_id = OBJECT_ID(N'dbo.' + @table)
              AND COL_NAME(fc.parent_object_id, fc.parent_column_id) = @column
        )
        BEGIN
            SET @sql = N'ALTER TABLE dbo.' + QUOTENAME(@table) + N' WITH CHECK ADD CONSTRAINT ' + QUOTENAME(@constraint)
                + N' FOREIGN KEY (' + QUOTENAME(@column) + N') REFERENCES dbo.' + QUOTENAME(@reference) + N'(id);';
            EXEC sys.sp_executesql @sql;
        END;
        SET @n += 1;
    END;

    -- Strict enum CHECK whitelist permits IN and SQL Server's equivalent OR form.
    DECLARE @checks TABLE (row_id INT IDENTITY PRIMARY KEY, table_name SYSNAME, constraint_name SYSNAME, predicate NVARCHAR(500));
    INSERT @checks (table_name, constraint_name, predicate) VALUES
        (N'member_sport_level', N'ck_msl_level', N'level IN (''BEGINNER'', ''INTERMEDIATE'', ''ADVANCED'')'),
        (N'skill_assessment', N'ck_sa_status', N'status IN (''PENDING'', ''COMPLETED'', ''CANCELLED'')'),
        (N'skill_assessment', N'ck_sa_level', N'assessed_level IS NULL OR assessed_level IN (''BEGINNER'', ''INTERMEDIATE'', ''ADVANCED'')');
    DECLARE @allowedChecks TABLE (table_name SYSNAME, fingerprint NVARCHAR(500));
    INSERT @allowedChecks VALUES
        (N'member_sport_level', N'levelIN''BEGINNER'',''INTERMEDIATE'',''ADVANCED'''),
        (N'member_sport_level', N'level=''BEGINNER''ORLEVEL=''INTERMEDIATE''ORLEVEL=''ADVANCED'''),
        (N'member_sport_level', N'levelIN''BEGINNER'',''ADVANCED'',''INTERMEDIATE'''),
        (N'member_sport_level', N'level=''BEGINNER''ORLEVEL=''ADVANCED''ORLEVEL=''INTERMEDIATE'''),
        (N'member_sport_level', N'levelIN''INTERMEDIATE'',''BEGINNER'',''ADVANCED'''),
        (N'member_sport_level', N'level=''INTERMEDIATE''ORLEVEL=''BEGINNER''ORLEVEL=''ADVANCED'''),
        (N'member_sport_level', N'levelIN''INTERMEDIATE'',''ADVANCED'',''BEGINNER'''),
        (N'member_sport_level', N'level=''INTERMEDIATE''ORLEVEL=''ADVANCED''ORLEVEL=''BEGINNER'''),
        (N'member_sport_level', N'levelIN''ADVANCED'',''BEGINNER'',''INTERMEDIATE'''),
        (N'member_sport_level', N'level=''ADVANCED''ORLEVEL=''BEGINNER''ORLEVEL=''INTERMEDIATE'''),
        (N'member_sport_level', N'levelIN''ADVANCED'',''INTERMEDIATE'',''BEGINNER'''),
        (N'member_sport_level', N'level=''ADVANCED''ORLEVEL=''INTERMEDIATE''ORLEVEL=''BEGINNER'''),
        (N'skill_assessment', N'statusIN''PENDING'',''COMPLETED'',''CANCELLED'''),
        (N'skill_assessment', N'status=''PENDING''ORSTATUS=''COMPLETED''ORSTATUS=''CANCELLED'''),
        (N'skill_assessment', N'statusIN''PENDING'',''CANCELLED'',''COMPLETED'''),
        (N'skill_assessment', N'status=''PENDING''ORSTATUS=''CANCELLED''ORSTATUS=''COMPLETED'''),
        (N'skill_assessment', N'statusIN''COMPLETED'',''PENDING'',''CANCELLED'''),
        (N'skill_assessment', N'status=''COMPLETED''ORSTATUS=''PENDING''ORSTATUS=''CANCELLED'''),
        (N'skill_assessment', N'statusIN''COMPLETED'',''CANCELLED'',''PENDING'''),
        (N'skill_assessment', N'status=''COMPLETED''ORSTATUS=''CANCELLED''ORSTATUS=''PENDING'''),
        (N'skill_assessment', N'statusIN''CANCELLED'',''PENDING'',''COMPLETED'''),
        (N'skill_assessment', N'status=''CANCELLED''ORSTATUS=''PENDING''ORSTATUS=''COMPLETED'''),
        (N'skill_assessment', N'statusIN''CANCELLED'',''COMPLETED'',''PENDING'''),
        (N'skill_assessment', N'status=''CANCELLED''ORSTATUS=''COMPLETED''ORSTATUS=''PENDING'''),
        (N'skill_assessment', N'assessed_levelIN''BEGINNER'',''INTERMEDIATE'',''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''BEGINNER'',''INTERMEDIATE'',''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelIN''BEGINNER'',''INTERMEDIATE'',''ADVANCED''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED'''),
        (N'skill_assessment', N'assessed_level=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_levelIN''BEGINNER'',''ADVANCED'',''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''BEGINNER'',''ADVANCED'',''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelIN''BEGINNER'',''ADVANCED'',''INTERMEDIATE''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_level=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_levelIN''INTERMEDIATE'',''BEGINNER'',''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''INTERMEDIATE'',''BEGINNER'',''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelIN''INTERMEDIATE'',''BEGINNER'',''ADVANCED''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED'''),
        (N'skill_assessment', N'assessed_level=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_levelIN''INTERMEDIATE'',''ADVANCED'',''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''INTERMEDIATE'',''ADVANCED'',''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelIN''INTERMEDIATE'',''ADVANCED'',''BEGINNER''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER'''),
        (N'skill_assessment', N'assessed_level=''INTERMEDIATE''ORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_levelIN''ADVANCED'',''BEGINNER'',''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''ADVANCED'',''BEGINNER'',''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelIN''ADVANCED'',''BEGINNER'',''INTERMEDIATE''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE'''),
        (N'skill_assessment', N'assessed_level=''ADVANCED''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_levelIN''ADVANCED'',''INTERMEDIATE'',''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVELIN''ADVANCED'',''INTERMEDIATE'',''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelIN''ADVANCED'',''INTERMEDIATE'',''BEGINNER''ORASSESSED_LEVELISNULL'),
        (N'skill_assessment', N'assessed_level=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER'''),
        (N'skill_assessment', N'assessed_levelISNULLORASSESSED_LEVEL=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER'''),
        (N'skill_assessment', N'assessed_level=''ADVANCED''ORASSESSED_LEVEL=''INTERMEDIATE''ORASSESSED_LEVEL=''BEGINNER''ORASSESSED_LEVELISNULL');
    IF EXISTS (
        SELECT 1 FROM sys.check_constraints c JOIN sys.tables t ON t.object_id = c.parent_object_id
        CROSS APPLY (SELECT REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            c.definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'') AS fingerprint) f
        WHERE c.parent_object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND (c.is_disabled = 1 OR c.is_not_trusted = 1 OR c.is_not_for_replication = 1
               OR NOT EXISTS (SELECT 1 FROM @allowedChecks a WHERE a.table_name = t.name
                   AND a.fingerprint COLLATE Latin1_General_100_BIN2 = f.fingerprint COLLATE Latin1_General_100_BIN2))
    ) THROW 50012, 'Unexpected/disabled/untrusted Phase 1 CHECK constraint; review its definition.', 1;
    SET @n = 1;
    DECLARE @predicate NVARCHAR(500);
    WHILE @n <= (SELECT COUNT(*) FROM @checks)
    BEGIN
        SELECT @table = table_name, @constraint = constraint_name, @predicate = predicate FROM @checks WHERE row_id = @n;
        IF OBJECT_ID(N'dbo.' + @constraint) IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE object_id = OBJECT_ID(N'dbo.' + @constraint)
                           AND parent_object_id = OBJECT_ID(N'dbo.' + @table))
            THROW 50013, 'Expected CHECK name is occupied by another object.', 1;
        SET @column = CASE @constraint WHEN N'ck_msl_level' THEN N'level'
                     WHEN N'ck_sa_status' THEN N'status' ELSE N'assessed_level' END;
        IF EXISTS (
            SELECT 1 FROM sys.check_constraints c
            CROSS APPLY (SELECT REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                c.definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'') AS fingerprint) f
            WHERE c.object_id = OBJECT_ID(N'dbo.' + @constraint)
              AND LEFT(f.fingerprint, LEN(@column)) COLLATE Latin1_General_100_BIN2 <> @column COLLATE Latin1_General_100_BIN2
        ) THROW 50016, 'Named enum CHECK constraint applies to the wrong column.', 1;
        IF OBJECT_ID(N'dbo.' + @constraint) IS NULL
        BEGIN
            SET @sql = N'ALTER TABLE dbo.' + QUOTENAME(@table) + N' WITH CHECK ADD CONSTRAINT ' + QUOTENAME(@constraint) + N' CHECK (' + @predicate + N');';
            EXEC sys.sp_executesql @sql;
        END;
        SET @n += 1;
    END;

    DECLARE @indexes TABLE (row_id INT IDENTITY PRIMARY KEY, table_name SYSNAME, index_name SYSNAME, filtered BIT);
    INSERT @indexes (table_name, index_name, filtered) VALUES
        (N'member_sport_level', N'uk_member_sport_level', 0),
        (N'skill_assessment', N'uk_skill_assessment_pending', 1);
    -- Extra unique keys could forbid terminal history, so require explicit review.
    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'))
          AND i.is_primary_key = 0 AND i.is_unique = 1
          AND NOT EXISTS (SELECT 1 FROM @indexes e WHERE i.object_id = OBJECT_ID(N'dbo.' + e.table_name) AND i.name = e.index_name)
    ) THROW 50014, 'Unexpected unique Phase 1 index/constraint; review explicitly (none is dropped).', 1;
    DECLARE @index SYSNAME, @filtered BIT;
    SET @n = 1;
    WHILE @n <= (SELECT COUNT(*) FROM @indexes)
    BEGIN
        SELECT @table = table_name, @index = index_name, @filtered = filtered FROM @indexes WHERE row_id = @n;
        IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.' + @table) AND name = @index)
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM sys.indexes i
                CROSS APPLY (SELECT REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    i.filter_definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'') AS fingerprint) f
                WHERE i.object_id = OBJECT_ID(N'dbo.' + @table) AND i.name = @index
                  AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0
                  AND i.ignore_dup_key = 0 AND i.is_primary_key = 0 AND i.type IN (1, 2)
                  AND i.has_filter = @filtered
                  AND ((@filtered = 0 AND i.filter_definition IS NULL)
                       OR (@filtered = 1 AND f.fingerprint COLLATE Latin1_General_100_BIN2 = N'status=''PENDING''' COLLATE Latin1_General_100_BIN2))
                  AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 2
                  AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                              WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 1 AND c.name = N'member_id')
                  AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                              WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 2 AND c.name = N'sport_id')
            )
            BEGIN
                SET @message = N'Incorrect/disabled required index: dbo.' + @table + N'.' + @index;
                THROW 50015, @message, 1;
            END;
        END
        ELSE
        BEGIN
            SET @sql = N'CREATE UNIQUE INDEX ' + QUOTENAME(@index) + N' ON dbo.' + QUOTENAME(@table) + N'(member_id, sport_id)'
                + CASE WHEN @filtered = 1 THEN N' WHERE status = ''PENDING''' ELSE N'' END + N';';
            EXEC sys.sp_executesql @sql;
        END;
        SET @n += 1;
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Duplicate levels/PENDING rows, orphaned FKs and invalid enums fail and roll back.
-- Verification after approved execution:
-- SELECT name, is_unique, is_disabled, has_filter, filter_definition FROM sys.indexes
-- WHERE object_id IN (OBJECT_ID(N'dbo.member_sport_level'), OBJECT_ID(N'dbo.skill_assessment'));
-- Rollback is a separate authorized operation that discards Phase 1 data after backup:
-- DROP TABLE dbo.skill_assessment;
-- DROP TABLE dbo.member_sport_level;
