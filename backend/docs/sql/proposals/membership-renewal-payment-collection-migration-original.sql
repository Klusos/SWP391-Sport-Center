/* ORIGINAL DATABASE ONLY. Use only after separate execution authorization.
   Mandatory SQLCMD variables: ExpectedServer, ExpectedDatabase, ExpectedDatabaseId, ExpectedProductVersion.
   No USE statement, fallback target, historical backfill, or business-row DML. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF @@TRANCOUNT<>0 THROW 52860,'Run as a standalone batch without an ambient transaction.',1;
DECLARE @expectedServer nvarchar(128)=N'$(ExpectedServer)';
DECLARE @expectedDatabase sysname=N'$(ExpectedDatabase)';
DECLARE @expectedDatabaseIdText nvarchar(20)=N'$(ExpectedDatabaseId)';
DECLARE @expectedProductVersion nvarchar(128)=N'$(ExpectedProductVersion)';
DECLARE @expectedDatabaseId int=TRY_CONVERT(int,@expectedDatabaseIdText);
DECLARE @actualServer nvarchar(128)=CONVERT(nvarchar(128),SERVERPROPERTY('ServerName'));
DECLARE @actualProductVersion nvarchar(128)=CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion'));
IF NULLIF(LTRIM(RTRIM(@expectedServer)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(@expectedDatabase)),N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabaseIdText)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(@expectedProductVersion)),N'') IS NULL
   OR CHARINDEX(N'$(',@expectedServer)>0 OR CHARINDEX(N'$(',@expectedDatabase)>0
   OR CHARINDEX(N'$(',@expectedDatabaseIdText)>0 OR CHARINDEX(N'$(',@expectedProductVersion)>0
   OR @expectedServer COLLATE Latin1_General_100_BIN2<>N'LAPTOP-416VBDLK' COLLATE Latin1_General_100_BIN2
   OR @expectedDatabase COLLATE Latin1_General_100_BIN2<>N'SportCenterDemo' COLLATE Latin1_General_100_BIN2
   OR @expectedDatabaseId IS NULL OR @expectedDatabaseIdText<>N'5'
   OR @expectedProductVersion COLLATE Latin1_General_100_BIN2<>N'17.0.1155.3' COLLATE Latin1_General_100_BIN2
    THROW 52861,'Explicit approved original-target variables are missing, unresolved, or not the reviewed values.',1;
IF @actualServer IS NULL
   OR @actualServer COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
   OR CONVERT(nvarchar(128),@@SERVERNAME) COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
   OR DB_NAME() COLLATE Latin1_General_100_BIN2<>@expectedDatabase COLLATE Latin1_General_100_BIN2
   OR DB_ID()<>@expectedDatabaseId OR DB_ID()<>5 OR DB_ID()=12
   OR DB_NAME()=N'SportCenterDemo_PTOutcome_8CF1'
   OR @actualProductVersion IS NULL
   OR @actualProductVersion COLLATE Latin1_General_100_BIN2<>@expectedProductVersion COLLATE Latin1_General_100_BIN2
   OR NOT EXISTS(SELECT 1 FROM sys.databases WHERE database_id=5 AND name=N'SportCenterDemo' AND state=0)
    THROW 52862,'Connected SQL Server/database identity, ID, version, or ONLINE state does not match the approved original target.',1;
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

    IF CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
       OR CONVERT(nvarchar(128),@@SERVERNAME) COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
       OR DB_NAME() COLLATE Latin1_General_100_BIN2<>@expectedDatabase COLLATE Latin1_General_100_BIN2
       OR DB_ID()<>@expectedDatabaseId OR DB_ID()<>5 OR DB_ID()=12
       OR DB_NAME()=N'SportCenterDemo_PTOutcome_8CF1'
       OR CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')) COLLATE Latin1_General_100_BIN2<>@expectedProductVersion COLLATE Latin1_General_100_BIN2
       OR NOT EXISTS(SELECT 1 FROM sys.databases WHERE database_id=5 AND name=N'SportCenterDemo' AND state=0)
        THROW 52863,'Original target identity/version/state changed inside migration transaction.',1;

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

