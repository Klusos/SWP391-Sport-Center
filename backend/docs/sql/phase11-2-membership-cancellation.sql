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

IF DB_NAME() <> N'SportCenterDB'
    THROW 51201, 'Select SportCenterDB before applying the membership cancellation migration.', 1;
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
