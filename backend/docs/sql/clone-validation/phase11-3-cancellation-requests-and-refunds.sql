-- CLONE VALIDATION COPY. Original phase11 script remains authoritative and unchanged.
-- FAIL-CLOSED target guards pin the owner-verified SQL Server identity and the clone database.
-- Target database is fixed to SportCenterPtE2E; this script never issues USE or switches databases.
-- Additive membership cancellation request and refund confirmation schema (SQL Server 2016+).
-- Back up the selected database and stop application writers before running.
-- Run as one standalone batch; do not add GO or wrap this script in another transaction.
-- Adds payment status REFUNDED with dbo.payment.refunded_at/refunded_by (who confirmed the money was returned),
-- and dbo.membership_cancellation_request (a member asks to cancel an upcoming membership; staff approve or reject)
-- with a filtered unique index allowing one PENDING request per membership. Existing rows are not changed.
-- Rerunnable: objects that already exist are kept; a status CHECK that already allows REFUNDED is kept.
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
    THROW 51302, 'Run the cancellation request migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 51303, 'Expected dbo.payment, dbo.membership and dbo.users before the cancellation request migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- Destructive CHECK replacement is permitted only after exact legacy definition review.
    DECLARE @ExpectedLegacyPaymentStatusCheckDefinition nvarchar(4000) = N'([status]=''REFUND_REQUIRED'' OR [status]=''CANCELLED'' OR [status]=''SUCCESS'' OR [status]=''PENDING'')';
    DECLARE @CurrentLegacyPaymentStatusCheckDefinition nvarchar(4000);
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
               AND name = N'ck_payment_status' AND definition NOT LIKE N'%''REFUNDED''%')
    BEGIN
        SELECT @CurrentLegacyPaymentStatusCheckDefinition = definition FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status';
        IF @CurrentLegacyPaymentStatusCheckDefinition IS NULL
           OR CONVERT(varbinary(max), REPLACE(REPLACE(REPLACE(REPLACE(@CurrentLegacyPaymentStatusCheckDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''))
              <> CONVERT(varbinary(max), REPLACE(REPLACE(REPLACE(REPLACE(@ExpectedLegacyPaymentStatusCheckDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''))
            THROW 51994, 'Existing payment status CHECK does not exactly match the owner-verified legacy definition.', 1;
        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
                   AND name = N'ck_payment_status' AND (is_disabled = 1 OR is_not_trusted = 1))
            THROW 51995, 'Existing payment status CHECK must be enabled and trusted before replacement.', 1;
    END;
    -- 1. Allow status = REFUNDED. Only replace the CHECK when it does not mention REFUNDED yet.
    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status'
          AND definition LIKE N'%''REFUNDED''%')
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.payment
                   WHERE status NOT IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED', 'REFUNDED'))
            THROW 51304, 'dbo.payment contains a status outside PENDING, SUCCESS, CANCELLED, REFUND_REQUIRED, REFUNDED; inspect it first.', 1;
        IF EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_status;
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_status
            CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED', 'REFUNDED'));
    END;

    -- 2. Who confirmed the refund, and when.
    IF COL_LENGTH(N'dbo.payment', N'refunded_at') IS NULL
        ALTER TABLE dbo.payment ADD refunded_at DATETIME2(6) NULL;
    IF COL_LENGTH(N'dbo.payment', N'refunded_by') IS NULL
        ALTER TABLE dbo.payment ADD refunded_by INT NULL;
    -- Dynamic SQL: the column may have been added earlier in this batch.
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'fk_payment_refunded_by')
        EXEC sys.sp_executesql N'
            ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT fk_payment_refunded_by
                FOREIGN KEY (refunded_by) REFERENCES dbo.users(id);';

    -- 3. Member cancellation requests.
    IF OBJECT_ID(N'dbo.membership_cancellation_request', N'U') IS NULL
    BEGIN
        IF OBJECT_ID(N'dbo.membership_cancellation_request') IS NOT NULL
            THROW 51305, 'dbo.membership_cancellation_request is occupied by a non-table object.', 1;
        CREATE TABLE dbo.membership_cancellation_request (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_cancellation_request PRIMARY KEY,
            membership_id INT NOT NULL,
            member_id INT NOT NULL,
            reason NVARCHAR(500) NULL,
            status VARCHAR(20) NOT NULL,
            requested_at DATETIME2(6) NOT NULL,
            decided_by INT NULL,
            decided_at DATETIME2(6) NULL,
            decision_note NVARCHAR(500) NULL,
            CONSTRAINT fk_membership_cancellation_request_membership FOREIGN KEY (membership_id) REFERENCES dbo.membership(id),
            CONSTRAINT fk_membership_cancellation_request_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_membership_cancellation_request_decided_by FOREIGN KEY (decided_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_membership_cancellation_request_status
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'))
        );
        CREATE INDEX ix_membership_cancellation_request_member ON dbo.membership_cancellation_request(member_id);
        CREATE INDEX ix_membership_cancellation_request_status ON dbo.membership_cancellation_request(status, requested_at);
    END
    ELSE IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.membership_cancellation_request')
             AND name IN (N'id', N'membership_id', N'member_id', N'reason', N'status', N'requested_at',
                          N'decided_by', N'decided_at', N'decision_note')) <> 9
        THROW 51306, 'dbo.membership_cancellation_request exists but is missing expected columns; inspect it manually.', 1;

    -- 4. At most one PENDING request per membership (the application also checks under a row lock).
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N'dbo.membership_cancellation_request')
                     AND name = N'ux_membership_cancellation_request_pending')
        EXEC sys.sp_executesql N'
            CREATE UNIQUE INDEX ux_membership_cancellation_request_pending
                ON dbo.membership_cancellation_request(membership_id) WHERE status = ''PENDING'';';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
