/*
  PROPOSAL ONLY — READ-ONLY preflight for payment collection evidence schema.
  Set both identity placeholders to the separately approved isolated target before use.
  This script performs SELECT/metadata reads only. No SQL Server connection was made
  while preparing this draft.
*/
SET NOCOUNT ON;
DECLARE @expectedServer sysname = N'__SET_APPROVED_SERVER__';
DECLARE @expectedDatabase sysname = N'__SET_APPROVED_DATABASE__';

IF @expectedServer = N'__SET_APPROVED_SERVER__' OR @expectedDatabase = N'__SET_APPROVED_DATABASE__'
    THROW 50780, 'Set an explicitly approved server and database identity before preflight.', 1;
IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50781, 'Payment evidence preflight identity mismatch.', 1;
IF OBJECT_ID(N'dbo.payment',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_renewal_intent',N'U') IS NULL
   OR OBJECT_ID(N'dbo.users',N'U') IS NULL
    THROW 50782, 'Required Payment, Renewal Intent, or User table is missing.', 1;

SELECT @@SERVERNAME AS server_name,
       CONVERT(sysname, SERVERPROPERTY('InstanceName')) AS instance_name,
       DB_NAME() AS database_name,
       CONVERT(nvarchar(60), DATABASEPROPERTYEX(DB_NAME(), 'Status')) AS database_status,
       SCHEMA_NAME() AS default_schema;

-- Required Phase 2A parent objects and exact columns. Missing rows are blockers.
WITH required(table_name, column_name) AS (
    SELECT N'payment', v.column_name FROM (VALUES
        (N'id'),(N'member_id'),(N'amount'),(N'method'),(N'purpose'),(N'status'),
        (N'transaction_code'),(N'processed_by'),(N'paid_at'),(N'renewal_intent_id'),
        (N'attempt_valid_until'),(N'renewal_settlement_accepted_at')) v(column_name)
    UNION ALL
    SELECT N'membership_renewal_intent', v.column_name FROM (VALUES
        (N'id'),(N'settled_payment_id'),(N'intent_status'),(N'settled_at')) v(column_name)
)
SELECT r.table_name, r.column_name,
       CASE WHEN t.object_id IS NULL THEN N'MISSING TABLE'
            WHEN c.column_id IS NULL THEN N'MISSING COLUMN' ELSE N'PRESENT' END AS state,
       ty.name AS data_type, c.max_length, c.precision, c.scale, c.is_nullable
FROM required r
LEFT JOIN sys.tables t ON t.schema_id=SCHEMA_ID(N'dbo') AND t.name=r.table_name
LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name=r.column_name
LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id
ORDER BY r.table_name,r.column_name;

-- Phase 2A relationship/unique-index prerequisites.
SELECT f.name, OBJECT_NAME(f.parent_object_id) AS parent_table,
       OBJECT_NAME(f.referenced_object_id) AS referenced_table,
       f.is_disabled, f.is_not_trusted
FROM sys.foreign_keys f
WHERE f.name IN (N'fk_payment_renewal_intent',N'fk_renewal_intent_settled_payment',
                 N'fk_membership_renewal_intent_settled_payment')
ORDER BY f.name;
SELECT i.name, OBJECT_NAME(i.object_id) AS table_name, i.is_unique,
       i.is_disabled, i.has_filter, i.filter_definition
FROM sys.indexes i
WHERE i.name IN (N'ux_payment_one_accepted_renewal_attempt',
                 N'ux_membership_one_renewal_successor')
ORDER BY i.name;

-- Counts only: no member names, notes, provider secrets, or personal data.
SELECT COUNT_BIG(*) AS renewal_intent_count FROM dbo.membership_renewal_intent;
SELECT COUNT_BIG(*) AS accepted_renewal_attempt_count
FROM dbo.payment
WHERE renewal_intent_id IS NOT NULL AND renewal_settlement_accepted_at IS NOT NULL;
SELECT COUNT_BIG(*) AS existing_evidence_table_count
FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'payment_collection_evidence';
SELECT COUNT_BIG(*) AS existing_decision_table_count
FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'payment_collection_decision';
SELECT COUNT_BIG(*) AS existing_payment_accepted_evidence_column_count
FROM sys.columns
WHERE object_id=OBJECT_ID(N'dbo.payment') AND name=N'accepted_collection_evidence_id';

-- Any existing settled Intent or accepted Renewal attempt has no backfilled evidence.
-- Return only IDs/counts for owner disposition; never fabricate historical evidence.
SELECT p.id AS payment_id, p.renewal_intent_id, p.renewal_settlement_accepted_at
FROM dbo.payment p
WHERE p.renewal_intent_id IS NOT NULL AND p.renewal_settlement_accepted_at IS NOT NULL
ORDER BY p.id;
SELECT ri.id AS intent_id, ri.settled_payment_id, ri.settled_at
FROM dbo.membership_renewal_intent ri
WHERE ri.intent_status=N'SETTLED'
ORDER BY ri.id;
