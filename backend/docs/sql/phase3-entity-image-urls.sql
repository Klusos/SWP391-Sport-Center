-- SQL Server 2016+ incremental migration for local SportCenter images.
-- Back up the target database and stop application writers before running.
-- Run as one standalone batch, without GO or an outer transaction.
-- Idempotent: preserves all rows and never drops or rebuilds objects.
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
    THROW 50301, 'Select SportCenterDB before running the image URL migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50302, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase3.EntityImageUrls.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50303, 'Could not acquire the entity image URL migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.sport', N'U') IS NULL
       OR OBJECT_ID(N'dbo.coach', N'U') IS NULL
       OR OBJECT_ID(N'dbo.facility', N'U') IS NULL
        THROW 50304, 'Expected dbo.sport, dbo.coach, and dbo.facility tables.', 1;

    DECLARE @targets TABLE (table_name SYSNAME PRIMARY KEY);
    INSERT @targets VALUES (N'sport'), (N'coach'), (N'facility');
    DECLARE @table SYSNAME, @sql NVARCHAR(MAX);
    DECLARE image_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT table_name FROM @targets;
    OPEN image_cursor;
    FETCH NEXT FROM image_cursor INTO @table;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF COL_LENGTH(N'dbo.' + @table, N'image_url') IS NULL
        BEGIN
            SET @sql = N'ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD image_url NVARCHAR(500) NULL;';
            EXEC sys.sp_executesql @sql;
        END
        ELSE IF NOT EXISTS (
            SELECT 1 FROM sys.columns c
            WHERE c.object_id = OBJECT_ID(N'dbo.' + @table)
              AND c.name = N'image_url'
              AND c.system_type_id = TYPE_ID(N'nvarchar')
              AND c.user_type_id = c.system_type_id
              AND c.max_length = 1000
              AND c.is_nullable = 1
              AND c.is_computed = 0
              AND c.generated_always_type = 0
        )
        BEGIN
            CLOSE image_cursor;
            DEALLOCATE image_cursor;
            THROW 50305, 'An existing image_url column has incompatible metadata; inspect it before migration.', 1;
        END;
        FETCH NEXT FROM image_cursor INTO @table;
    END;
    CLOSE image_cursor;
    DEALLOCATE image_cursor;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'image_cursor') >= 0 CLOSE image_cursor;
    IF CURSOR_STATUS('local', 'image_cursor') > -3 DEALLOCATE image_cursor;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
