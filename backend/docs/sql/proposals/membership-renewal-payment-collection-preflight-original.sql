/* ORIGINAL DATABASE ONLY. Proposal; execution needs separate authorization.
   Mandatory SQLCMD variables: -v ExpectedServer="..." ExpectedDatabase="..." ExpectedDatabaseId="..." ExpectedProductVersion="...".
   This variant accepts only LAPTOP-416VBDLK / SportCenterDemo / database ID 5 / SQL Server 17.0.1155.3.
   No USE statement, alternate target, historical evidence backfill, or business-row DML is included. */
SET NOCOUNT ON;
DECLARE @expectedServer nvarchar(128)=N'$(ExpectedServer)';
DECLARE @expectedDatabase sysname=N'$(ExpectedDatabase)';
DECLARE @expectedDatabaseIdText nvarchar(20)=N'$(ExpectedDatabaseId)';
DECLARE @expectedProductVersion nvarchar(128)=N'$(ExpectedProductVersion)';
DECLARE @expectedDatabaseId int=TRY_CONVERT(int,@expectedDatabaseIdText);
DECLARE @actualServer nvarchar(128)=CONVERT(nvarchar(128),SERVERPROPERTY('ServerName'));
DECLARE @actualProductVersion nvarchar(128)=CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion'));
IF NULLIF(LTRIM(RTRIM(@expectedServer)),N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabase)),N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabaseIdText)),N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedProductVersion)),N'') IS NULL
   OR CHARINDEX(N'$(',@expectedServer)>0 OR CHARINDEX(N'$(',@expectedDatabase)>0
   OR CHARINDEX(N'$(',@expectedDatabaseIdText)>0 OR CHARINDEX(N'$(',@expectedProductVersion)>0
   OR @expectedServer COLLATE Latin1_General_100_BIN2<>N'LAPTOP-416VBDLK' COLLATE Latin1_General_100_BIN2
   OR @expectedDatabase COLLATE Latin1_General_100_BIN2<>N'SportCenterDemo' COLLATE Latin1_General_100_BIN2
   OR @expectedDatabaseId IS NULL OR @expectedDatabaseIdText<>N'5'
   OR @expectedProductVersion COLLATE Latin1_General_100_BIN2<>N'17.0.1155.3' COLLATE Latin1_General_100_BIN2
    THROW 52900,'Explicit approved original-target variables are missing, unresolved, or not the reviewed values.',1;
IF @actualServer IS NULL
   OR @actualServer COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
   OR CONVERT(nvarchar(128),@@SERVERNAME) COLLATE Latin1_General_100_BIN2<>@expectedServer COLLATE Latin1_General_100_BIN2
   OR DB_NAME() COLLATE Latin1_General_100_BIN2<>@expectedDatabase COLLATE Latin1_General_100_BIN2
   OR DB_ID()<>@expectedDatabaseId OR DB_ID()<>5 OR DB_ID()=12
   OR DB_NAME()=N'SportCenterDemo_PTOutcome_8CF1'
   OR @actualProductVersion IS NULL
   OR @actualProductVersion COLLATE Latin1_General_100_BIN2<>@expectedProductVersion COLLATE Latin1_General_100_BIN2
   OR NOT EXISTS(SELECT 1 FROM sys.databases WHERE database_id=5 AND name=N'SportCenterDemo' AND state=0)
    THROW 52901,'Connected SQL Server/database identity, ID, version, or ONLINE state does not match the approved original target.',1;
IF OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_cancellation_request', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_renewal_intent', N'U') IS NULL
    THROW 50821, 'A required business table is missing.', 1;

DECLARE @required TABLE (table_name sysname NOT NULL, column_name sysname NOT NULL, PRIMARY KEY(table_name,column_name));
INSERT INTO @required(table_name,column_name) VALUES
 (N'payment',N'id'),(N'payment',N'member_id'),(N'payment',N'amount'),(N'payment',N'method'),
 (N'payment',N'purpose'),(N'payment',N'reference_id'),(N'payment',N'status'),(N'payment',N'transaction_code'),
 (N'payment',N'paid_at'),(N'payment',N'processed_by'),(N'payment',N'renewal_intent_id'),
 (N'payment',N'attempt_valid_until'),(N'payment',N'renewal_attempt_close_reason'),
 (N'payment',N'renewal_settlement_accepted_at'),
 (N'membership',N'id'),(N'membership',N'renewal_intent_id'),(N'membership',N'purchase_payment_id'),
 (N'membership_upgrade',N'id'),(N'membership_cancellation_request',N'id'),
 (N'membership_renewal_intent',N'id');
IF EXISTS (SELECT 1 FROM @required r
           LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+r.table_name,N'U') AND c.name=r.column_name
           WHERE c.column_id IS NULL)
    THROW 50822, 'A required Phase 2D table column is missing.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
               AND name=N'ux_payment_one_accepted_renewal_attempt' AND is_unique=1 AND is_disabled=0
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_intent_id is not null%'
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_settlement_accepted_at is not null%')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
                  AND name=N'ix_payment_renewal_intent_status' AND is_disabled=0)
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership_renewal_intent')
                  AND name=N'ix_renewal_intent_due' AND is_disabled=0)
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership')
                  AND name=N'ux_membership_one_renewal_successor' AND is_unique=1 AND is_disabled=0
                  AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_intent_id is not null%')
    THROW 50823, 'Required Phase 2D Renewal indexes are missing or disabled.', 1;

DECLARE @protectedTables TABLE (table_name sysname PRIMARY KEY);
INSERT INTO @protectedTables VALUES
 (N'membership'),(N'payment'),(N'membership_upgrade'),(N'membership_cancellation_request');
IF EXISTS (SELECT 1 FROM @protectedTables t
           WHERE NOT EXISTS (SELECT 1 FROM sys.indexes i
                             WHERE i.object_id=OBJECT_ID(N'dbo.'+t.table_name,N'U')
                               AND i.is_primary_key=1 AND i.is_disabled=0))
    THROW 50826, 'A protected business table lacks an enabled primary key required for exact row-level comparison.', 1;

/* The new settlement CHECK cannot be truthfully applied to a historical accepted Renewal Payment. */
IF EXISTS (SELECT 1 FROM dbo.payment
           WHERE renewal_intent_id IS NOT NULL AND renewal_settlement_accepted_at IS NOT NULL)
    THROW 50827, 'An accepted Renewal Payment predates durable collection evidence; stop for separate review, do not backfill.', 1;

IF COL_LENGTH(N'dbo.payment',N'renewal_provider_transaction_reference') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_verified_collection_amount') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_verified_collection_currency') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_paid_time_evidence_type') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_collection_verified_at') IS NOT NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_collection_conflict_detected_at') IS NOT NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_collection_evidence',N'C') IS NOT NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_settlement_has_collection',N'C') IS NOT NULL
   OR EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
             AND name=N'ux_payment_renewal_provider_transaction_reference')
    THROW 50824, 'Renewal collection schema is already or partially present.', 1;

IF EXISTS (
    SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
    WHERE c.object_id IN (OBJECT_ID(N'dbo.membership'),OBJECT_ID(N'dbo.payment'),
                          OBJECT_ID(N'dbo.membership_upgrade'),OBJECT_ID(N'dbo.membership_cancellation_request'))
      AND t.name IN (N'text',N'ntext',N'image',N'xml',N'sql_variant',N'geography',N'geometry',N'hierarchyid')
)
    THROW 50825, 'A protected table contains a type not supported by the exact EXCEPT baseline; prepare explicit lossless mappings.', 1;

/* Session-scoped record baseline. The four copies include every persisted column and primary key. */
SELECT * INTO #baseline_membership FROM dbo.membership;
SELECT * INTO #baseline_payment FROM dbo.payment;
SELECT * INTO #baseline_membership_upgrade FROM dbo.membership_upgrade;
SELECT * INTO #baseline_membership_cancellation_request FROM dbo.membership_cancellation_request;

SELECT @actualServer AS server_name, DB_NAME() AS database_name,
       DATABASEPROPERTYEX(DB_NAME(),'Status') AS database_status,
       N'Baseline captured in #baseline_* tempdb tables; keep this connection for migration and verification.' AS baseline_note;
SELECT N'membership' AS table_name, COUNT_BIG(*) AS baseline_rows FROM #baseline_membership
UNION ALL SELECT N'payment', COUNT_BIG(*) FROM #baseline_payment
UNION ALL SELECT N'membership_upgrade', COUNT_BIG(*) FROM #baseline_membership_upgrade
UNION ALL SELECT N'membership_cancellation_request', COUNT_BIG(*) FROM #baseline_membership_cancellation_request;

