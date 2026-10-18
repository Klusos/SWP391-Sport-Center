-- SQL Server 2016+ manual migration for Flow 3 (Payment).
-- Run after the core users table exists. ddl-auto=validate => chạy script này trước khi start app.
-- Do not run inside an outer transaction. Transactional and rerunnable; never drops tables, columns or rows.
-- referenceId meaning: MEMBERSHIP -> membership_plan.id, COURSE_REGISTRATION -> course_registration.id,
-- PT_BOOKING -> personal_training_booking.id (polymorphic, so no FK on reference_id).
-- If dbo.payment was created by an earlier draft of this script, its purpose/status CHECKs are replaced
-- to allow PT_BOOKING and REFUND_REQUIRED. The earlier draft stored CourseOffering ids for
-- COURSE_REGISTRATION; such rows block the migration and must be reviewed manually.
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
    THROW 50601, 'Select SportCenterDB before running the payment migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50602, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment (
            id               INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_payment PRIMARY KEY,
            member_id        INT NOT NULL,
            amount           DECIMAL(12,2) NOT NULL,
            method           VARCHAR(20) NOT NULL,
            purpose          VARCHAR(30) NOT NULL,
            reference_id     INT NOT NULL,
            auto_renew       BIT NULL,
            status           VARCHAR(20) NOT NULL,
            transaction_code VARCHAR(40) NOT NULL,
            processed_by     INT NULL,
            note             NVARCHAR(500) NULL,
            created_at       DATETIME2(6) NOT NULL,
            paid_at          DATETIME2(6) NULL,
            CONSTRAINT uq_payment_txn UNIQUE (transaction_code),
            CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_payment_processed_by FOREIGN KEY (processed_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_payment_method CHECK (method IN ('ONLINE', 'COUNTER')),
            CONSTRAINT ck_payment_purpose CHECK (purpose IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING')),
            CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED')),
            CONSTRAINT ck_payment_amount CHECK (amount >= 0)
        );
    END
    ELSE
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.payment WHERE purpose = 'COURSE_REGISTRATION')
            THROW 50603, 'dbo.payment has COURSE_REGISTRATION rows from the draft schema (reference_id = CourseOffering id). Review them manually first.', 1;

        IF EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_purpose;
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_purpose
            CHECK (purpose IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING'));

        IF EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_status;
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_status
            CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED'));
    END

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_member')
        CREATE INDEX ix_payment_member ON dbo.payment(member_id, created_at DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_paid_at')
        CREATE INDEX ix_payment_paid_at ON dbo.payment(status, paid_at);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_reference')
        CREATE INDEX ix_payment_reference ON dbo.payment(purpose, reference_id, status);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
