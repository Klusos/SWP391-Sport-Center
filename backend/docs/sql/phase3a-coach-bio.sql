-- SQL Server 2016+ incremental migration for a short public Coach biography.
-- Back up the target database and stop application writers before running.
-- Run as one standalone batch, without GO or an outer transaction.
-- Idempotent: preserves rows and never drops or rebuilds objects.
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
    THROW 50311, 'Select SportCenterDB before running the Coach biography migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50312, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.coach', N'U') IS NULL
    THROW 50313, 'Expected dbo.coach table.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase3A.CoachBio.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50314, 'Could not acquire the Coach biography migration lock; retry with writers stopped.', 1;

    IF COL_LENGTH(N'dbo.coach', N'bio') IS NULL
        ALTER TABLE dbo.coach ADD bio NVARCHAR(1000) NULL;
    ELSE IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.coach')
          AND c.name = N'bio'
          AND c.system_type_id = TYPE_ID(N'nvarchar')
          AND c.user_type_id = c.system_type_id
          AND c.max_length = 2000
          AND c.is_nullable = 1
          AND c.is_computed = 0
          AND c.generated_always_type = 0
    )
        THROW 50315, 'Existing dbo.coach.bio has incompatible metadata; inspect it before migration.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
