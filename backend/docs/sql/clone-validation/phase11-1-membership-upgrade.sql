-- CLONE VALIDATION COPY. Original phase11 script remains authoritative and unchanged.
-- FAIL-CLOSED target guards pin the owner-verified SQL Server identity and the clone database.
-- Target database is fixed to SportCenterPtE2E; this script never issues USE or switches databases.
-- Additive Membership Upgrade schema (SQL Server 2016+).
-- Back up the selected database and stop application writers before running.
-- Run as one standalone batch; do not add GO or wrap this script in another transaction.
-- Adds the UPGRADE payment purpose, dbo.membership_upgrade (one quote per UPGRADE payment) and a
-- filtered unique index allowing one PENDING upgrade payment per member. Existing rows are not changed.
-- Rerunnable: objects that already exist are kept; a purpose CHECK that already allows UPGRADE is kept.
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
    THROW 51102, 'Run the membership upgrade migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL
    THROW 51103, 'Expected dbo.payment, dbo.membership and dbo.membership_plan before the upgrade migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- Destructive CHECK replacement is permitted only after exact legacy definition review.
    DECLARE @ExpectedLegacyPurposeCheckDefinition nvarchar(4000) = N'([purpose]=''PT_BOOKING'' OR [purpose]=''COURSE_REGISTRATION'' OR [purpose]=''MEMBERSHIP'')';
    DECLARE @CurrentLegacyPurposeCheckDefinition nvarchar(4000);
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
               AND name = N'ck_payment_purpose' AND definition NOT LIKE N'%''UPGRADE''%')
    BEGIN
        SELECT @CurrentLegacyPurposeCheckDefinition = definition FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose';
        IF @CurrentLegacyPurposeCheckDefinition IS NULL
           OR CONVERT(varbinary(max), REPLACE(REPLACE(REPLACE(REPLACE(@CurrentLegacyPurposeCheckDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''))
              <> CONVERT(varbinary(max), REPLACE(REPLACE(REPLACE(REPLACE(@ExpectedLegacyPurposeCheckDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''))
            THROW 51992, 'Existing payment purpose CHECK does not exactly match the owner-verified legacy definition.', 1;
        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
                   AND name = N'ck_payment_purpose' AND (is_disabled = 1 OR is_not_trusted = 1))
            THROW 51993, 'Existing payment purpose CHECK must be enabled and trusted before replacement.', 1;
    END;
    -- 1. Allow purpose = UPGRADE. Only replace the CHECK when it does not mention UPGRADE yet.
    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose'
          AND definition LIKE N'%''UPGRADE''%')
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.payment
                   WHERE purpose NOT IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING', 'UPGRADE'))
            THROW 51104, 'dbo.payment contains a purpose outside MEMBERSHIP, COURSE_REGISTRATION, PT_BOOKING, UPGRADE; inspect it first.', 1;
        IF EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_purpose;
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_purpose
            CHECK (purpose IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING', 'UPGRADE'));
    END;

    -- 2. One quote per UPGRADE payment.
    IF OBJECT_ID(N'dbo.membership_upgrade', N'U') IS NULL
    BEGIN
        IF OBJECT_ID(N'dbo.membership_upgrade') IS NOT NULL
            THROW 51105, 'dbo.membership_upgrade is occupied by a non-table object.', 1;
        CREATE TABLE dbo.membership_upgrade (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_upgrade PRIMARY KEY,
            payment_id INT NOT NULL,
            source_membership_id INT NOT NULL,
            target_plan_id INT NOT NULL,
            remaining_days INT NOT NULL,
            expires_at DATETIME2(6) NOT NULL,
            created_at DATETIME2(6) NOT NULL,
            result_membership_id INT NULL,
            CONSTRAINT uq_membership_upgrade_payment UNIQUE (payment_id),
            CONSTRAINT fk_membership_upgrade_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
            CONSTRAINT fk_membership_upgrade_source FOREIGN KEY (source_membership_id) REFERENCES dbo.membership(id),
            CONSTRAINT fk_membership_upgrade_target_plan FOREIGN KEY (target_plan_id) REFERENCES dbo.membership_plan(id),
            CONSTRAINT fk_membership_upgrade_result FOREIGN KEY (result_membership_id) REFERENCES dbo.membership(id),
            CONSTRAINT ck_membership_upgrade_remaining_days CHECK (remaining_days > 0)
        );
        CREATE INDEX ix_membership_upgrade_source ON dbo.membership_upgrade(source_membership_id);
    END
    ELSE IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.membership_upgrade')
             AND name IN (N'id', N'payment_id', N'source_membership_id', N'target_plan_id', N'remaining_days',
                          N'expires_at', N'created_at', N'result_membership_id')) <> 8
        THROW 51106, 'dbo.membership_upgrade exists but is missing expected columns; inspect it manually.', 1;

    -- 3. At most one PENDING upgrade payment per member (the application also checks under a row lock).
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ux_payment_member_pending_upgrade')
    BEGIN
        IF EXISTS (SELECT member_id FROM dbo.payment WHERE purpose = 'UPGRADE' AND status = 'PENDING'
                   GROUP BY member_id HAVING COUNT(*) > 1)
            THROW 51107, 'A member has more than one PENDING UPGRADE payment; cancel the extras first.', 1;
        EXEC sys.sp_executesql N'
            CREATE UNIQUE INDEX ux_payment_member_pending_upgrade ON dbo.payment(member_id)
            WHERE purpose = ''UPGRADE'' AND status = ''PENDING'';';
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
