/*
  PROPOSAL ONLY — read-only post-migration verification.
  Set expected identity to the separately approved target. No data is modified.
*/
SET NOCOUNT ON;
DECLARE @expectedServer sysname = N'__SET_APPROVED_SERVER__';
DECLARE @expectedDatabase sysname = N'__SET_APPROVED_DATABASE__';
IF @expectedServer = N'__SET_APPROVED_SERVER__' OR @expectedDatabase = N'__SET_APPROVED_DATABASE__'
    THROW 50788, 'Set an explicitly approved server and database identity before verification.', 1;
IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase OR SCHEMA_NAME() <> N'dbo'
    THROW 50789, 'Payment evidence verification identity mismatch.', 1;

SELECT @@SERVERNAME AS server_name,
       CONVERT(sysname,SERVERPROPERTY('InstanceName')) AS instance_name,
       DB_NAME() AS database_name,
       CONVERT(nvarchar(60),DATABASEPROPERTYEX(DB_NAME(),'Status')) AS database_status;

SELECT t.name AS table_name,c.name AS column_name,ty.name AS data_type,
       c.max_length,c.precision,c.scale,c.is_nullable
FROM sys.tables t
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE t.schema_id=SCHEMA_ID(N'dbo')
  AND (t.name IN (N'payment_collection_evidence',N'payment_collection_decision')
       OR (t.name=N'payment' AND c.name=N'accepted_collection_evidence_id'))
ORDER BY t.name,c.column_id;

SELECT f.name,OBJECT_NAME(f.parent_object_id) AS parent_table,
       OBJECT_NAME(f.referenced_object_id) AS referenced_table,
       f.is_disabled,f.is_not_trusted,f.delete_referential_action_desc
FROM sys.foreign_keys f
WHERE f.name IN (N'fk_payment_collection_evidence_payment',N'fk_payment_collection_evidence_actor',
                 N'fk_payment_accepted_collection_evidence',N'fk_payment_collection_decision_payment',
                 N'fk_payment_collection_decision_evidence',N'fk_payment_collection_decision_related_evidence',
                 N'fk_payment_collection_decision_actor')
ORDER BY f.name;

SELECT name,parent_object_id,is_disabled,is_not_trusted,definition
FROM sys.check_constraints
WHERE name IN (N'ck_payment_collection_evidence_source',N'ck_payment_collection_evidence_facts',
               N'ck_payment_collection_decision_code',N'ck_payment_collection_decision_evidence_required',
               N'ck_payment_collection_decision_refund_evidence',
               N'ck_payment_collection_decision_digest')
ORDER BY name;

SELECT OBJECT_NAME(i.object_id) AS table_name,i.name,i.is_unique,i.is_disabled,
       i.has_filter,i.filter_definition,ic.key_ordinal,c.name AS key_column
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.name IN (N'ux_payment_collection_evidence_provider_reference',
                 N'ux_payment_collection_evidence_payment_digest',
                 N'ux_payment_collection_evidence_counter_reference',
                 N'ux_payment_accepted_collection_evidence',
                 N'ux_payment_collection_decision_digest',
                 N'ux_payment_one_accepted_renewal_attempt',
                 N'ux_membership_one_renewal_successor')
ORDER BY table_name,i.name,ic.key_ordinal;

-- New tables must start empty; no historical backfill is expected.
SELECT (SELECT COUNT_BIG(*) FROM dbo.payment_collection_evidence) AS evidence_rows,
       (SELECT COUNT_BIG(*) FROM dbo.payment_collection_decision) AS decision_rows;

-- Referential and lifecycle cross-checks; these do not infer legacy evidence.
SELECT p.id AS payment_id,p.renewal_intent_id,p.status,p.renewal_settlement_accepted_at,
       p.accepted_collection_evidence_id,e.payment_id AS evidence_payment_id,
       e.source_code,e.verification_result
FROM dbo.payment p
LEFT JOIN dbo.payment_collection_evidence e ON e.id=p.accepted_collection_evidence_id
WHERE p.accepted_collection_evidence_id IS NOT NULL
  AND (e.id IS NULL OR e.payment_id<>p.id);

SELECT ri.id AS intent_id,ri.intent_status,ri.settled_payment_id,
       p.accepted_collection_evidence_id,e.payment_id AS evidence_payment_id
FROM dbo.membership_renewal_intent ri
JOIN dbo.payment p ON p.id=ri.settled_payment_id
LEFT JOIN dbo.payment_collection_evidence e ON e.id=p.accepted_collection_evidence_id
WHERE ri.intent_status=N'SETTLED'
  AND (p.accepted_collection_evidence_id IS NULL OR e.payment_id<>p.id);
