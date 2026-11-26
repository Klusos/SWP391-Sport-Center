/* Read-only preflight for consultation-request-dob-additive-proposal.sql.
   SQLCMD mode required. Supply all four values explicitly for the approved target. */
SET NOCOUNT ON;

IF NULLIF(N'$(ExpectedServer)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabase)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabaseId)', N'') IS NULL
   OR NULLIF(N'$(ExpectedProductVersion)', N'') IS NULL
    THROW 51100, 'Supply explicit ExpectedServer, ExpectedDatabase, ExpectedDatabaseId and ExpectedProductVersion SQLCMD values.', 1;

IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) <> N'$(ExpectedServer)'
   OR DB_NAME() <> N'$(ExpectedDatabase)'
   OR DB_ID() <> TRY_CONVERT(int, N'$(ExpectedDatabaseId)')
   OR CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) <> N'$(ExpectedProductVersion)'
    THROW 51101, 'Connected SQL Server or database identity does not match the explicitly approved target.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE database_id = DB_ID() AND state_desc = N'ONLINE')
    THROW 51102, 'Target database is not ONLINE.', 1;

DECLARE @table_id int = OBJECT_ID(N'dbo.consultation_request', N'U');
IF @table_id IS NULL
    THROW 51103, 'Expected dbo.consultation_request table is missing.', 1;

DECLARE @dob_count int = (SELECT COUNT(*) FROM sys.columns WHERE object_id = @table_id AND name = N'date_of_birth');
IF @dob_count = 1 AND NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.types t ON t.user_type_id = c.user_type_id
    WHERE c.object_id = @table_id AND c.name = N'date_of_birth'
      AND t.name = N'date' AND c.max_length = 3 AND c.is_nullable = 1
      AND c.is_identity = 0 AND c.is_computed = 0
)
    THROW 51104, 'date_of_birth exists with an unexpected definition; no migration should proceed.', 1;

IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = @table_id AND name = N'date_of_birth') > 1
    THROW 51105, 'Unexpected duplicate date_of_birth metadata.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = @table_id AND name = N'age')
    THROW 51106, 'Expected legacy age column is absent; schema drift requires review.', 1;

DECLARE @summary_sql nvarchar(max);
IF @dob_count = 0
BEGIN
    SET @summary_sql = N'
        SELECT CONVERT(nvarchar(128), SERVERPROPERTY(''ServerName'')) AS server_identity,
               CONVERT(nvarchar(128), SERVERPROPERTY(''ProductVersion'')) AS product_version,
               DB_NAME() AS database_name, DB_ID() AS database_id, d.state_desc AS database_state,
               s.name AS schema_name, t.name AS table_name, c.name AS column_name,
               ty.name AS data_type, c.max_length, c.is_nullable,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request) AS consultation_row_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NULL) AS legacy_age_null_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NOT NULL) AS legacy_age_nonnull_count,
               CAST(NULL AS bigint) AS dob_null_count, CAST(NULL AS bigint) AS dob_nonnull_count
        FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        JOIN sys.columns c ON c.object_id = t.object_id
        JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        CROSS JOIN sys.databases d
        WHERE t.object_id = OBJECT_ID(N''dbo.consultation_request'', N''U'')
          AND c.name = N''age'' AND d.database_id = DB_ID()
        ORDER BY c.name;';
END
ELSE
BEGIN
    SET @summary_sql = N'
        SELECT CONVERT(nvarchar(128), SERVERPROPERTY(''ServerName'')) AS server_identity,
               CONVERT(nvarchar(128), SERVERPROPERTY(''ProductVersion'')) AS product_version,
               DB_NAME() AS database_name, DB_ID() AS database_id, d.state_desc AS database_state,
               s.name AS schema_name, t.name AS table_name, c.name AS column_name,
               ty.name AS data_type, c.max_length, c.is_nullable,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request) AS consultation_row_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NULL) AS legacy_age_null_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NOT NULL) AS legacy_age_nonnull_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE date_of_birth IS NULL) AS dob_null_count,
               (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE date_of_birth IS NOT NULL) AS dob_nonnull_count
        FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        JOIN sys.columns c ON c.object_id = t.object_id
        JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        CROSS JOIN sys.databases d
        WHERE t.object_id = OBJECT_ID(N''dbo.consultation_request'', N''U'')
          AND c.name IN (N''age'', N''date_of_birth'') AND d.database_id = DB_ID()
        ORDER BY c.name;';
END;
EXEC sys.sp_executesql @summary_sql;
