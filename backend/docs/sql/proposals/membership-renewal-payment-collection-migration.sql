/* DRAFT ONLY. Clone rehearsal proposal; never execute without separate approval.
   Invoke with SQLCMD variables: -v ExpectedServer="..." ExpectedDatabase="..."
   The exact identity guard is not proof of clone provenance; verify source/restore metadata independently.
   No USE statement, fallback target, historical backfill, or business-data update is included. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedServer sysname = N'$(ExpectedServer)';
DECLARE @expectedDatabase sysname = N'$(ExpectedDatabase)';
DECLARE @actualServer sysname = CONVERT(sysname, SERVERPROPERTY('ServerName'));
IF NULLIF(LTRIM(RTRIM(@expectedServer)), N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabase)), N'') IS NULL
   OR @expectedServer = N'$' + N'(ExpectedServer)'
   OR @expectedDatabase = N'$' + N'(ExpectedDatabase)'
   OR UPPER(LTRIM(RTRIM(@expectedDatabase))) = N'SPORTCENTERDEMO'
   OR @actualServer IS NULL OR CONVERT(varbinary(256),@actualServer) <> CONVERT(varbinary(256),@expectedServer)
   OR CONVERT(varbinary(256),DB_NAME()) <> CONVERT(varbinary(256),@expectedDatabase)
   OR DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 50810, 'Explicit isolated-clone identity is missing or mismatched. Migration stopped.', 1;

IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_renewal_intent', N'U') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'method') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'amount') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'paid_at') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'processed_by') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_intent_id') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'attempt_valid_until') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_settlement_accepted_at') IS NULL
    THROW 50811, 'Required Renewal Payment schema prerequisites are missing.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment')
               AND name = N'ux_payment_one_accepted_renewal_attempt' AND is_unique = 1 AND is_disabled = 0
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'')
                    LIKE N'%renewal_intent_id is not null%'
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'')
                    LIKE N'%renewal_settlement_accepted_at is not null%')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.membership')
                  AND name = N'ux_membership_one_renewal_successor' AND is_unique = 1 AND is_disabled = 0
                  AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'')
                    LIKE N'%renewal_intent_id is not null%')
    THROW 50814, 'Phase 2D accepted-attempt/successor uniqueness prerequisites are missing.', 1;

/* No historical accepted Renewal settlement can be truthfully backfilled with collection evidence. */
IF EXISTS (SELECT 1 FROM dbo.payment
           WHERE renewal_intent_id IS NOT NULL AND renewal_settlement_accepted_at IS NOT NULL)
    THROW 50815, 'An accepted Renewal Payment already exists without the new collection evidence; inspect separately, do not fabricate a backfill.', 1;

IF COL_LENGTH(N'dbo.payment', N'renewal_provider_transaction_reference') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_verified_collection_amount') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_verified_collection_currency') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_paid_time_evidence_type') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_collection_verified_at') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_collection_conflict_detected_at') IS NOT NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_collection_evidence', N'C') IS NOT NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_settlement_has_collection', N'C') IS NOT NULL
   OR EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment')
              AND name = N'ux_payment_renewal_provider_transaction_reference')
    THROW 50812, 'Renewal collection schema is already or partially present; inspect before proceeding.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    ALTER TABLE dbo.payment ADD
        renewal_provider_transaction_reference varchar(64) NULL,
        renewal_verified_collection_amount decimal(12,2) NULL,
        renewal_verified_collection_currency varchar(3) NULL,
        renewal_paid_time_evidence_type varchar(24) NULL,
        renewal_collection_verified_at datetime2(6) NULL,
        renewal_collection_conflict_detected_at datetime2(6) NULL;

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_renewal_collection_evidence CHECK (
        (renewal_collection_verified_at IS NULL
            AND renewal_provider_transaction_reference IS NULL
            AND renewal_verified_collection_amount IS NULL
            AND renewal_verified_collection_currency IS NULL
            AND renewal_paid_time_evidence_type IS NULL
            AND renewal_collection_conflict_detected_at IS NULL)
        OR (renewal_collection_verified_at IS NULL
            AND renewal_collection_conflict_detected_at IS NOT NULL
            AND renewal_intent_id IS NOT NULL
            AND renewal_provider_transaction_reference IS NULL
            AND renewal_verified_collection_amount IS NULL
            AND renewal_verified_collection_currency IS NULL
            AND renewal_paid_time_evidence_type IS NULL)
        OR (renewal_intent_id IS NOT NULL
            AND renewal_collection_verified_at IS NOT NULL
            AND method IS NOT NULL
            AND renewal_paid_time_evidence_type IS NOT NULL
            AND renewal_paid_time_evidence_type IN (''PROVIDER_VERIFIED'',''MISSING'',''MALFORMED'',''SERVER_CONFIRMATION'')
            AND ((method = ''ONLINE'' AND renewal_paid_time_evidence_type IN (''PROVIDER_VERIFIED'',''MISSING'',''MALFORMED''))
                 OR (method = ''COUNTER'' AND renewal_paid_time_evidence_type = ''SERVER_CONFIRMATION''
                    AND renewal_provider_transaction_reference IS NULL)))
    );';

    EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_renewal_settlement_has_collection CHECK (
        renewal_settlement_accepted_at IS NULL
        OR renewal_intent_id IS NULL
        OR (renewal_collection_conflict_detected_at IS NULL
            AND method IS NOT NULL
            AND renewal_collection_verified_at IS NOT NULL
                    AND amount IS NOT NULL
                    AND renewal_verified_collection_amount IS NOT NULL
                    AND renewal_verified_collection_amount = amount
                    AND renewal_verified_collection_currency IS NOT NULL
                    AND renewal_verified_collection_currency = ''VND''
                    AND renewal_paid_time_evidence_type IS NOT NULL
                    AND paid_at IS NOT NULL
                    AND ((method = ''ONLINE'' AND renewal_paid_time_evidence_type = ''PROVIDER_VERIFIED''
                            AND renewal_provider_transaction_reference IS NOT NULL)
                         OR (method = ''COUNTER'' AND renewal_paid_time_evidence_type = ''SERVER_CONFIRMATION''
                            AND processed_by IS NOT NULL
                             AND renewal_provider_transaction_reference IS NULL)))
    );';

    EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_payment_renewal_provider_transaction_reference
        ON dbo.payment(renewal_provider_transaction_reference)
        WHERE renewal_intent_id IS NOT NULL AND renewal_provider_transaction_reference IS NOT NULL;';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
