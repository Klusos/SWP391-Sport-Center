/*
  DRAFT ONLY — Phase 2A schema migration for an explicitly verified SQL Server clone.
  NOT APPROVED TO RUN. This is a separate copy; the original SportCenterDemo
  migration and its identity guard remain unchanged.

  Replace BOTH required identity values below using evidence from the restored
  clone. Do not guess. This script hard-rejects database SportCenterDemo.
  It performs DDL only and no data backfill. Use only after separate owner
  approval for clone rehearsal, with application writers stopped.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedCloneServerProperty sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedCloneDatabase sysname = N'SportCenterDemo_Phase2C4_Rehearsal_20261119';
DECLARE @lockResult int;

IF @expectedCloneServerProperty = N'__SET_VERIFIED_CLONE_SERVERPROPERTY_SERVERNAME__'
   OR @expectedCloneDatabase = N'__SET_VERIFIED_CLONE_DATABASE_NAME__'
   OR LEN(LTRIM(RTRIM(@expectedCloneServerProperty))) = 0
   OR LEN(LTRIM(RTRIM(@expectedCloneDatabase))) = 0
    THROW 50711, 'Set both verified clone identity values before rehearsal. No DDL was applied.', 1;

IF UPPER(@expectedCloneDatabase) = N'SPORTCENTERDEMO'
    THROW 50712, 'Refusing to run clone migration against the original SportCenterDemo database.', 1;

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedCloneServerProperty
   OR DB_NAME() <> @expectedCloneDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50713, 'Clone identity mismatch. Migration draft stopped before DDL.', 1;

IF OBJECT_ID(N'dbo.membership',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_plan',N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade',N'U') IS NULL
    THROW 50611, 'Required live tables are missing. Stop and review the Phase 2A preflight.', 1;

-- Require one atomically completed manifest from the clone preflight. Check its
-- identity, per-table counts, total row count and record-ID coverage before DDL.
IF OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest',N'U') IS NULL
   OR OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest_run',N'U') IS NULL
    THROW 50714, 'A complete clone preflight manifest is required before DDL.', 1;

IF (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest_run) <> 1
   OR NOT EXISTS (
       SELECT 1 FROM dbo.phase2a_clone_rehearsal_manifest_run
       WHERE manifest_key=1 AND is_complete=1
         AND server_name=@expectedCloneServerProperty
         AND database_name=@expectedCloneDatabase
   )
    THROW 50715, 'Clone manifest is missing, incomplete, or belongs to another identity.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.phase2a_clone_rehearsal_manifest_run r
    WHERE r.membership_count <> (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Membership')
       OR r.payment_count <> (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Payment')
       OR r.membership_upgrade_count <> (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipUpgrade')
       OR r.cancellation_request_count <> (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipCancellationRequest')
       OR r.manifest_record_count <> (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest)
       OR r.membership_count <> (SELECT COUNT_BIG(*) FROM dbo.membership)
       OR r.payment_count <> (SELECT COUNT_BIG(*) FROM dbo.payment)
       OR r.membership_upgrade_count <> (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade)
       OR r.cancellation_request_count <> (SELECT COUNT_BIG(*) FROM dbo.membership_cancellation_request)
)
    THROW 50716, 'Clone manifest is incomplete or source record counts changed after baseline.', 1;

DECLARE @currentRecordIds TABLE(entity_name varchar(40) NOT NULL,record_id int NOT NULL,
                                PRIMARY KEY(entity_name,record_id));
INSERT @currentRecordIds SELECT 'Membership',id FROM dbo.membership;
INSERT @currentRecordIds SELECT 'Payment',id FROM dbo.payment;
INSERT @currentRecordIds SELECT 'MembershipUpgrade',id FROM dbo.membership_upgrade;
INSERT @currentRecordIds SELECT 'MembershipCancellationRequest',id FROM dbo.membership_cancellation_request;

IF EXISTS (
    SELECT entity_name,record_id FROM @currentRecordIds
    EXCEPT
    SELECT entity_name,record_id FROM dbo.phase2a_clone_rehearsal_manifest
) OR EXISTS (
    SELECT entity_name,record_id FROM dbo.phase2a_clone_rehearsal_manifest
    EXCEPT
    SELECT entity_name,record_id FROM @currentRecordIds
)
    THROW 50717, 'Clone manifest record-ID coverage is incomplete or no longer matches source tables.', 1;

DECLARE @currentBaselineHashes TABLE(entity_name varchar(40) NOT NULL,record_id int NOT NULL,
                                     row_hash binary(32) NOT NULL,PRIMARY KEY(entity_name,record_id));
INSERT @currentBaselineHashes
SELECT 'Membership',m.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership m
CROSS APPLY (SELECT (SELECT m.member_id,m.plan_id,m.auto_renew,m.start_date,m.end_date,m.status,
                            m.purchase_payment_id,m.cancelled_at,m.cancel_reason
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;
INSERT @currentBaselineHashes
SELECT 'Payment',p.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.payment p
CROSS APPLY (SELECT (SELECT p.member_id,p.amount,p.method,p.purpose,p.reference_id,p.auto_renew,p.status,
                            p.transaction_code,p.processed_by,p.note,p.created_at,p.paid_at,p.refunded_at,p.refunded_by
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;
INSERT @currentBaselineHashes
SELECT 'MembershipUpgrade',u.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership_upgrade u
CROSS APPLY (SELECT (SELECT u.payment_id,u.source_membership_id,u.target_plan_id,u.remaining_days,
                            u.expires_at,u.created_at,u.result_membership_id
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;
INSERT @currentBaselineHashes
SELECT 'MembershipCancellationRequest',r.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership_cancellation_request r
CROSS APPLY (SELECT (SELECT r.membership_id,r.member_id,r.reason,r.status,r.requested_at,
                            r.decided_by,r.decided_at,r.decision_note
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

IF EXISTS (
    SELECT 1
    FROM dbo.phase2a_clone_rehearsal_manifest b
    FULL OUTER JOIN @currentBaselineHashes c ON c.entity_name=b.entity_name AND c.record_id=b.record_id
    WHERE b.record_id IS NULL OR c.record_id IS NULL OR b.row_hash<>c.row_hash
)
    THROW 50718, 'Clone baseline row hashes no longer match source data. Stop before DDL and refresh/review the baseline.', 1;

IF OBJECT_ID(N'dbo.membership_renewal_intent',N'U') IS NOT NULL
   OR COL_LENGTH(N'dbo.membership','benefit_snapshot_status') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment','renewal_intent_id') IS NOT NULL
   OR COL_LENGTH(N'dbo.membership_upgrade','source_paid_value_basis_amount') IS NOT NULL
    THROW 50612, 'A Phase 2A object/column already exists. Review partial deployment; do not rerun blindly.', 1;

-- Existing data must pass these guards before the new unique constraints can be added.
IF EXISTS (
    SELECT source_membership_id
    FROM dbo.membership_upgrade
    WHERE result_membership_id IS NOT NULL
    GROUP BY source_membership_id
    HAVING COUNT_BIG(*) > 1
)
    THROW 50613, 'More than one successful Upgrade result exists for a source Membership. Review rows before migration.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.payment p
    WHERE p.purpose='UPGRADE' AND p.status='SUCCESS'
      AND NOT EXISTS (SELECT 1 FROM dbo.membership_upgrade u WHERE u.payment_id=p.id)
)
    THROW 50615, 'A SUCCESS Upgrade Payment has no Upgrade attempt. Review rows before migration.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.membership_upgrade u
    JOIN dbo.payment p ON p.id=u.payment_id
    WHERE p.purpose='UPGRADE' AND p.status='SUCCESS' AND u.result_membership_id IS NULL
)
    THROW 50616, 'A SUCCESS Upgrade Payment has an attempt but no result Membership. Reconcile before adding success constraints.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter-Membership-Phase2A-Schema',
        @LockMode = N'Exclusive',
        @LockOwner = N'Transaction',
        @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50614, 'Could not acquire the Phase 2A migration application lock.', 1;

    CREATE TABLE dbo.membership_renewal_intent (
        id int IDENTITY(1,1) NOT NULL,
        source_membership_id int NOT NULL,
        target_plan_id int NOT NULL,
        creation_source varchar(24) NOT NULL,
        auto_renew_opt_in_snapshot bit NOT NULL,
        intent_status varchar(20) NOT NULL,
        created_at datetime2(6) NOT NULL,
        valid_from datetime2(6) NOT NULL,
        valid_until datetime2(6) NOT NULL,
        quote_captured_at datetime2(6) NOT NULL,
        source_end_date_snapshot date NOT NULL,
        successor_start_date date NOT NULL,
        successor_end_date date NOT NULL,
        plan_name_snapshot nvarchar(100) NOT NULL,
        plan_description_snapshot nvarchar(max) NULL,
        plan_price_snapshot decimal(12,2) NOT NULL,
        quoted_amount decimal(12,2) NOT NULL,
        duration_months_snapshot int NOT NULL,
        course_discount_percent_snapshot decimal(5,2) NOT NULL,
        pt_discount_percent_snapshot decimal(5,2) NOT NULL,
        allow_pt_plan_snapshot bit NOT NULL,
        allow_one_on_one_snapshot bit NOT NULL,
        settled_payment_id int NULL,
        settled_at datetime2(6) NULL,
        closed_at datetime2(6) NULL,
        closed_reason varchar(32) NULL,
        CONSTRAINT pk_membership_renewal_intent PRIMARY KEY (id),
        CONSTRAINT uq_membership_renewal_intent_source UNIQUE (source_membership_id),
        CONSTRAINT fk_renewal_intent_source_membership FOREIGN KEY (source_membership_id)
            REFERENCES dbo.membership(id),
        CONSTRAINT fk_renewal_intent_target_plan FOREIGN KEY (target_plan_id)
            REFERENCES dbo.membership_plan(id),
        CONSTRAINT ck_renewal_intent_status CHECK (intent_status IN ('OPEN','SETTLED','EXPIRED','CANCELLED')),
        CONSTRAINT ck_renewal_intent_creation_source CHECK (LEN(LTRIM(RTRIM(creation_source))) > 0),
        CONSTRAINT ck_renewal_intent_opt_in_value CHECK (auto_renew_opt_in_snapshot IN (0,1)),
        CONSTRAINT ck_renewal_intent_scheduler_opt_in CHECK (
            creation_source <> 'AUTO_RENEW_SCHEDULER' OR auto_renew_opt_in_snapshot = 1),
        CONSTRAINT ck_renewal_intent_time_window CHECK (valid_until > valid_from),
        CONSTRAINT ck_renewal_intent_successor_period CHECK (
            successor_start_date = source_end_date_snapshot AND successor_end_date > successor_start_date),
        CONSTRAINT ck_renewal_intent_duration CHECK (duration_months_snapshot > 0),
        CONSTRAINT ck_renewal_intent_amounts CHECK (plan_price_snapshot >= 0 AND quoted_amount >= 0),
        CONSTRAINT ck_renewal_intent_discounts CHECK (
            course_discount_percent_snapshot BETWEEN 0 AND 100
            AND pt_discount_percent_snapshot BETWEEN 0 AND 100),
        CONSTRAINT ck_renewal_intent_settlement_state CHECK (
            (intent_status = 'OPEN' AND settled_payment_id IS NULL AND settled_at IS NULL
                 AND closed_at IS NULL AND closed_reason IS NULL)
            OR (intent_status = 'SETTLED' AND settled_payment_id IS NOT NULL AND settled_at IS NOT NULL)
            OR (intent_status IN ('EXPIRED','CANCELLED') AND settled_payment_id IS NULL AND settled_at IS NULL
                 AND closed_at IS NOT NULL AND closed_reason IS NOT NULL)),
        CONSTRAINT uq_renewal_intent_id_settled_payment UNIQUE (id, settled_payment_id)
    );

    ALTER TABLE dbo.payment ADD
        membership_plan_name_snapshot nvarchar(100) NULL,
        membership_plan_description_snapshot nvarchar(max) NULL,
        membership_plan_price_snapshot decimal(12,2) NULL,
        membership_duration_months_snapshot int NULL,
        membership_course_discount_percent_snapshot decimal(5,2) NULL,
        membership_pt_discount_percent_snapshot decimal(5,2) NULL,
        membership_allow_pt_plan_snapshot bit NULL,
        membership_allow_one_on_one_snapshot bit NULL,
        membership_quote_captured_at datetime2(6) NULL,
        renewal_intent_id int NULL,
        attempt_valid_until datetime2(6) NULL,
        renewal_attempt_close_reason varchar(32) NULL,
        renewal_settlement_accepted_at datetime2(6) NULL;

    ALTER TABLE dbo.membership ADD
        benefit_snapshot_status varchar(16) NULL,
        benefit_snapshot_source varchar(24) NULL,
        plan_name_snapshot nvarchar(100) NULL,
        plan_description_snapshot nvarchar(max) NULL,
        plan_price_snapshot decimal(12,2) NULL,
        plan_duration_months_snapshot int NULL,
        course_discount_percent_snapshot decimal(5,2) NULL,
        pt_discount_percent_snapshot decimal(5,2) NULL,
        allow_pt_plan_snapshot bit NULL,
        allow_one_on_one_snapshot bit NULL,
        paid_value_basis_status varchar(20) NULL,
        paid_value_basis_amount decimal(12,2) NULL,
        paid_value_basis_source varchar(24) NULL,
        renewal_intent_id int NULL;

    ALTER TABLE dbo.membership_upgrade ADD
        source_period_start_date date NULL,
        source_period_end_date date NULL,
        credit_valuation_date date NULL,
        source_total_days int NULL,
        source_unused_days int NULL,
        source_paid_value_basis_amount decimal(12,2) NULL,
        target_plan_name_snapshot nvarchar(100) NULL,
        target_plan_description_snapshot nvarchar(max) NULL,
        target_plan_price_snapshot decimal(12,2) NULL,
        target_duration_months_snapshot int NULL,
        target_course_discount_percent_snapshot decimal(5,2) NULL,
        target_pt_discount_percent_snapshot decimal(5,2) NULL,
        target_allow_pt_plan_snapshot bit NULL,
        target_allow_one_on_one_snapshot bit NULL,
        applied_credit_amount decimal(12,2) NULL;

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT fk_payment_renewal_intent
        FOREIGN KEY (renewal_intent_id) REFERENCES dbo.membership_renewal_intent(id);';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT uq_payment_id_renewal_intent UNIQUE (id, renewal_intent_id);';

    ALTER TABLE dbo.membership_renewal_intent ADD CONSTRAINT fk_renewal_intent_settled_payment
        FOREIGN KEY (settled_payment_id, id)
        REFERENCES dbo.payment(id, renewal_intent_id);

    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD CONSTRAINT fk_membership_renewal_intent_settled_payment
        FOREIGN KEY (renewal_intent_id, purchase_payment_id)
        REFERENCES dbo.membership_renewal_intent(id, settled_payment_id);';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_renewal_attempt_fields CHECK (
        (renewal_intent_id IS NULL AND attempt_valid_until IS NULL
             AND renewal_attempt_close_reason IS NULL AND renewal_settlement_accepted_at IS NULL)
        OR (renewal_intent_id IS NOT NULL AND purpose = ''MEMBERSHIP'' AND attempt_valid_until IS NOT NULL
             AND attempt_valid_until > created_at
             AND (renewal_attempt_close_reason IS NULL OR renewal_attempt_close_reason IN
                  (''PROVIDER_FAILED'',''ATTEMPT_EXPIRED'',''MEMBER_CANCELLED'',''INTENT_EXPIRED'',
                   ''LATE_PROVIDER_PAYMENT'',''DUPLICATE_SETTLEMENT''))
             AND (renewal_settlement_accepted_at IS NULL OR
                  (paid_at IS NOT NULL AND status IN (''SUCCESS'',''REFUND_REQUIRED'',''REFUNDED''))))
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_membership_quote_snapshot CHECK (
        (membership_quote_captured_at IS NULL
             AND membership_plan_name_snapshot IS NULL
             AND membership_plan_description_snapshot IS NULL
             AND membership_plan_price_snapshot IS NULL
             AND membership_duration_months_snapshot IS NULL
             AND membership_course_discount_percent_snapshot IS NULL
             AND membership_pt_discount_percent_snapshot IS NULL
             AND membership_allow_pt_plan_snapshot IS NULL
             AND membership_allow_one_on_one_snapshot IS NULL)
        OR (purpose = ''MEMBERSHIP'' AND renewal_intent_id IS NULL
             AND membership_quote_captured_at IS NOT NULL
             AND membership_plan_name_snapshot IS NOT NULL
             AND membership_plan_price_snapshot IS NOT NULL
             AND membership_plan_price_snapshot >= 0
             AND membership_duration_months_snapshot IS NOT NULL
             AND membership_duration_months_snapshot > 0
             AND membership_course_discount_percent_snapshot IS NOT NULL
             AND membership_course_discount_percent_snapshot BETWEEN 0 AND 100
             AND membership_pt_discount_percent_snapshot IS NOT NULL
             AND membership_pt_discount_percent_snapshot BETWEEN 0 AND 100
             AND membership_allow_pt_plan_snapshot IS NOT NULL
             AND membership_allow_one_on_one_snapshot IS NOT NULL)
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD CONSTRAINT ck_membership_benefit_snapshot CHECK (
        (benefit_snapshot_status IS NULL AND benefit_snapshot_source IS NULL
             AND plan_name_snapshot IS NULL AND plan_description_snapshot IS NULL AND plan_price_snapshot IS NULL
             AND plan_duration_months_snapshot IS NULL
             AND course_discount_percent_snapshot IS NULL AND pt_discount_percent_snapshot IS NULL
             AND allow_pt_plan_snapshot IS NULL AND allow_one_on_one_snapshot IS NULL)
        OR (benefit_snapshot_status IS NOT NULL AND benefit_snapshot_status = ''UNKNOWN''
             AND benefit_snapshot_source IS NOT NULL
             AND benefit_snapshot_source = ''LEGACY_UNKNOWN''
             AND plan_name_snapshot IS NULL AND plan_description_snapshot IS NULL AND plan_price_snapshot IS NULL
             AND plan_duration_months_snapshot IS NULL
             AND course_discount_percent_snapshot IS NULL AND pt_discount_percent_snapshot IS NULL
             AND allow_pt_plan_snapshot IS NULL AND allow_one_on_one_snapshot IS NULL)
        OR (benefit_snapshot_status IS NOT NULL AND benefit_snapshot_status = ''COMPLETE''
             AND benefit_snapshot_source IS NOT NULL
             AND benefit_snapshot_source IN (''PURCHASE'',''RENEWAL'',''UPGRADE'',''ADMIN_ASSIGNMENT'',''LEGACY_VERIFIED'')
             AND plan_name_snapshot IS NOT NULL AND plan_price_snapshot IS NOT NULL AND plan_price_snapshot >= 0
             AND plan_duration_months_snapshot IS NOT NULL
             AND plan_duration_months_snapshot > 0
             AND course_discount_percent_snapshot IS NOT NULL
             AND course_discount_percent_snapshot BETWEEN 0 AND 100
             AND pt_discount_percent_snapshot IS NOT NULL
             AND pt_discount_percent_snapshot BETWEEN 0 AND 100
             AND allow_pt_plan_snapshot IS NOT NULL AND allow_one_on_one_snapshot IS NOT NULL)
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD CONSTRAINT ck_membership_paid_value_basis CHECK (
        (paid_value_basis_status IS NULL AND paid_value_basis_amount IS NULL AND paid_value_basis_source IS NULL)
        OR (paid_value_basis_status IS NOT NULL AND paid_value_basis_status = ''VERIFIED''
             AND paid_value_basis_amount IS NOT NULL AND paid_value_basis_amount > 0
             AND paid_value_basis_source IS NOT NULL
             AND paid_value_basis_source IN (''PURCHASE_PAYMENT'',''UPGRADE_CHAIN'',''RENEWAL_PAYMENT''))
        OR (paid_value_basis_status IS NOT NULL AND paid_value_basis_status = ''UNKNOWN''
             AND paid_value_basis_amount IS NULL
             AND paid_value_basis_source IS NOT NULL
             AND paid_value_basis_source = ''LEGACY_UNKNOWN'')
        OR (paid_value_basis_status IS NOT NULL AND paid_value_basis_status = ''NO_PAID_VALUE''
             AND paid_value_basis_amount IS NULL
             AND paid_value_basis_source IS NOT NULL
             AND paid_value_basis_source = ''ADMIN_ASSIGNMENT'')
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD CONSTRAINT ck_membership_renewal_purchase_payment CHECK (
        renewal_intent_id IS NULL OR purchase_payment_id IS NOT NULL
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership_upgrade ADD CONSTRAINT ck_membership_upgrade_frozen_quote CHECK (
        (source_period_start_date IS NULL AND source_period_end_date IS NULL AND credit_valuation_date IS NULL
             AND source_total_days IS NULL AND source_unused_days IS NULL AND source_paid_value_basis_amount IS NULL
             AND target_plan_name_snapshot IS NULL AND target_plan_description_snapshot IS NULL AND target_plan_price_snapshot IS NULL
             AND target_duration_months_snapshot IS NULL AND target_course_discount_percent_snapshot IS NULL
             AND target_pt_discount_percent_snapshot IS NULL AND target_allow_pt_plan_snapshot IS NULL
             AND target_allow_one_on_one_snapshot IS NULL AND applied_credit_amount IS NULL)
        OR (source_period_start_date IS NOT NULL AND source_period_end_date IS NOT NULL AND source_period_end_date > source_period_start_date
             AND credit_valuation_date IS NOT NULL AND source_total_days IS NOT NULL AND source_total_days > 0
             AND source_unused_days IS NOT NULL
             AND source_unused_days BETWEEN 0 AND source_total_days
             AND source_paid_value_basis_amount IS NOT NULL AND source_paid_value_basis_amount > 0
             AND target_plan_name_snapshot IS NOT NULL
             AND target_plan_price_snapshot IS NOT NULL AND target_plan_price_snapshot >= 0
             AND target_duration_months_snapshot IS NOT NULL AND target_duration_months_snapshot > 0
             AND target_course_discount_percent_snapshot IS NOT NULL
             AND target_course_discount_percent_snapshot BETWEEN 0 AND 100
             AND target_pt_discount_percent_snapshot IS NOT NULL
             AND target_pt_discount_percent_snapshot BETWEEN 0 AND 100
             AND target_allow_pt_plan_snapshot IS NOT NULL AND target_allow_one_on_one_snapshot IS NOT NULL
             AND applied_credit_amount IS NOT NULL AND applied_credit_amount >= 0
             AND applied_credit_amount <= source_paid_value_basis_amount
             AND applied_credit_amount <= target_plan_price_snapshot)
    );';

    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_payment_one_accepted_renewal_attempt
        ON dbo.payment(renewal_intent_id)
        WHERE renewal_intent_id IS NOT NULL AND renewal_settlement_accepted_at IS NOT NULL;';

    EXEC sys.sp_executesql N'CREATE INDEX ix_payment_renewal_intent_status
        ON dbo.payment(renewal_intent_id,status,created_at)
        WHERE renewal_intent_id IS NOT NULL;';

    EXEC sys.sp_executesql N'CREATE INDEX ix_renewal_intent_due
        ON dbo.membership_renewal_intent(intent_status,valid_from,valid_until);';

    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_membership_one_renewal_successor
        ON dbo.membership(renewal_intent_id)
        WHERE renewal_intent_id IS NOT NULL;';

    EXEC sys.sp_executesql N'CREATE INDEX ix_membership_member_status_dates
        ON dbo.membership(member_id,status,start_date,end_date);';

    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_membership_upgrade_one_success_per_source
        ON dbo.membership_upgrade(source_membership_id)
        WHERE result_membership_id IS NOT NULL;';

    COMMIT TRANSACTION;
    PRINT 'DRAFT MIGRATION COMPLETE. This message is part of a proposed script only; it does not imply approval or execution.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

