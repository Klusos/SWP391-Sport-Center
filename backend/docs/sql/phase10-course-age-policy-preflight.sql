-- Read-only preflight for phase10-course-age-policy.sql.
-- Run only after explicitly selecting the intended target database. This script is read-only.
SET NOCOUNT ON;

SELECT @@SERVERNAME AS server_name,
       CAST(SERVERPROPERTY('ProductVersion') AS varchar(30)) AS sql_server_version,
       DB_NAME() AS database_name,
       CONVERT(varchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status')) AS database_status;

IF OBJECT_ID(N'dbo.course', N'U') IS NULL
    THROW 51093, 'Expected dbo.course in the selected database; no migration should be attempted.', 1;

SELECT s.name AS schema_name,
       t.name AS table_name,
       c.name AS column_name,
       ty.name AS data_type,
       c.max_length AS max_length_bytes,
       c.is_nullable
FROM sys.schemas AS s
JOIN sys.tables AS t ON t.schema_id = s.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE s.name = N'dbo'
  AND t.name = N'course'
  AND c.name IN (N'target_age_min', N'target_age_max', N'age_policy')
ORDER BY c.column_id;

SELECT name AS constraint_name,
       definition,
       is_disabled,
       is_not_trusted
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.course')
ORDER BY name;

SELECT dc.name AS constraint_name,
       c.name AS column_name,
       dc.definition
FROM sys.default_constraints AS dc
JOIN sys.columns AS c
  ON c.object_id = dc.parent_object_id
 AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID(N'dbo.course')
ORDER BY c.column_id;

-- Aggregate only: this does not reveal course names or other stored content.
SELECT COUNT_BIG(*) AS course_rows,
       SUM(CONVERT(bigint, CASE WHEN target_age_min < 0 THEN 1 ELSE 0 END)) AS negative_minimum_rows,
       SUM(CONVERT(bigint, CASE WHEN target_age_max < 0 THEN 1 ELSE 0 END)) AS negative_maximum_rows,
       SUM(CONVERT(bigint, CASE
           WHEN target_age_min IS NOT NULL
            AND target_age_max IS NOT NULL
            AND target_age_min > target_age_max THEN 1 ELSE 0 END)) AS inverted_range_rows
FROM dbo.course;

-- If age_policy already exists, review this aggregate before migration.
IF COL_LENGTH(N'dbo.course', N'age_policy') IS NOT NULL
BEGIN
    EXEC sys.sp_executesql N'
        SELECT COUNT_BIG(*) AS null_policy_rows,
               SUM(CONVERT(bigint, CASE WHEN age_policy NOT IN (''RECOMMENDED'', ''REQUIRED'') THEN 1 ELSE 0 END)) AS unsupported_policy_rows,
               SUM(CONVERT(bigint, CASE WHEN age_policy = ''REQUIRED''
                    AND (target_age_min IS NULL OR target_age_max IS NULL) THEN 1 ELSE 0 END)) AS required_missing_bound_rows
        FROM dbo.course;';
END;
