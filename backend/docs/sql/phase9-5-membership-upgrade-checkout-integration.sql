-- Phase 9.5 / C2.5B.2: isolated UGP checkout attempts and authenticated counter receipts.
-- Manual additive migration. Apply only after phases 9.3 and 9.4, after backup and writer stop.
-- This script is not executed by the application and must not be run against production without rehearsal.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDB' THROW 50981, 'Select SportCenterDB before Phase 9.5.', 1;
IF @@TRANCOUNT <> 0 THROW 50982, 'Run Phase 9.5 without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.membership_upgrade_payment_evidence',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade_capture_evidence',N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment',N'U') IS NULL OR OBJECT_ID(N'dbo.users',N'U') IS NULL
    THROW 50983, 'Phases 9.3 and 9.4 must be applied first.', 1;
IF OBJECT_ID(N'dbo.membership_upgrade_checkout_attempt',N'U') IS NOT NULL
   OR OBJECT_ID(N'dbo.membership_upgrade_counter_receipt',N'U') IS NOT NULL
   OR COL_LENGTH(N'dbo.membership_upgrade_capture_evidence',N'checkout_attempt_id') IS NOT NULL
    THROW 50984, 'Partial or pre-existing Phase 9.5 objects found; inspect before applying.', 1;

BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lockResult INT;
 EXEC @lockResult=sys.sp_getapplock @Resource=N'SportCenter:Phase9.5:UpgradeCheckout',
   @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=30000;
 IF @lockResult<0 THROW 50985, 'Could not acquire Phase 9.5 migration lock.', 1;

 IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.membership_upgrade_payment_evidence')
                 AND name=N'expires_at_utc' AND system_type_id=TYPE_ID(N'datetime2') AND scale=6 AND is_nullable=0)
    OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence')
                 AND name=N'evidence_key' AND system_type_id=TYPE_ID(N'varchar') AND max_length=64 AND is_nullable=0)
    OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.payment')
                 AND name=N'purpose' AND system_type_id=TYPE_ID(N'varchar') AND max_length=30 AND is_nullable=0)
    THROW 50986, 'Upgrade draft, capture, or Payment metadata differs from the reviewed schema.', 1;

 IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence')
       AND name=N'ck_upgrade_capture_outcome')<>1
    OR EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence')
       AND name=N'ck_upgrade_capture_outcome' AND (is_disabled=1 OR is_not_trusted=1))
    OR EXISTS(SELECT 1 FROM dbo.membership_upgrade_capture_evidence
       WHERE outcome NOT IN('RECORDED','APPLIED','DUPLICATE','RECONCILIATION_REQUIRED'))
    THROW 50988, 'Unexpected Upgrade capture outcome constraint or data.', 1;

 ALTER TABLE dbo.membership_upgrade_capture_evidence DROP CONSTRAINT ck_upgrade_capture_outcome;
 ALTER TABLE dbo.membership_upgrade_capture_evidence WITH CHECK ADD CONSTRAINT ck_upgrade_capture_outcome
   CHECK(outcome IN('RECORDED','APPLIED','DUPLICATE','RECONCILIATION_REQUIRED','FAILED'));
 ALTER TABLE dbo.membership_upgrade_capture_evidence CHECK CONSTRAINT ck_upgrade_capture_outcome;

 CREATE TABLE dbo.membership_upgrade_checkout_attempt (
   id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_upgrade_checkout_attempt PRIMARY KEY,
   payment_id INT NOT NULL,
   transaction_reference VARCHAR(40) NOT NULL,
   provider VARCHAR(20) NOT NULL,
   amount DECIMAL(12,2) NOT NULL,
   created_at_utc DATETIME2(6) NOT NULL,
   expires_at_utc DATETIME2(6) NOT NULL,
   status VARCHAR(30) NOT NULL,
   checkout_url VARCHAR(2048) NOT NULL,
   provider_transaction_id VARCHAR(100) NULL,
   CONSTRAINT ux_upgrade_checkout_reference UNIQUE(transaction_reference),
   CONSTRAINT fk_upgrade_checkout_payment FOREIGN KEY(payment_id) REFERENCES dbo.payment(id),
   CONSTRAINT ck_upgrade_checkout_provider CHECK(provider='VNPAY'),
   CONSTRAINT ck_upgrade_checkout_window CHECK(amount>0 AND expires_at_utc>created_at_utc),
   CONSTRAINT ck_upgrade_checkout_status CHECK(status IN('CREATED','URL_ISSUED','FAILED','CAPTURED','RECONCILIATION_REQUIRED'))
 );
 CREATE INDEX ix_upgrade_checkout_payment_created
   ON dbo.membership_upgrade_checkout_attempt(payment_id,created_at_utc,id);
 CREATE UNIQUE INDEX ux_upgrade_checkout_provider_transaction
   ON dbo.membership_upgrade_checkout_attempt(provider_transaction_id)
   WHERE provider_transaction_id IS NOT NULL;
 CREATE UNIQUE INDEX ux_upgrade_checkout_one_issued_attempt
   ON dbo.membership_upgrade_checkout_attempt(payment_id)
   WHERE status='URL_ISSUED';

 ALTER TABLE dbo.membership_upgrade_capture_evidence ADD checkout_attempt_id INT NULL;
 ALTER TABLE dbo.membership_upgrade_capture_evidence WITH CHECK ADD CONSTRAINT fk_upgrade_capture_attempt
   FOREIGN KEY(checkout_attempt_id) REFERENCES dbo.membership_upgrade_checkout_attempt(id);

 CREATE TABLE dbo.membership_upgrade_counter_receipt (
   id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_upgrade_counter_receipt PRIMARY KEY,
   payment_id INT NOT NULL,
   staff_user_id INT NOT NULL,
   amount DECIMAL(12,2) NOT NULL,
   received_at_utc DATETIME2(6) NOT NULL,
   staff_note NVARCHAR(500) NULL,
   outcome VARCHAR(30) NOT NULL,
   reconciliation_reason NVARCHAR(500) NULL,
   CONSTRAINT ux_upgrade_counter_receipt_payment UNIQUE(payment_id),
   CONSTRAINT fk_upgrade_counter_receipt_payment FOREIGN KEY(payment_id) REFERENCES dbo.payment(id),
   CONSTRAINT fk_upgrade_counter_receipt_staff FOREIGN KEY(staff_user_id) REFERENCES dbo.users(id),
   CONSTRAINT ck_upgrade_counter_receipt_amount CHECK(amount>0),
   CONSTRAINT ck_upgrade_counter_receipt_outcome CHECK(outcome IN('RECORDED','APPLIED','RECONCILIATION_REQUIRED'))
 );

 IF OBJECT_ID(N'dbo.membership_upgrade_checkout_attempt',N'U') IS NULL
    OR OBJECT_ID(N'dbo.membership_upgrade_counter_receipt',N'U') IS NULL
    OR NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence')
       AND name=N'fk_upgrade_capture_attempt' AND is_disabled=0 AND is_not_trusted=0)
    OR NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership_upgrade_checkout_attempt')
       AND name=N'ux_upgrade_checkout_provider_transaction' AND is_unique=1 AND is_disabled=0 AND has_filter=1)
    OR NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.membership_upgrade_capture_evidence')
       AND name=N'ck_upgrade_capture_outcome' AND is_disabled=0 AND is_not_trusted=0)
    THROW 50987, 'Phase 9.5 postcondition failed; migration will roll back.', 1;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
