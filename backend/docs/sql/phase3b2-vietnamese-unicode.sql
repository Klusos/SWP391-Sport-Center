/*
  Phase 3B.2: convert reviewed human-readable text columns to Unicode storage.

  Run only in SportCenterDB after a verified full backup and a successful rehearsal
  on a restored copy. Stop application writers for the duration. This script is
  transactional and fails closed if the schema/dependencies differ from the
  reviewed preflight. It never edits text values or attempts to repair '?' data.
*/
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

IF DB_NAME() <> N'SportCenterDB'
    THROW 50430, 'Unicode migration stopped: current database must be SportCenterDB.', 1;

DECLARE @targets TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL,
    source_type SYSNAME NOT NULL,
    source_max_length_bytes INT NOT NULL,
    target_max_length_bytes INT NOT NULL,
    is_nullable BIT NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT @targets (schema_name, table_name, column_name, source_type,
                 source_max_length_bytes, target_max_length_bytes, is_nullable)
VALUES
    (N'dbo', N'users', N'name', N'varchar', 100, 200, 0),
    (N'dbo', N'sport', N'name', N'varchar', 100, 200, 0),
    (N'dbo', N'sport', N'description', N'text', 16, -1, 1),
    (N'dbo', N'coach_sport', N'certification', N'varchar', 255, 510, 1),
    (N'dbo', N'member_profile', N'goal', N'text', 16, -1, 1),
    (N'dbo', N'course', N'name', N'varchar', 255, 510, 0),
    (N'dbo', N'course', N'description', N'text', 16, -1, 1),
    (N'dbo', N'course', N'learning_outcome', N'text', 16, -1, 1),
    (N'dbo', N'class', N'name', N'varchar', 100, 200, 0),
    (N'dbo', N'course_offering', N'name', N'varchar', 150, 300, 0),
    (N'dbo', N'facility', N'name', N'varchar', 100, 200, 0),
    (N'dbo', N'facility', N'type', N'varchar', 50, 100, 0),
    (N'dbo', N'membership_plan', N'name', N'varchar', 100, 200, 0),
    (N'dbo', N'membership_plan', N'description', N'text', 16, -1, 1);

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @lock_result INT;
    EXEC @lock_result = sys.sp_getapplock
        @Resource = N'SportCenter:Phase3B2:VietnameseUnicodeSchemaMigration',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 0;
    IF @lock_result < 0
        THROW 50431, 'Unicode migration stopped: could not acquire migration lock.', 1;

    -- Permit only the reviewed original definition or the exact converted definition.
    -- The latter makes a fully or partially applied migration safe to rerun.
    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        LEFT JOIN sys.schemas AS s ON s.name = t.schema_name
        LEFT JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        LEFT JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        LEFT JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL
           OR c.is_identity = 1 OR c.is_computed = 1 OR c.generated_always_type <> 0
           OR c.is_nullable <> t.is_nullable
           OR c.collation_name <> N'Chinese_PRC_CI_AS'
           OR NOT (
                (ty.name = t.source_type AND c.max_length = t.source_max_length_bytes)
                OR (ty.name = N'nvarchar' AND c.max_length = t.target_max_length_bytes)
           )
    )
        THROW 50432, 'Unicode migration stopped: a candidate column is missing or differs from the reviewed type, length, nullability, or collation.', 1;

    -- No candidate column may acquire dependencies between preflight and execution.
    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.default_constraints AS dc ON dc.parent_object_id = tb.object_id
                                            AND dc.parent_column_id = c.column_id
    )
        THROW 50433, 'Unicode migration stopped: a candidate column has a default constraint.', 1;

    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.check_constraints AS cc ON cc.parent_object_id = tb.object_id
         AND (cc.parent_column_id = c.column_id
              OR cc.definition LIKE N'%' + QUOTENAME(c.name) + N'%'
              OR cc.definition LIKE N'%' + c.name + N'%')
    )
        THROW 50434, 'Unicode migration stopped: a check constraint references a candidate column.', 1;

    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.foreign_key_columns AS fkc
          ON (fkc.parent_object_id = tb.object_id AND fkc.parent_column_id = c.column_id)
          OR (fkc.referenced_object_id = tb.object_id AND fkc.referenced_column_id = c.column_id)
    )
        THROW 50435, 'Unicode migration stopped: a candidate column participates in a foreign key.', 1;

    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.fulltext_index_columns AS fic ON fic.object_id = tb.object_id AND fic.column_id = c.column_id
    )
        THROW 50436, 'Unicode migration stopped: a candidate column belongs to a full-text index.', 1;

    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.sql_expression_dependencies AS d ON d.referenced_id = tb.object_id
         AND (d.referenced_minor_id = c.column_id OR d.referenced_minor_id = 0)
    )
        THROW 50437, 'Unicode migration stopped: a catalogued SQL module references a candidate column.', 1;

    -- The only reviewed candidate index is the UNIQUE NONCLUSTERED constraint on
    -- sport.name. Discover its name and recreation metadata; never assume a name.
    DECLARE @candidate_index_count INT;
    SELECT @candidate_index_count = COUNT(*)
    FROM (
        SELECT DISTINCT i.object_id, i.index_id
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.index_columns AS ic ON ic.object_id = tb.object_id AND ic.column_id = c.column_id
        JOIN sys.indexes AS i ON i.object_id = ic.object_id AND i.index_id = ic.index_id
    ) AS found_indexes;

    IF @candidate_index_count <> 1
        THROW 50438, 'Unicode migration stopped: expected exactly one candidate index, on dbo.sport.name.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.schemas AS s
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = N'sport'
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = N'name'
        JOIN sys.key_constraints AS kc ON kc.parent_object_id = tb.object_id AND kc.type = N'UQ'
        JOIN sys.indexes AS i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
        WHERE s.name = N'dbo' AND kc.is_system_named = 0
          AND i.is_unique = 1 AND i.type = 2
          AND i.has_filter = 0 AND i.filter_definition IS NULL
          AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND (SELECT COUNT(*) FROM sys.index_columns AS ix
               WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                 AND ix.is_included_column = 0 AND ix.key_ordinal > 0) = 1
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ix
                      WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                        AND ix.column_id = c.column_id AND ix.key_ordinal = 1
                        AND ix.is_included_column = 0 AND ix.is_descending_key = 0)
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ix
                          WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                            AND ix.is_included_column = 1)
    )
        THROW 50439, 'Unicode migration stopped: dbo.sport.name unique constraint/index no longer has the reviewed structure.', 1;

    DECLARE @sport_object_id INT = OBJECT_ID(N'dbo.sport', N'U');
    DECLARE @sport_name_column_id INT;
    DECLARE @sport_name_type SYSNAME;
    DECLARE @sport_name_constraint SYSNAME;
    DECLARE @sport_name_constraint_object_id INT;
    DECLARE @sport_name_index_id INT;
    DECLARE @verified_sport_name_constraints TABLE (
        constraint_object_id INT NOT NULL,
        constraint_name SYSNAME NOT NULL,
        index_id INT NOT NULL
    );
    DECLARE @verified_sport_name_constraint_count INT;
    DECLARE @sport_name_needs_rebuild BIT;
    DECLARE @sport_index_filegroup SYSNAME;
    DECLARE @sport_index_fill_factor TINYINT;
    DECLARE @sport_index_pad BIT;
    DECLARE @sport_index_ignore_dup_key BIT;
    DECLARE @sport_index_stats_norecompute BIT;
    DECLARE @sport_index_allow_row_locks BIT;
    DECLARE @sport_index_allow_page_locks BIT;
    DECLARE @sport_index_optimize_sequential BIT = 0;
    DECLARE @sport_index_suppressed_dup_messages BIT = 0;
    DECLARE @sport_index_auto_created BIT = 0;
    DECLARE @post_optimize_sequential BIT = 0;
    DECLARE @post_suppressed_dup_messages BIT = 0;
    DECLARE @post_auto_created BIT = 0;
    DECLARE @sport_name_post_index_id INT;
    DECLARE @sport_name_post_constraint_count INT;
    DECLARE @constraint_sql NVARCHAR(MAX);
    DECLARE @index_object_query NVARCHAR(MAX);

    SELECT @sport_name_column_id = c.column_id, @sport_name_type = ty.name
    FROM sys.columns AS c
    JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
    WHERE c.object_id = @sport_object_id AND c.name = N'name';

    -- Capture only the exact reviewed UNIQUE NONCLUSTERED key on sport.name.
    -- Keep its catalog object and index IDs with the discovered name so the
    -- settings query below cannot accidentally read another UQ on dbo.sport.
    INSERT @verified_sport_name_constraints (constraint_object_id, constraint_name, index_id)
    SELECT kc.object_id, kc.name, i.index_id
    FROM sys.key_constraints AS kc
    JOIN sys.indexes AS i
      ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
    WHERE kc.parent_object_id = @sport_object_id
      AND kc.type = N'UQ'
      AND kc.is_system_named = 0
      AND i.is_unique_constraint = 1
      AND i.is_unique = 1
      AND i.type = 2
      AND i.has_filter = 0
      AND i.filter_definition IS NULL
      AND i.is_disabled = 0
      AND i.is_hypothetical = 0
      AND (SELECT COUNT(*) FROM sys.index_columns AS ix
           WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
             AND ix.is_included_column = 0 AND ix.key_ordinal > 0) = 1
      AND EXISTS (SELECT 1 FROM sys.index_columns AS ix
                  WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                    AND ix.column_id = @sport_name_column_id AND ix.key_ordinal = 1
                    AND ix.is_included_column = 0 AND ix.is_descending_key = 0)
      AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ix
                      WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                        AND ix.is_included_column = 1);

    SELECT @verified_sport_name_constraint_count = COUNT(*)
    FROM @verified_sport_name_constraints;
    IF @verified_sport_name_constraint_count <> 1
        THROW 50448, 'Unicode migration stopped: expected exactly one reviewed UNIQUE constraint on dbo.sport.name.', 1;

    SELECT @sport_name_constraint_object_id = constraint_object_id,
           @sport_name_constraint = constraint_name,
           @sport_name_index_id = index_id
    FROM @verified_sport_name_constraints;

    SELECT @sport_index_filegroup = ds.name,
           @sport_index_fill_factor = i.fill_factor,
           @sport_index_pad = i.is_padded,
           @sport_index_ignore_dup_key = i.ignore_dup_key,
           @sport_index_allow_row_locks = i.allow_row_locks,
           @sport_index_allow_page_locks = i.allow_page_locks,
           @sport_index_stats_norecompute = st.no_recompute
    FROM sys.key_constraints AS kc
    JOIN sys.indexes AS i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
    JOIN sys.data_spaces AS ds ON ds.data_space_id = i.data_space_id
    JOIN sys.stats AS st ON st.object_id = i.object_id AND st.stats_id = i.index_id
    WHERE kc.object_id = @sport_name_constraint_object_id
      AND kc.name = @sport_name_constraint
      AND kc.unique_index_id = @sport_name_index_id
      AND i.is_unique_constraint = 1 AND i.is_unique = 1;

    SET @sport_name_needs_rebuild = CASE WHEN @sport_name_type = N'varchar' THEN 1 ELSE 0 END;

    -- Preserve supported persisted index options and storage placement. Fail closed
    -- for partitioning, compression, auto-created indexes, or 2022-only options
    -- that cannot be represented safely by this migration's constraint DDL.
    IF @sport_object_id IS NULL OR @sport_name_column_id IS NULL
       OR @sport_name_constraint IS NULL OR @sport_name_index_id IS NULL
       OR @sport_index_filegroup IS NULL OR @sport_index_fill_factor IS NULL
       OR @sport_index_fill_factor NOT BETWEEN 0 AND 100
       OR @sport_index_pad IS NULL OR @sport_index_ignore_dup_key IS NULL
       OR @sport_index_stats_norecompute IS NULL OR @sport_index_allow_row_locks IS NULL
       OR @sport_index_allow_page_locks IS NULL
       OR NOT EXISTS (SELECT 1 FROM sys.data_spaces AS ds
                      WHERE ds.name = @sport_index_filegroup AND ds.type = N'FG')
       OR (SELECT COUNT(*) FROM sys.partitions AS p
           WHERE p.object_id = @sport_object_id AND p.index_id = @sport_name_index_id) <> 1
       OR EXISTS (SELECT 1 FROM sys.partitions AS p
                  WHERE p.object_id = @sport_object_id AND p.index_id = @sport_name_index_id
                    AND p.data_compression <> 0)
        THROW 50443, 'Unicode migration stopped: the sport.name constraint uses unsupported index storage/options.', 1;

    -- These catalog columns only exist on newer SQL Server releases. Read them
    -- dynamically where available so the migration remains parseable on SQL Server
    -- versions predating each option. A true auto-created/suppressed-message option
    -- is rejected; sequential-key optimization is preserved when it is enabled.
    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'optimize_for_sequential_key')
    BEGIN
        SET @index_object_query = N'SELECT @value = optimize_for_sequential_key FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_index_id,
            @value = @sport_index_optimize_sequential OUTPUT;
    END;

    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'suppress_dup_key_messages')
    BEGIN
        SET @index_object_query = N'SELECT @value = suppress_dup_key_messages FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_index_id,
            @value = @sport_index_suppressed_dup_messages OUTPUT;
    END;

    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'auto_created')
    BEGIN
        SET @index_object_query = N'SELECT @value = auto_created FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_index_id,
            @value = @sport_index_auto_created OUTPUT;
    END;

    IF @sport_index_suppressed_dup_messages = 1 OR @sport_index_auto_created = 1
        THROW 50444, 'Unicode migration stopped: the sport.name index has an unsupported newer index option.', 1;

    -- Check for values that become equal under the target Unicode collation before
    -- dropping the unique constraint or changing the column type.
    IF EXISTS (
        SELECT CONVERT(NVARCHAR(100), sp.name) COLLATE Chinese_PRC_CI_AS
        FROM dbo.sport AS sp
        GROUP BY CONVERT(NVARCHAR(100), sp.name) COLLATE Chinese_PRC_CI_AS
        HAVING COUNT_BIG(*) > 1
    )
        THROW 50440, 'Unicode migration stopped: dbo.sport.name has a Unicode uniqueness collision.', 1;

    IF @sport_name_needs_rebuild = 1
    BEGIN
        SET @constraint_sql = N'ALTER TABLE [dbo].[sport] DROP CONSTRAINT '
                            + QUOTENAME(@sport_name_constraint) + N';';
        EXEC sys.sp_executesql @constraint_sql;

        ALTER TABLE dbo.sport ALTER COLUMN name NVARCHAR(100)
            COLLATE Chinese_PRC_CI_AS NOT NULL;

        SET @constraint_sql = N'ALTER TABLE [dbo].[sport] ADD CONSTRAINT '
            + QUOTENAME(@sport_name_constraint)
            + N' UNIQUE NONCLUSTERED ([name] ASC) WITH ('
            + N'PAD_INDEX = ' + CASE WHEN @sport_index_pad = 1 THEN N'ON' ELSE N'OFF' END
            + CASE
    WHEN @sport_index_fill_factor = 0
        THEN N''
    ELSE N', FILLFACTOR = '
         + CONVERT(NVARCHAR(3), @sport_index_fill_factor)
  END
            + N', IGNORE_DUP_KEY = ' + CASE WHEN @sport_index_ignore_dup_key = 1 THEN N'ON' ELSE N'OFF' END
            + N', STATISTICS_NORECOMPUTE = ' + CASE WHEN @sport_index_stats_norecompute = 1 THEN N'ON' ELSE N'OFF' END
            + N', ALLOW_ROW_LOCKS = ' + CASE WHEN @sport_index_allow_row_locks = 1 THEN N'ON' ELSE N'OFF' END
            + N', ALLOW_PAGE_LOCKS = ' + CASE WHEN @sport_index_allow_page_locks = 1 THEN N'ON' ELSE N'OFF' END
            + CASE WHEN @sport_index_optimize_sequential = 1
                   THEN N', OPTIMIZE_FOR_SEQUENTIAL_KEY = ON' ELSE N'' END
            + N') ON ' + QUOTENAME(@sport_index_filegroup) + N';';
        EXEC sys.sp_executesql @constraint_sql;
    END;

    DECLARE @schema SYSNAME, @table SYSNAME, @column SYSNAME, @source_type SYSNAME,
            @target_bytes INT, @nullable BIT, @actual_type SYSNAME, @sql NVARCHAR(MAX);
    DECLARE target_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT t.schema_name, t.table_name, t.column_name, t.source_type,
               t.target_max_length_bytes, t.is_nullable
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
        WHERE ty.name = t.source_type
          AND NOT (t.table_name = N'sport' AND t.column_name = N'name');

    OPEN target_cursor;
    FETCH NEXT FROM target_cursor INTO @schema, @table, @column, @source_type, @target_bytes, @nullable;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @actual_type = CASE WHEN @target_bytes = -1 THEN N'nvarchar(max)'
                                ELSE N'nvarchar(' + CONVERT(NVARCHAR(10), @target_bytes / 2) + N')' END;
        SET @sql = N'ALTER TABLE ' + QUOTENAME(@schema) + N'.' + QUOTENAME(@table)
                 + N' ALTER COLUMN ' + QUOTENAME(@column) + N' ' + @actual_type
                 + N' COLLATE Chinese_PRC_CI_AS '
                 + CASE WHEN @nullable = 1 THEN N'NULL;' ELSE N'NOT NULL;' END;
        EXEC sys.sp_executesql @sql;

        FETCH NEXT FROM target_cursor INTO @schema, @table, @column, @source_type, @target_bytes, @nullable;
    END;
    CLOSE target_cursor;
    DEALLOCATE target_cursor;

    -- Confirm that every candidate reached the exact target definition.
    IF EXISTS (
        SELECT 1
        FROM @targets AS t
        JOIN sys.schemas AS s ON s.name = t.schema_name
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = t.table_name
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = t.column_name
        JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
        WHERE ty.name <> N'nvarchar' OR c.max_length <> t.target_max_length_bytes
           OR c.is_nullable <> t.is_nullable OR c.collation_name <> N'Chinese_PRC_CI_AS'
    )
        THROW 50441, 'Unicode migration stopped: post-conversion metadata verification failed.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.schemas AS s
        JOIN sys.tables AS tb ON tb.schema_id = s.schema_id AND tb.name = N'sport'
        JOIN sys.columns AS c ON c.object_id = tb.object_id AND c.name = N'name'
        JOIN sys.key_constraints AS kc ON kc.parent_object_id = tb.object_id AND kc.type = N'UQ'
        JOIN sys.indexes AS i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
        JOIN sys.data_spaces AS ds ON ds.data_space_id = i.data_space_id
        JOIN sys.stats AS st ON st.object_id = i.object_id AND st.stats_id = i.index_id
        WHERE s.name = N'dbo' AND kc.name = @sport_name_constraint
          AND kc.is_system_named = 0
          AND i.is_unique = 1 AND i.is_unique_constraint = 1 AND i.type = 2
          AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.has_filter = 0 AND i.filter_definition IS NULL
          AND i.fill_factor = @sport_index_fill_factor AND i.is_padded = @sport_index_pad
          AND i.ignore_dup_key = @sport_index_ignore_dup_key
          AND i.allow_row_locks = @sport_index_allow_row_locks
          AND i.allow_page_locks = @sport_index_allow_page_locks
          AND st.no_recompute = @sport_index_stats_norecompute
          AND ds.name = @sport_index_filegroup AND ds.type = N'FG'
          AND (SELECT COUNT(*) FROM sys.partitions AS p
               WHERE p.object_id = i.object_id AND p.index_id = i.index_id) = 1
          AND NOT EXISTS (SELECT 1 FROM sys.partitions AS p
                          WHERE p.object_id = i.object_id AND p.index_id = i.index_id
                            AND p.data_compression <> 0)
          AND (SELECT COUNT(*) FROM sys.index_columns AS ix
               WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                 AND ix.is_included_column = 0 AND ix.key_ordinal > 0) = 1
          AND EXISTS (SELECT 1 FROM sys.index_columns AS ix
                      WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                        AND ix.column_id = c.column_id AND ix.key_ordinal = 1
                        AND ix.is_included_column = 0 AND ix.is_descending_key = 0)
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns AS ix
                          WHERE ix.object_id = i.object_id AND ix.index_id = i.index_id
                            AND ix.is_included_column = 1)
    )
        THROW 50442, 'Unicode migration stopped: the sport.name unique constraint/index name, semantics, options, or storage were not preserved.', 1;

    -- Resolve the recreated constraint by its preserved discovered name and
    -- require one UQ row before reading any newer-version index options.
    SELECT @sport_name_post_constraint_count = COUNT(*),
           @sport_name_post_index_id = MAX(unique_index_id)
    FROM sys.key_constraints
    WHERE parent_object_id = @sport_object_id
      AND name = @sport_name_constraint
      AND type = N'UQ';
    IF @sport_name_post_constraint_count <> 1 OR @sport_name_post_index_id IS NULL
        THROW 50449, 'Unicode migration stopped: the recreated sport.name constraint could not be resolved uniquely.', 1;

    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'optimize_for_sequential_key')
    BEGIN
        SET @index_object_query = N'SELECT @value = optimize_for_sequential_key FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_post_index_id,
            @value = @post_optimize_sequential OUTPUT;
        IF @post_optimize_sequential <> @sport_index_optimize_sequential
            THROW 50445, 'Unicode migration stopped: OPTIMIZE_FOR_SEQUENTIAL_KEY was not preserved.', 1;
    END;

    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'suppress_dup_key_messages')
    BEGIN
        SET @index_object_query = N'SELECT @value = suppress_dup_key_messages FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_post_index_id,
            @value = @post_suppressed_dup_messages OUTPUT;
        IF @post_suppressed_dup_messages <> 0
            THROW 50446, 'Unicode migration stopped: SUPPRESS_DUP_KEY_MESSAGES was unexpectedly enabled.', 1;
    END;

    IF EXISTS (SELECT 1 FROM sys.all_columns
               WHERE object_id = OBJECT_ID(N'sys.indexes') AND name = N'auto_created')
    BEGIN
        SET @index_object_query = N'SELECT @value = auto_created FROM sys.indexes WHERE object_id = @object_id AND index_id = @index_id;';
        EXEC sys.sp_executesql @index_object_query,
            N'@object_id INT, @index_id INT, @value BIT OUTPUT',
            @object_id = @sport_object_id, @index_id = @sport_name_post_index_id,
            @value = @post_auto_created OUTPUT;
        IF @post_auto_created <> 0
            THROW 50447, 'Unicode migration stopped: the recreated constraint index has an unexpected auto-created state.', 1;
    END;

    COMMIT TRANSACTION;
    PRINT N'Phase 3B.2 Unicode migration completed. Verify metadata and run Hibernate ddl-auto=validate before reopening application writers.';
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'target_cursor') >= 0 CLOSE target_cursor;
    IF CURSOR_STATUS('local', 'target_cursor') > -3 DEALLOCATE target_cursor;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
