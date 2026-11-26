/* Read-only post-migration verification. SQLCMD mode required; supply the same approved identity values. */
SET NOCOUNT ON;

IF NULLIF(N'$(ExpectedServer)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabase)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabaseId)', N'') IS NULL
   OR NULLIF(N'$(ExpectedProductVersion)', N'') IS NULL
    THROW 51200, 'Supply explicit ExpectedServer, ExpectedDatabase, ExpectedDatabaseId and ExpectedProductVersion SQLCMD values.', 1;

IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) <> N'$(ExpectedServer)'
   OR DB_NAME() <> N'$(ExpectedDatabase)'
   OR DB_ID() <> TRY_CONVERT(int, N'$(ExpectedDatabaseId)')
   OR CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) <> N'$(ExpectedProductVersion)'
    THROW 51201, 'Connected SQL Server or database identity does not match the explicitly approved target.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE database_id = DB_ID() AND state_desc = N'ONLINE')
    THROW 51202, 'Target database is not ONLINE.', 1;

DECLARE @table_id int = OBJECT_ID(N'dbo.consultation_request', N'U');
IF @table_id IS NULL
    THROW 51203, 'Expected dbo.consultation_request table is missing.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.types ty ON ty.user_type_id = c.user_type_id
    WHERE c.object_id = @table_id AND c.name = N'date_of_birth'
      AND ty.name = N'date' AND c.max_length = 3 AND c.is_nullable = 1
      AND c.is_identity = 0 AND c.is_computed = 0
)
    THROW 51204, 'dbo.consultation_request.date_of_birth is absent or has an incompatible definition.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = @table_id AND name = N'age')
    THROW 51205, 'Legacy age column is absent; unexpected schema drift.', 1;

SELECT
    CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS server_identity,
    CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) AS product_version,
    DB_NAME() AS database_name,
    DB_ID() AS database_id,
    N'dbo.consultation_request.date_of_birth' AS verified_object,
    N'date NULL' AS expected_definition,
    (SELECT COUNT_BIG(*) FROM dbo.consultation_request) AS consultation_row_count,
    (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NULL) AS legacy_age_null_count,
    (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE age IS NOT NULL) AS legacy_age_nonnull_count,
    (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE date_of_birth IS NULL) AS dob_null_count,
    (SELECT COUNT_BIG(*) FROM dbo.consultation_request WHERE date_of_birth IS NOT NULL) AS dob_nonnull_count;
