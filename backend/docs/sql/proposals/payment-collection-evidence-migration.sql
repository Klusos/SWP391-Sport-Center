/*
  PROPOSAL ONLY — additive payment collection evidence schema.
  Do not execute without separate owner approval and an isolated rehearsal.
  No USE statement; fail-closed identity placeholders must be set for the approved target.
  No historical evidence backfill and no business-row updates are performed.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @expectedServer sysname = N'__SET_APPROVED_SERVER__';
DECLARE @expectedDatabase sysname = N'__SET_APPROVED_DATABASE__';

IF @expectedServer = N'__SET_APPROVED_SERVER__' OR @expectedDatabase = N'__SET_APPROVED_DATABASE__'
    THROW 50782, 'Set an explicitly approved server and database identity before migration.', 1;
IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50783, 'Payment evidence migration identity mismatch.', 1;

IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_renewal_intent', N'U') IS NULL
   OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50784, 'Required Phase 2A/payment/user tables are missing.', 1;

IF COL_LENGTH(N'dbo.payment', N'renewal_intent_id') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_settlement_accepted_at') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'attempt_valid_until') IS NULL
   OR COL_LENGTH(N'dbo.membership_renewal_intent', N'settled_payment_id') IS NULL
    THROW 50785, 'Required Phase 2A Renewal columns are missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'uq_payment_id_renewal_intent'
               AND object_id=OBJECT_ID(N'dbo.payment') AND is_unique=1)
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_one_accepted_renewal_attempt'
               AND object_id=OBJECT_ID(N'dbo.payment') AND is_unique=1 AND has_filter=1)
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_membership_one_renewal_successor'
               AND object_id=OBJECT_ID(N'dbo.membership') AND is_unique=1 AND has_filter=1)
    THROW 50790, 'Required Phase 2A composite key or Renewal uniqueness indexes are missing.', 1;

-- A partial prior deployment is not silently repaired by this proposal.
IF (OBJECT_ID(N'dbo.payment_collection_evidence', N'U') IS NULL
    AND (OBJECT_ID(N'dbo.payment_collection_decision', N'U') IS NOT NULL
         OR COL_LENGTH(N'dbo.payment', N'accepted_collection_evidence_id') IS NOT NULL))
   OR (OBJECT_ID(N'dbo.payment_collection_evidence', N'U') IS NOT NULL
       AND (OBJECT_ID(N'dbo.payment_collection_decision', N'U') IS NULL
            OR COL_LENGTH(N'dbo.payment', N'accepted_collection_evidence_id') IS NULL))
    THROW 50786, 'Partial payment evidence schema detected; stop for manual review.', 1;

IF OBJECT_ID(N'dbo.payment_collection_evidence', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'dbo.payment_collection_decision', N'U') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'accepted_collection_evidence_id') IS NULL
       OR COL_LENGTH(N'dbo.payment_collection_evidence', N'payment_id') IS NULL
       OR COL_LENGTH(N'dbo.payment_collection_evidence', N'canonical_evidence_digest') IS NULL
       OR COL_LENGTH(N'dbo.payment_collection_decision', N'payment_id') IS NULL
       OR COL_LENGTH(N'dbo.payment_collection_decision', N'event_code') IS NULL
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name=N'pk_payment_collection_evidence' AND type='PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name=N'pk_payment_collection_decision' AND type='PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name=N'uq_payment_collection_evidence_payment_id' AND type='UQ')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_evidence_payment')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_evidence_actor')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_accepted_collection_evidence')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_decision_payment')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_decision_evidence')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_decision_related_evidence')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_payment_collection_decision_actor')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_evidence_source')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_evidence_facts')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_decision_code')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_decision_evidence_required')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_decision_refund_evidence')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name=N'ck_payment_collection_decision_digest')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_collection_evidence_provider_reference')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_collection_evidence_payment_digest')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_collection_evidence_counter_reference')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ix_payment_collection_evidence_payment_created')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_accepted_collection_evidence')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ux_payment_collection_decision_digest')
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'ix_payment_collection_decision_payment_time')
        THROW 50787, 'Evidence objects exist but are incomplete; stop for manual review.', 1;
    PRINT 'Proposed payment collection evidence schema is already present; no DDL executed.';
    RETURN;
END;

BEGIN TRY
    BEGIN TRANSACTION;

    CREATE TABLE dbo.payment_collection_evidence (
        id bigint IDENTITY(1,1) NOT NULL,
        payment_id int NOT NULL,
        source_code varchar(12) NOT NULL,
        merchant_scope nvarchar(64) NOT NULL,
        provider_transaction_reference nvarchar(100) NULL,
        counter_collection_reference nvarchar(100) NULL,
        merchant_transaction_reference nvarchar(40) NOT NULL,
        collection_amount decimal(12,2) NOT NULL,
        currency_code char(3) NOT NULL,
        verified_collection_at datetimeoffset(6) NOT NULL,
        verified_at datetimeoffset(6) NOT NULL,
        verification_provenance varchar(32) NOT NULL,
        verification_actor_user_id int NULL,
        verification_result varchar(24) NOT NULL,
        digest_algorithm varchar(12) NOT NULL,
        canonical_evidence_digest binary(32) NOT NULL,
        created_at datetimeoffset(6) NOT NULL
            CONSTRAINT df_payment_collection_evidence_created_at
            DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
        CONSTRAINT pk_payment_collection_evidence PRIMARY KEY (id),
        CONSTRAINT uq_payment_collection_evidence_payment_id UNIQUE (payment_id,id),
        CONSTRAINT fk_payment_collection_evidence_payment FOREIGN KEY (payment_id)
            REFERENCES dbo.payment(id),
        CONSTRAINT fk_payment_collection_evidence_actor FOREIGN KEY (verification_actor_user_id)
            REFERENCES dbo.users(id),
        CONSTRAINT ck_payment_collection_evidence_source CHECK (
            (source_code='VNPAY' AND verification_provenance='VNPAY_SIGNED_IPN'
                AND provider_transaction_reference IS NOT NULL
                AND counter_collection_reference IS NULL
                AND verification_actor_user_id IS NULL)
            OR
            (source_code='COUNTER' AND verification_provenance='AUTHENTICATED_COUNTER'
                AND provider_transaction_reference IS NULL
                AND counter_collection_reference IS NOT NULL
                AND verification_actor_user_id IS NOT NULL)),
        CONSTRAINT ck_payment_collection_evidence_facts CHECK (
            LEN(LTRIM(RTRIM(merchant_scope))) > 0
            AND LEN(LTRIM(RTRIM(merchant_transaction_reference))) > 0
            AND collection_amount > 0
            AND currency_code='VND'
            AND verification_result='VERIFIED_COLLECTED'
            AND digest_algorithm='SHA-256')
    );

    CREATE UNIQUE INDEX ux_payment_collection_evidence_provider_reference
        ON dbo.payment_collection_evidence(source_code,merchant_scope,provider_transaction_reference)
        WHERE source_code='VNPAY' AND provider_transaction_reference IS NOT NULL;

    CREATE UNIQUE INDEX ux_payment_collection_evidence_payment_digest
        ON dbo.payment_collection_evidence(payment_id,canonical_evidence_digest);

    CREATE UNIQUE INDEX ux_payment_collection_evidence_counter_reference
        ON dbo.payment_collection_evidence(merchant_scope,counter_collection_reference)
        WHERE source_code='COUNTER' AND counter_collection_reference IS NOT NULL;

    CREATE INDEX ix_payment_collection_evidence_payment_created
        ON dbo.payment_collection_evidence(payment_id,created_at,id);

    ALTER TABLE dbo.payment ADD accepted_collection_evidence_id bigint NULL;

    ALTER TABLE dbo.payment ADD CONSTRAINT fk_payment_accepted_collection_evidence
        FOREIGN KEY (id,accepted_collection_evidence_id)
        REFERENCES dbo.payment_collection_evidence(payment_id,id);

    CREATE UNIQUE INDEX ux_payment_accepted_collection_evidence
        ON dbo.payment(accepted_collection_evidence_id)
        WHERE accepted_collection_evidence_id IS NOT NULL;

    CREATE TABLE dbo.payment_collection_decision (
        id bigint IDENTITY(1,1) NOT NULL,
        payment_id int NOT NULL,
        evidence_id bigint NULL,
        related_evidence_id bigint NULL,
        event_code varchar(32) NOT NULL,
        event_at datetimeoffset(6) NOT NULL,
        actor_user_id int NULL,
        reason_code varchar(64) NULL,
        external_reference nvarchar(100) NULL,
        event_digest binary(32) NULL,
        created_at datetimeoffset(6) NOT NULL
            CONSTRAINT df_payment_collection_decision_created_at
            DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
        CONSTRAINT pk_payment_collection_decision PRIMARY KEY (id),
        CONSTRAINT fk_payment_collection_decision_payment FOREIGN KEY (payment_id)
            REFERENCES dbo.payment(id),
        CONSTRAINT fk_payment_collection_decision_evidence FOREIGN KEY (payment_id,evidence_id)
            REFERENCES dbo.payment_collection_evidence(payment_id,id),
        CONSTRAINT fk_payment_collection_decision_related_evidence FOREIGN KEY (related_evidence_id)
            REFERENCES dbo.payment_collection_evidence(id),
        CONSTRAINT fk_payment_collection_decision_actor FOREIGN KEY (actor_user_id)
            REFERENCES dbo.users(id),
        CONSTRAINT ck_payment_collection_decision_code CHECK (event_code IN (
            'VERIFIED_NON_COLLECTION','SETTLEMENT_ACCEPTED','RECONCILIATION_REQUIRED',
            'RECONCILIATION_RESOLVED','DUPLICATE_DELIVERY','REFERENCE_CONFLICT',
            'REFUND_COMPLETION_RECORDED')),
        CONSTRAINT ck_payment_collection_decision_evidence_required CHECK (
            event_code NOT IN ('SETTLEMENT_ACCEPTED','RECONCILIATION_REQUIRED',
                               'RECONCILIATION_RESOLVED','REFUND_COMPLETION_RECORDED')
            OR evidence_id IS NOT NULL),
        CONSTRAINT ck_payment_collection_decision_refund_evidence CHECK (
            event_code <> 'REFUND_COMPLETION_RECORDED'
            OR (external_reference IS NOT NULL AND LEN(LTRIM(RTRIM(external_reference))) > 0
                AND event_digest IS NOT NULL)),
        CONSTRAINT ck_payment_collection_decision_digest CHECK (
            event_code NOT IN ('DUPLICATE_DELIVERY','REFERENCE_CONFLICT') OR event_digest IS NOT NULL)
    );

    CREATE UNIQUE INDEX ux_payment_collection_decision_digest
        ON dbo.payment_collection_decision(payment_id,event_digest)
        WHERE event_digest IS NOT NULL;

    CREATE INDEX ix_payment_collection_decision_payment_time
        ON dbo.payment_collection_decision(payment_id,event_at,id);

    COMMIT TRANSACTION;
    PRINT 'Proposal DDL complete. This script has not been executed by the authoring task.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
