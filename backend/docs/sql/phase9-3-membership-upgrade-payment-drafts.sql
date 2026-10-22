-- Phase 9.3 / C2.5A: isolated Membership Upgrade Payment drafts and immutable quote evidence.
-- Back up SportCenterDB and stop application writers before running. Run after the Phase 9.2A
-- Membership purchase and Phase 9.2A.1 renewal migrations. This script is manual; do not run
-- it from application startup. It does not issue checkout URLs or change Membership/Payment rows.
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
    THROW 50961, 'Select SportCenterDB before applying the Upgrade Payment draft migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50962, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL
    THROW 50963, 'Expected payment, membership, and membershipplan tables.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter:Phase9.3:MembershipUpgradePaymentDrafts',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 30000;
    IF @lockResult < 0 THROW 50964, 'Could not acquire Upgrade Payment migration lock.', 1;

    DECLARE @paymentId INT = OBJECT_ID(N'dbo.payment');
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0 AND is_identity=1)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'member_id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'purpose' AND system_type_id=TYPE_ID(N'varchar') AND max_length=30 AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'reference_id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'amount' AND system_type_id=TYPE_ID(N'decimal') AND precision=12 AND scale=2 AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'status' AND system_type_id=TYPE_ID(N'varchar') AND max_length=20 AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'transaction_code' AND system_type_id=TYPE_ID(N'varchar') AND max_length=40 AND is_nullable=0)
        THROW 50965, 'Payment core metadata differs from the reviewed schema; no changes made.', 1;

    IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=@paymentId AND name=N'ck_payment_purpose') <> 1
        THROW 50966, 'Expected exactly ck_payment_purpose; inspect constraints before migration.', 1;
    DECLARE @purposeDefinition NVARCHAR(MAX), @purposeNorm NVARCHAR(MAX);
    SELECT @purposeDefinition=definition FROM sys.check_constraints
      WHERE parent_object_id=@paymentId AND name=N'ck_payment_purpose';
    SET @purposeNorm=LOWER(@purposeDefinition);
    SET @purposeNorm=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@purposeNorm,N'[',N''),N']',N''),N' ',N''),CHAR(9),N''),CHAR(13),N'');
    SET @purposeNorm=REPLACE(@purposeNorm,CHAR(10),N'');
    SET @purposeNorm=REPLACE(@purposeNorm,N'(',N''); SET @purposeNorm=REPLACE(@purposeNorm,N')',N'');
    IF @purposeNorm NOT IN (
        N'purposein''membership'',''course_registration'',''pt_booking''',
        N'purpose=''membership''orpurpose=''course_registration''orpurpose=''pt_booking''',
        N'purpose=''membership''orpurpose=''pt_booking''orpurpose=''course_registration''',
        N'purpose=''course_registration''orpurpose=''membership''orpurpose=''pt_booking''',
        N'purpose=''course_registration''orpurpose=''pt_booking''orpurpose=''membership''',
        N'purpose=''pt_booking''orpurpose=''membership''orpurpose=''course_registration''',
        N'purpose=''pt_booking''orpurpose=''course_registration''orpurpose=''membership''',
        N'purposein''membership'',''course_registration'',''pt_booking'',''upgrade''',
        N'purpose=''membership''orpurpose=''course_registration''orpurpose=''pt_booking''orpurpose=''upgrade''',
        N'purpose=''membership''orpurpose=''course_registration''orpurpose=''upgrade''orpurpose=''pt_booking''',
        N'purpose=''membership''orpurpose=''upgrade''orpurpose=''course_registration''orpurpose=''pt_booking''',
        N'purpose=''upgrade''orpurpose=''membership''orpurpose=''course_registration''orpurpose=''pt_booking'''
    ) OR EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@paymentId AND name=N'ck_payment_purpose' AND (is_disabled=1 OR is_not_trusted=1))
        THROW 50967, 'ck_payment_purpose is not a trusted known legacy or target definition; inspect it manually.', 1;

    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name IN (N'upgrade_source_membership_id',N'upgrade_target_plan_id'))
       OR EXISTS (SELECT 1 FROM sys.tables WHERE object_id=OBJECT_ID(N'dbo.membership_upgrade_payment_evidence'))
       OR EXISTS (SELECT 1 FROM dbo.payment WHERE purpose=N'UPGRADE')
        THROW 50968, 'Partial or pre-existing Upgrade Payment schema/data found; stop for manual review.', 1;

    IF @purposeNorm NOT LIKE N'%upgrade%'
    BEGIN
        ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_purpose;
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_purpose
            CHECK (purpose IN ('MEMBERSHIP','COURSE_REGISTRATION','PT_BOOKING','UPGRADE'));
        ALTER TABLE dbo.payment CHECK CONSTRAINT ck_payment_purpose;
    END;

    CREATE TABLE dbo.membership_upgrade_payment_evidence (
        id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_upgrade_payment_evidence PRIMARY KEY,
        payment_id INT NOT NULL,
        source_membership_id INT NOT NULL,
        target_plan_id INT NOT NULL,
        source_start_date DATE NOT NULL,
        source_end_date DATE NOT NULL,
        source_purchased_months INT NULL,
        original_duration_days INT NOT NULL,
        remaining_days INT NOT NULL,
        source_paid_amount DECIMAL(12,2) NOT NULL,
        target_monthly_price DECIMAL(12,2) NOT NULL,
        remaining_credit DECIMAL(12,2) NOT NULL,
        payable_amount DECIMAL(12,2) NOT NULL,
        issuance_business_date DATE NOT NULL,
        created_at_utc DATETIME2(6) NOT NULL,
        expires_at_utc DATETIME2(6) NOT NULL,
        CONSTRAINT ux_membership_upgrade_evidence_payment UNIQUE (payment_id),
        CONSTRAINT fk_upgrade_evidence_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
        CONSTRAINT fk_upgrade_evidence_source_membership FOREIGN KEY (source_membership_id) REFERENCES dbo.membership(id),
        CONSTRAINT fk_upgrade_evidence_target_plan FOREIGN KEY (target_plan_id) REFERENCES dbo.membership_plan(id),
        CONSTRAINT ck_upgrade_evidence_period CHECK (source_end_date > source_start_date AND original_duration_days > 0
            AND remaining_days >= 0 AND remaining_days <= original_duration_days),
        CONSTRAINT ck_upgrade_evidence_money CHECK (source_paid_amount >= 0 AND target_monthly_price > 0
            AND remaining_credit >= 0 AND payable_amount > 0)
    );

    -- Member-row serialization is primary; this filtered unique index is a database backstop
    -- against concurrent draft issuance and applies only while a draft remains pending.
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@paymentId AND name=N'ux_payment_pending_upgrade_member')
        THROW 50969, 'Index name ux_payment_pending_upgrade_member is already occupied; inspect it manually.', 1;
    CREATE UNIQUE INDEX ux_payment_pending_upgrade_member ON dbo.payment(member_id ASC)
        WHERE purpose='UPGRADE' AND status='PENDING';

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@paymentId AND name=N'ck_payment_purpose'
        AND is_disabled=0 AND is_not_trusted=0)
       OR OBJECT_ID(N'dbo.membership_upgrade_payment_evidence',N'U') IS NULL
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_payment_evidence')
          AND name=N'fk_upgrade_evidence_payment' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@paymentId AND name=N'ux_payment_pending_upgrade_member'
          AND is_unique=1 AND is_disabled=0 AND has_filter=1)
        THROW 50970, 'Upgrade draft migration postcondition failed; transaction will roll back.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
