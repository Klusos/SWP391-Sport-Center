-- Additive Course age-policy migration (SQL Server 2016+).
-- Target for this reviewed release: SportCenterDemo. Never run against another database by mistake.
-- Back up the selected database, verify the backup, stop application writers, and rehearse on a
-- restored copy before execution. Run as one standalone SSMS batch; do not add GO.
-- Existing courses receive RECOMMENDED. Existing age-bound columns and values are preserved.
-- This script is manual; application startup remains ddl-auto=validate.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDemo'
    THROW 51091, 'Select the verified SportCenterDemo database before applying the course age-policy migration.', 1;
IF @@SERVERNAME <> N'sportcenter-release-sqlserver'
    THROW 51103, 'The SQL Server name differs from the verified release container; stop and verify the target.', 1;
IF CONVERT(varchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status')) <> 'ONLINE'
    THROW 51104, 'SportCenterDemo is not ONLINE; stop and verify database state before migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 51092, 'Run the course age-policy migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.course', N'U') IS NULL
    THROW 51093, 'Expected dbo.course before applying the course age-policy migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- Preserve the existing nullable INT bounds. Stop if this database has a different contract.
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.course')
          AND c.name = N'target_age_min' AND t.name = N'int' AND c.is_nullable = 1)
       OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.course')
          AND c.name = N'target_age_max' AND t.name = N'int' AND c.is_nullable = 1)
        THROW 51094, 'Course age-bound metadata differs from the reviewed nullable INT columns; inspect before migration.', 1;

    IF EXISTS (SELECT 1 FROM dbo.course
               WHERE target_age_min < 0 OR target_age_max < 0
                  OR (target_age_min IS NOT NULL AND target_age_max IS NOT NULL
                      AND target_age_min > target_age_max))
        THROW 51095, 'Existing Course age bounds violate the proposed nonnegative ordered-range constraint; resolve through a reviewed data plan.', 1;

    -- Do not collide with unrelated checks that already constrain the age-policy columns.
    IF EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.course')
          AND (definition LIKE N'%target_age_min%' OR definition LIKE N'%target_age_max%' OR definition LIKE N'%age_policy%')
          AND name NOT IN (N'ck_course_age_policy_values', N'ck_course_age_policy_required_bounds', N'ck_course_age_range'))
        THROW 51096, 'An existing Course CHECK constraint references age-policy columns; inspect its definition before migration.', 1;

    -- Same-name constraints must also match the intended semantics; never trust a name alone.
    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID(N'dbo.course')
                 AND name = N'ck_course_age_policy_values'
                 AND (definition NOT LIKE N'%RECOMMENDED%' OR definition NOT LIKE N'%REQUIRED%'))
       OR EXISTS (SELECT 1 FROM sys.check_constraints
                  WHERE parent_object_id = OBJECT_ID(N'dbo.course')
                    AND name = N'ck_course_age_policy_required_bounds'
                    AND (definition NOT LIKE N'%REQUIRED%' OR definition NOT LIKE N'%target_age_min%'
                         OR definition NOT LIKE N'%target_age_max%' OR definition NOT LIKE N'%IS NOT NULL%'))
       OR EXISTS (SELECT 1 FROM sys.check_constraints
                  WHERE parent_object_id = OBJECT_ID(N'dbo.course')
                    AND name = N'ck_course_age_range'
                    AND (definition NOT LIKE N'%target_age_min%' OR definition NOT LIKE N'%target_age_max%'
                         OR definition NOT LIKE N'%>=%' OR definition NOT LIKE N'%<=%'))
        THROW 51105, 'A named Course age constraint has an unexpected definition; inspect before migration.', 1;

    -- Adding a NOT NULL column with a default fills every existing row without changing age bounds.
    IF COL_LENGTH(N'dbo.course', N'age_policy') IS NULL
    BEGIN
        ALTER TABLE dbo.course ADD age_policy VARCHAR(20) NOT NULL
            CONSTRAINT df_course_age_policy DEFAULT ('RECOMMENDED') WITH VALUES;
    END
    ELSE
    BEGIN
        -- The outer batch is compiled before the missing column can be added. Defer every
        -- statement that names age_policy until this separate dynamic batch is compiled.
        EXEC sys.sp_executesql N'
            IF NOT EXISTS (
                SELECT 1 FROM sys.columns c
                JOIN sys.types t ON t.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID(N''dbo.course'')
                  AND c.name = N''age_policy'' AND t.name = N''varchar'' AND c.max_length = 20)
                THROW 51097, ''Existing dbo.course.age_policy is not VARCHAR(20); inspect rather than converting it automatically.'', 1;

            IF EXISTS (SELECT 1 FROM dbo.course WHERE age_policy IS NULL)
                UPDATE dbo.course SET age_policy = ''RECOMMENDED'' WHERE age_policy IS NULL;

            IF EXISTS (SELECT 1 FROM dbo.course WHERE age_policy NOT IN (''RECOMMENDED'', ''REQUIRED''))
                THROW 51098, ''Existing age_policy contains unsupported values; inspect before migration.'', 1;

            IF EXISTS (SELECT 1 FROM sys.columns
                       WHERE object_id = OBJECT_ID(N''dbo.course'') AND name = N''age_policy'' AND is_nullable = 1)
                ALTER TABLE dbo.course ALTER COLUMN age_policy VARCHAR(20) NOT NULL;

            IF NOT EXISTS (SELECT 1 FROM sys.default_constraints
                           WHERE parent_object_id = OBJECT_ID(N''dbo.course'')
                             AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N''dbo.course''), N''age_policy'', ''ColumnId''))
            BEGIN
                ALTER TABLE dbo.course ADD CONSTRAINT df_course_age_policy
                    DEFAULT (''RECOMMENDED'') FOR age_policy;
            END
            ELSE IF NOT EXISTS (
                SELECT 1 FROM sys.default_constraints
                WHERE parent_object_id = OBJECT_ID(N''dbo.course'')
                  AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N''dbo.course''), N''age_policy'', ''ColumnId'')
                  AND LOWER(REPLACE(REPLACE(definition, N''('', N''''), N'')'', N'''')) LIKE N''%recommended%'')
                THROW 51099, ''Existing age_policy default is not RECOMMENDED; inspect before migration.'', 1;
        ';
    END;

    -- This nested batch is compiled only after the column-creation/validation branch above runs.
    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.course WHERE age_policy NOT IN (''RECOMMENDED'', ''REQUIRED''))
            THROW 51100, ''Course age_policy must be RECOMMENDED or REQUIRED.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.course
                   WHERE age_policy = ''REQUIRED''
                     AND (target_age_min IS NULL OR target_age_max IS NULL))
            THROW 51101, ''Every REQUIRED course must have both age bounds before the constraint can be enabled.'', 1;

        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                       WHERE parent_object_id = OBJECT_ID(N''dbo.course'') AND name = N''ck_course_age_policy_values'')
            ALTER TABLE dbo.course WITH CHECK ADD CONSTRAINT ck_course_age_policy_values
                CHECK (age_policy IN (''RECOMMENDED'', ''REQUIRED''));
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                       WHERE parent_object_id = OBJECT_ID(N''dbo.course'') AND name = N''ck_course_age_policy_required_bounds'')
            ALTER TABLE dbo.course WITH CHECK ADD CONSTRAINT ck_course_age_policy_required_bounds
                CHECK (age_policy <> ''REQUIRED'' OR (target_age_min IS NOT NULL AND target_age_max IS NOT NULL));
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                       WHERE parent_object_id = OBJECT_ID(N''dbo.course'') AND name = N''ck_course_age_range'')
            ALTER TABLE dbo.course WITH CHECK ADD CONSTRAINT ck_course_age_range
                CHECK ((target_age_min IS NULL OR target_age_min >= 0)
                   AND (target_age_max IS NULL OR target_age_max >= 0)
                   AND (target_age_min IS NULL OR target_age_max IS NULL OR target_age_min <= target_age_max));
    ';

    -- Ensure all named constraints are enabled and trusted, including on a rerun.
    ALTER TABLE dbo.course WITH CHECK CHECK CONSTRAINT ck_course_age_policy_values;
    ALTER TABLE dbo.course WITH CHECK CHECK CONSTRAINT ck_course_age_policy_required_bounds;
    ALTER TABLE dbo.course WITH CHECK CHECK CONSTRAINT ck_course_age_range;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID(N'dbo.course')
                 AND name IN (N'ck_course_age_policy_values', N'ck_course_age_policy_required_bounds', N'ck_course_age_range')
                 AND (is_disabled = 1 OR is_not_trusted = 1))
        THROW 51102, 'Course age-policy constraints are disabled or untrusted after migration.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Post-migration verification (still part of this standalone script; read-only).
SELECT c.name AS column_name, t.name AS data_type, c.max_length AS max_length_bytes, c.is_nullable
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.course') AND c.name IN (N'target_age_min', N'target_age_max', N'age_policy')
ORDER BY c.column_id;

SELECT name AS constraint_name, definition, is_disabled, is_not_trusted
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.course')
  AND name IN (N'ck_course_age_policy_values', N'ck_course_age_policy_required_bounds', N'ck_course_age_range')
ORDER BY name;

EXEC sys.sp_executesql N'
    SELECT age_policy, COUNT_BIG(*) AS course_count
    FROM dbo.course
    GROUP BY age_policy
    ORDER BY age_policy;';

-- Rollback guidance: if this batch itself fails, XACT_ABORT/CATCH rolls it back.
-- Before application code writes REQUIRED values, a separately reviewed rollback may drop only
-- the three named CHECK constraints, the df_course_age_policy default, and dbo.course.age_policy.
-- Once REQUIRED values have been written, do not drop the column: restore a verified backup or
-- coordinate an application/data downgrade without losing policy intent.
