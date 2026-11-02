-- CLONE VALIDATION COPY. Original phase11 script remains authoritative and unchanged.
-- FAIL-CLOSED target guards pin the owner-verified SQL Server identity and the clone database.
-- Target database is fixed to SportCenterPtE2E; this script never issues USE or switches databases.
-- Additive Membership cancellation schema (SQL Server 2016+).
-- Back up the selected database and stop application writers before running.
-- Run as one standalone batch; do not add GO or wrap this script in another transaction.
-- Adds nullable dbo.membership.purchase_payment_id (the payment that bought the membership, so a cancelled
-- upcoming membership can be refunded), cancelled_at and cancel_reason. Existing rows keep NULLs.
-- The column is deliberately not named source_payment_id: older local databases may already have that
-- column from an abandoned Phase 9 migration, tied to other columns by a CHECK constraint.
-- Rerunnable: columns and the foreign key that already exist are kept.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF CONVERT(varbinary(256), DB_NAME()) <> CONVERT(varbinary(256), N'SportCenterPtE2E')
    THROW 51989, 'Select SportCenterPtE2E before applying this clone-only migration.', 1;
DECLARE @ExpectedServerIdentity nvarchar(128) = N'sportcenter-recovery-sql';
IF CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('ServerName'))) <> CONVERT(varbinary(256), @ExpectedServerIdentity)
   OR CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('MachineName'))) <> CONVERT(varbinary(256), N'sportcenter-recovery-sql')
   OR SERVERPROPERTY('InstanceName') IS NOT NULL
    THROW 51991, 'Connected SQL Server identity does not match the verified default clone instance.', 1;
IF @@TRANCOUNT <> 0
    THROW 51202, 'Run the membership cancellation migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.membership', N'U') IS NULL OR OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 51203, 'Expected dbo.membership and dbo.payment before the cancellation migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.membership', N'purchase_payment_id') IS NULL
        ALTER TABLE dbo.membership ADD purchase_payment_id INT NULL;
    IF COL_LENGTH(N'dbo.membership', N'cancelled_at') IS NULL
        ALTER TABLE dbo.membership ADD cancelled_at DATETIME2(6) NULL;
    IF COL_LENGTH(N'dbo.membership', N'cancel_reason') IS NULL
        ALTER TABLE dbo.membership ADD cancel_reason NVARCHAR(500) NULL;

    -- Dynamic SQL: the column may have been added earlier in this batch.
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE parent_object_id = OBJECT_ID(N'dbo.membership') AND name = N'fk_membership_purchase_payment')
        EXEC sys.sp_executesql N'
            ALTER TABLE dbo.membership WITH CHECK ADD CONSTRAINT fk_membership_purchase_payment
                FOREIGN KEY (purchase_payment_id) REFERENCES dbo.payment(id);';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
