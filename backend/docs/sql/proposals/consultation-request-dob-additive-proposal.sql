/*
 * Proposal only. Do not execute without separate target review and authorization.
 * Adds a nullable date-only DOB field for new Guest consultation requests.
 * Existing age values and all historical rows are preserved; no backfill occurs.
 */
SET XACT_ABORT ON;

IF NULLIF(N'$(ExpectedServer)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabase)', N'') IS NULL
   OR NULLIF(N'$(ExpectedDatabaseId)', N'') IS NULL
   OR NULLIF(N'$(ExpectedProductVersion)', N'') IS NULL
    THROW 51000, 'Supply explicit ExpectedServer, ExpectedDatabase, ExpectedDatabaseId and ExpectedProductVersion SQLCMD values.', 1;

IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) <> N'$(ExpectedServer)'
   OR DB_NAME() <> N'$(ExpectedDatabase)'
   OR DB_ID() <> TRY_CONVERT(int, N'$(ExpectedDatabaseId)')
   OR CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) <> N'$(ExpectedProductVersion)'
    THROW 51004, 'Connected SQL Server or database identity does not match the explicitly approved target.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE database_id = DB_ID() AND state_desc = N'ONLINE')
    THROW 51005, 'Target database is not ONLINE.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- Recheck target identity inside the protected operation immediately before schema inspection/DDL.
    IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) <> N'$(ExpectedServer)'
       OR DB_NAME() <> N'$(ExpectedDatabase)'
       OR DB_ID() <> TRY_CONVERT(int, N'$(ExpectedDatabaseId)')
       OR CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) <> N'$(ExpectedProductVersion)'
        THROW 51006, 'Target identity changed before migration; transaction will be rolled back.', 1;

    DECLARE @table_id int = OBJECT_ID(N'dbo.consultation_request', N'U');
    IF @table_id IS NULL
        THROW 51001, 'Expected dbo.consultation_request table is missing.', 1;

    DECLARE @column_id int = COLUMNPROPERTY(@table_id, N'date_of_birth', 'ColumnId');
    IF @column_id IS NULL
    BEGIN
        ALTER TABLE dbo.consultation_request
            ADD date_of_birth date NULL;
    END
    ELSE IF NOT EXISTS (
        SELECT 1
        FROM sys.columns AS c
        JOIN sys.types AS t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = @table_id
          AND c.name = N'date_of_birth'
          AND t.name = N'date'
          AND c.max_length = 3
          AND c.is_nullable = 1
          AND c.is_identity = 0
          AND c.is_computed = 0
    )
    BEGIN
        THROW 51002, 'date_of_birth exists with an unexpected definition; no change made.', 1;
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.columns AS c
        JOIN sys.types AS t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = @table_id
          AND c.name = N'date_of_birth'
          AND t.name = N'date'
          AND c.max_length = 3
          AND c.is_nullable = 1
          AND c.is_identity = 0
          AND c.is_computed = 0
    )
        THROW 51003, 'Postcondition failed for dbo.consultation_request.date_of_birth.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
