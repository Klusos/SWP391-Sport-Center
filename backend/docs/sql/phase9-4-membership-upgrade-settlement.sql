-- Phase 9.4 / C2.5B.1: durable Upgrade replacement and provider-capture evidence.
-- Manual additive migration. Apply after phase9-3-membership-upgrade-payment-drafts.sql,
-- during a writer stop and only after a verified backup. Do not execute from application startup.
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
    THROW 50971, 'Select SportCenterDB before applying the Upgrade settlement migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50972, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.membership',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade_payment_evidence',N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment',N'U') IS NULL
    THROW 50973, 'Apply the Phase 9.3 Upgrade draft migration first; no changes were made.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock @Resource=N'SportCenter:Phase9.4:UpgradeSettlement',
        @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=30000;
    IF @lockResult < 0 THROW 50974, 'Could not acquire Upgrade settlement migration lock.', 1;

    DECLARE @membershipId INT=OBJECT_ID(N'dbo.membership'),
            @upgradeEvidenceId INT=OBJECT_ID(N'dbo.membership_upgrade_payment_evidence');
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name=N'id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0 AND is_identity=1)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name=N'member_id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name=N'status' AND system_type_id=TYPE_ID(N'varchar') AND max_length=30 AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name=N'start_date' AND system_type_id=TYPE_ID(N'date') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name=N'end_date' AND system_type_id=TYPE_ID(N'date') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@upgradeEvidenceId AND name=N'payment_id' AND system_type_id=TYPE_ID(N'int') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@upgradeEvidenceId AND name=N'expires_at_utc' AND system_type_id=TYPE_ID(N'datetime2') AND scale=6 AND is_nullable=0)
        THROW 50975, 'Membership or Upgrade evidence core metadata differs from the reviewed schema.', 1;

    -- The repository migrations define no status CHECK for Membership. Fail closed if
    -- the target database has an owner-added status constraint that might reject REPLACED.
    IF EXISTS (SELECT 1 FROM sys.check_constraints cc WHERE cc.parent_object_id=@membershipId
               AND LOWER(cc.definition) LIKE N'%status%')
        THROW 50978, 'An existing Membership status CHECK was found; review its allowed values before adding REPLACED.', 1;

    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@membershipId AND name IN (N'replaced_on',N'replacement_membership_id'))
       OR EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@upgradeEvidenceId AND name IN (N'applied_membership_id',N'applied_at_utc'))
       OR OBJECT_ID(N'dbo.membership_upgrade_capture_evidence',N'U') IS NOT NULL
       OR EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@membershipId AND name=N'ck_membership_replacement_metadata')
       OR EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@membershipId AND name=N'ux_membership_replaced_by')
       OR EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@upgradeEvidenceId AND name=N'ux_upgrade_evidence_applied_membership')
        THROW 50976, 'Partial or pre-existing Phase 9.4 objects found; inspect metadata before applying.', 1;

    -- Preserve original start/end period snapshots. REPLACED is an explicit entitlement
    -- state; replaced_on records the effective cutoff, and replacement_membership_id links history.
    ALTER TABLE dbo.membership ADD replaced_on DATE NULL, replacement_membership_id INT NULL;
    ALTER TABLE dbo.membership WITH CHECK ADD CONSTRAINT fk_membership_replacement
        FOREIGN KEY (replacement_membership_id) REFERENCES dbo.membership(id);
    ALTER TABLE dbo.membership WITH CHECK ADD CONSTRAINT ck_membership_replacement_metadata
        CHECK ((status='REPLACED' AND replaced_on IS NOT NULL AND replacement_membership_id IS NOT NULL)
            OR (status<>'REPLACED' AND replaced_on IS NULL AND replacement_membership_id IS NULL));
    CREATE UNIQUE INDEX ux_membership_replaced_by ON dbo.membership(replacement_membership_id)
        WHERE replacement_membership_id IS NOT NULL;

    -- Nullable winning link is set in the same transaction that changes Payment to SUCCESS.
    ALTER TABLE dbo.membership_upgrade_payment_evidence
        ADD applied_membership_id INT NULL, applied_at_utc DATETIME2(6) NULL;
    ALTER TABLE dbo.membership_upgrade_payment_evidence WITH CHECK ADD CONSTRAINT fk_upgrade_evidence_applied_membership
        FOREIGN KEY (applied_membership_id) REFERENCES dbo.membership(id);
    ALTER TABLE dbo.membership_upgrade_payment_evidence WITH CHECK ADD CONSTRAINT ck_upgrade_evidence_applied_pair
        CHECK ((applied_membership_id IS NULL AND applied_at_utc IS NULL)
            OR (applied_membership_id IS NOT NULL AND applied_at_utc IS NOT NULL));
    CREATE UNIQUE INDEX ux_upgrade_evidence_applied_membership
        ON dbo.membership_upgrade_payment_evidence(applied_membership_id)
        WHERE applied_membership_id IS NOT NULL;

    -- One append-only row per distinct verified callback evidence tuple. No signature or raw
    -- callback parameters are stored. A missing provider capture time is durably represented as NULL.
    CREATE TABLE dbo.membership_upgrade_capture_evidence (
        id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_upgrade_capture_evidence PRIMARY KEY,
        upgrade_evidence_id INT NOT NULL,
        evidence_key VARCHAR(64) NOT NULL,
        transaction_code VARCHAR(40) NOT NULL,
        provider_transaction_id VARCHAR(100) NOT NULL,
        provider_response_code VARCHAR(10) NOT NULL,
        reported_amount DECIMAL(12,2) NOT NULL,
        captured_at_utc DATETIME2(6) NULL,
        received_at_utc DATETIME2(6) NOT NULL,
        outcome VARCHAR(30) NOT NULL,
        reconciliation_reason VARCHAR(500) NULL,
        CONSTRAINT ux_upgrade_capture_evidence_key UNIQUE (evidence_key),
        CONSTRAINT fk_upgrade_capture_parent FOREIGN KEY (upgrade_evidence_id)
            REFERENCES dbo.membership_upgrade_payment_evidence(id),
        CONSTRAINT ck_upgrade_capture_outcome CHECK (outcome IN ('RECORDED','APPLIED','DUPLICATE','RECONCILIATION_REQUIRED'))
    );
    CREATE INDEX ix_upgrade_capture_parent ON dbo.membership_upgrade_capture_evidence(upgrade_evidence_id,id);

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=@membershipId AND name=N'fk_membership_replacement' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@membershipId AND name=N'ck_membership_replacement_metadata' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@membershipId AND name=N'ux_membership_replaced_by' AND is_unique=1 AND has_filter=1 AND is_disabled=0)
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=@upgradeEvidenceId AND name=N'fk_upgrade_evidence_applied_membership' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@upgradeEvidenceId AND name=N'ck_upgrade_evidence_applied_pair' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@upgradeEvidenceId AND name=N'ux_upgrade_evidence_applied_membership' AND is_unique=1 AND has_filter=1 AND is_disabled=0)
       OR OBJECT_ID(N'dbo.membership_upgrade_capture_evidence',N'U') IS NULL
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence') AND name=N'fk_upgrade_capture_parent' AND is_disabled=0 AND is_not_trusted=0)
        THROW 50977, 'Phase 9.4 postcondition failed; transaction will roll back.', 1;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
