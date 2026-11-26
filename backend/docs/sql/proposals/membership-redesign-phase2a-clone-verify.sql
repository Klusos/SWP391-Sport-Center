/*
  DRAFT ONLY — Phase 2C.1 clone post-migration verification.
  Does not modify data. Requires the sidecar baseline manifest created by the
  clone-preflight script. Hard-rejects the original SportCenterDemo database.
*/
SET NOCOUNT ON;
DECLARE @expectedCloneServerProperty sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedCloneDatabase sysname = N'SportCenterDemo_Phase2C4_Rehearsal_20261119';
IF @expectedCloneServerProperty = N'__SET_VERIFIED_CLONE_SERVERPROPERTY_SERVERNAME__'
   OR @expectedCloneDatabase = N'__SET_VERIFIED_CLONE_DATABASE_NAME__'
   OR LEN(LTRIM(RTRIM(@expectedCloneServerProperty))) = 0
   OR LEN(LTRIM(RTRIM(@expectedCloneDatabase))) = 0
    THROW 50721,'Set both verified clone identity values before verification.',1;
IF UPPER(@expectedCloneDatabase) = N'SPORTCENTERDEMO'
    THROW 50722,'Refusing clone verification against the original SportCenterDemo database.',1;
IF CONVERT(sysname,SERVERPROPERTY('ServerName'))<>@expectedCloneServerProperty
   OR DB_NAME()<>@expectedCloneDatabase OR SCHEMA_NAME()<>N'dbo'
    THROW 50723,'Clone identity mismatch. Verification is invalid for this target.',1;
IF OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest',N'U') IS NULL
   OR OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest_run',N'U') IS NULL
    THROW 50724,'Clone baseline manifest/header is missing. No comparison is possible.',1;
IF (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest_run)<>1
   OR NOT EXISTS (SELECT 1 FROM dbo.phase2a_clone_rehearsal_manifest_run
                  WHERE manifest_key=1 AND is_complete=1
                    AND server_name=@expectedCloneServerProperty
                    AND database_name=@expectedCloneDatabase)
    THROW 50725,'Clone baseline manifest is incomplete or belongs to another identity.',1;
IF EXISTS (
    SELECT 1 FROM dbo.phase2a_clone_rehearsal_manifest_run r
    WHERE r.membership_count<>(SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Membership')
       OR r.payment_count<>(SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Payment')
       OR r.membership_upgrade_count<>(SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipUpgrade')
       OR r.cancellation_request_count<>(SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipCancellationRequest')
       OR r.manifest_record_count<>(SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest)
)
    THROW 50726,'Clone baseline manifest record counts are incomplete or corrupted.',1;

SELECT @@SERVERNAME AS server_name,
       CONVERT(sysname,SERVERPROPERTY('InstanceName')) AS instance_name,
       DB_NAME() AS database_name,SCHEMA_NAME() AS default_schema;

-- Added column types/nullability; existing fields and original relationships must remain.
SELECT t.name AS table_name,c.name AS column_name,ty.name AS data_type,
       c.max_length,c.precision,c.scale,c.is_nullable
FROM sys.tables t
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE t.schema_id=SCHEMA_ID(N'dbo')
  AND ((t.name=N'membership' AND c.name IN
       (N'benefit_snapshot_status',N'benefit_snapshot_source',N'plan_name_snapshot',N'plan_description_snapshot',N'plan_price_snapshot',N'plan_duration_months_snapshot',N'course_discount_percent_snapshot',N'pt_discount_percent_snapshot',N'allow_pt_plan_snapshot',N'allow_one_on_one_snapshot',N'paid_value_basis_status',N'paid_value_basis_amount',N'paid_value_basis_source',N'renewal_intent_id'))
    OR (t.name=N'payment' AND c.name IN
       (N'renewal_intent_id',N'attempt_valid_until',N'renewal_attempt_close_reason',N'renewal_settlement_accepted_at',N'membership_plan_name_snapshot',N'membership_plan_price_snapshot',N'membership_duration_months_snapshot',N'membership_course_discount_percent_snapshot',N'membership_pt_discount_percent_snapshot',N'membership_allow_pt_plan_snapshot',N'membership_allow_one_on_one_snapshot',N'membership_quote_captured_at'))
    OR (t.name=N'membership_upgrade' AND c.name LIKE N'%snapshot' OR t.name=N'membership_upgrade' AND c.name IN
       (N'source_period_start_date',N'source_period_end_date',N'credit_valuation_date',N'source_total_days',N'source_unused_days',N'source_paid_value_basis_amount',N'applied_credit_amount')))
ORDER BY t.name,c.column_id;

-- Immediate post-DDL expectation: new table empty; legacy rows have NULL snapshot and basis markers.
SELECT N'membership' entity,COUNT_BIG(*) row_count,
       SUM(CASE WHEN benefit_snapshot_status IS NULL THEN 1 ELSE 0 END) legacy_null_snapshot_count,
       SUM(CASE WHEN paid_value_basis_status IS NULL THEN 1 ELSE 0 END) legacy_null_basis_count
FROM dbo.membership
UNION ALL
SELECT N'membership_renewal_intent',COUNT_BIG(*),NULL,NULL FROM dbo.membership_renewal_intent;

-- Baseline values are not changed by this DDL-only migration.
SELECT status,COUNT_BIG(*) row_count,MIN(start_date) min_start_date,MAX(end_date) max_end_date
FROM dbo.membership GROUP BY status ORDER BY status;
SELECT purpose,status,COUNT_BIG(*) row_count,SUM(amount) total_amount,
       SUM(CASE WHEN refunded_at IS NOT NULL THEN 1 ELSE 0 END) refund_timestamp_count
FROM dbo.payment WHERE purpose IN ('MEMBERSHIP','UPGRADE')
GROUP BY purpose,status ORDER BY purpose,status;
SELECT COUNT_BIG(*) AS mismatched_current_plan_duration_count
FROM dbo.membership m JOIN dbo.membership_plan p ON p.id=m.plan_id
WHERE m.end_date<>DATEADD(month,p.duration_months,m.start_date);

-- Constraints must exist, be enabled and trusted. Preserve names/definitions for review.
SELECT name,is_disabled,is_not_trusted,definition
FROM sys.check_constraints
WHERE parent_object_id IN (OBJECT_ID(N'dbo.membership'),OBJECT_ID(N'dbo.payment'),
                           OBJECT_ID(N'dbo.membership_upgrade'),OBJECT_ID(N'dbo.membership_renewal_intent'))
ORDER BY parent_object_id,name;
SELECT f.name,OBJECT_NAME(f.parent_object_id) AS parent_table,
       OBJECT_NAME(f.referenced_object_id) AS referenced_table,
       f.is_disabled,f.is_not_trusted,f.delete_referential_action_desc
FROM sys.foreign_keys f
WHERE f.parent_object_id IN (OBJECT_ID(N'dbo.membership'),OBJECT_ID(N'dbo.payment'),
                             OBJECT_ID(N'dbo.membership_upgrade'),OBJECT_ID(N'dbo.membership_renewal_intent'))
ORDER BY parent_table,f.name;
SELECT OBJECT_NAME(i.object_id) AS table_name,i.name,i.is_unique,i.is_disabled,
       i.has_filter,i.filter_definition,ic.key_ordinal,c.name AS key_column
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=0
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.name IN (N'ux_payment_one_accepted_renewal_attempt',N'ix_payment_renewal_intent_status',
                 N'ix_renewal_intent_due',N'ux_membership_one_renewal_successor',
                 N'ix_membership_member_status_dates',N'ux_membership_upgrade_one_success_per_source')
ORDER BY table_name,i.name,ic.key_ordinal;

-- Relational consistency: every settled intent has exactly one matching successor/payment;
-- every accepted settlement is linked back to the intent and its successor.
SELECT ri.id AS renewal_intent_id,ri.intent_status,ri.source_membership_id,
       ri.settled_payment_id,ri.settled_at,
       m.id AS successor_membership_id,m.purchase_payment_id AS successor_payment_id,
       p.status AS payment_status,p.renewal_settlement_accepted_at
FROM dbo.membership_renewal_intent ri
LEFT JOIN dbo.membership m ON m.renewal_intent_id=ri.id
LEFT JOIN dbo.payment p ON p.id=ri.settled_payment_id
ORDER BY ri.id;

SELECT p.id AS payment_id,p.renewal_intent_id,p.status,p.renewal_settlement_accepted_at,
       COUNT(m.id) AS successor_count
FROM dbo.payment p
LEFT JOIN dbo.membership m ON m.renewal_intent_id=p.renewal_intent_id
WHERE p.renewal_intent_id IS NOT NULL AND p.renewal_settlement_accepted_at IS NOT NULL
GROUP BY p.id,p.renewal_intent_id,p.status,p.renewal_settlement_accepted_at
HAVING COUNT(m.id)<>1;

-- Cross-table invariant not expressible by a SQL Server CHECK/FK alone:
-- a settled Intent and its selected Payment must agree on the permanent accepted marker.
SELECT ri.id AS renewal_intent_id,ri.intent_status,ri.settled_payment_id,
       p.status AS selected_payment_status,p.renewal_settlement_accepted_at
FROM dbo.membership_renewal_intent ri
LEFT JOIN dbo.payment p ON p.id=ri.settled_payment_id
WHERE (ri.intent_status='SETTLED'
       AND (p.id IS NULL OR p.renewal_intent_id<>ri.id
            OR p.renewal_settlement_accepted_at IS NULL
            OR p.status NOT IN ('SUCCESS','REFUND_REQUIRED','REFUNDED')))
   OR (p.renewal_settlement_accepted_at IS NOT NULL
       AND (ri.intent_status<>'SETTLED' OR ri.settled_payment_id<>p.id));

-- Orphan checks should return zero rows.
SELECT N'payment_without_intent' AS issue,p.id
FROM dbo.payment p LEFT JOIN dbo.membership_renewal_intent ri ON ri.id=p.renewal_intent_id
WHERE p.renewal_intent_id IS NOT NULL AND ri.id IS NULL
UNION ALL
SELECT N'membership_without_intent',m.id
FROM dbo.membership m LEFT JOIN dbo.membership_renewal_intent ri ON ri.id=m.renewal_intent_id
WHERE m.renewal_intent_id IS NOT NULL AND ri.id IS NULL;

-- Per-record comparison against the baseline manifest. Hashes cover every
-- original application column in each row and deliberately omit new Phase 2A
-- columns. Only changed/missing/new records are returned here.
DECLARE @current_manifest TABLE (
    entity_name varchar(40) NOT NULL,
    record_id int NOT NULL,
    row_hash binary(32) NOT NULL,
    PRIMARY KEY(entity_name,record_id)
);

INSERT @current_manifest(entity_name,record_id,row_hash)
SELECT 'Membership',m.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership m
CROSS APPLY (SELECT (SELECT m.member_id,m.plan_id,m.auto_renew,m.start_date,m.end_date,m.status,
                            m.purchase_payment_id,m.cancelled_at,m.cancel_reason
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

INSERT @current_manifest(entity_name,record_id,row_hash)
SELECT 'Payment',p.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.payment p
CROSS APPLY (SELECT (SELECT p.member_id,p.amount,p.method,p.purpose,p.reference_id,p.auto_renew,p.status,
                            p.transaction_code,p.processed_by,p.note,p.created_at,p.paid_at,
                            p.refunded_at,p.refunded_by
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

INSERT @current_manifest(entity_name,record_id,row_hash)
SELECT 'MembershipUpgrade',u.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership_upgrade u
CROSS APPLY (SELECT (SELECT u.payment_id,u.source_membership_id,u.target_plan_id,u.remaining_days,
                            u.expires_at,u.created_at,u.result_membership_id
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

INSERT @current_manifest(entity_name,record_id,row_hash)
SELECT 'MembershipCancellationRequest',r.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
FROM dbo.membership_cancellation_request r
CROSS APPLY (SELECT (SELECT r.membership_id,r.member_id,r.reason,r.status,r.requested_at,
                            r.decided_by,r.decided_at,r.decision_note
                     FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

DECLARE @record_differences TABLE (
    entity_name varchar(40) NOT NULL,
    record_id int NOT NULL,
    comparison varchar(32) NOT NULL
);
INSERT @record_differences(entity_name,record_id,comparison)
SELECT COALESCE(b.entity_name,c.entity_name),COALESCE(b.record_id,c.record_id),
       CASE WHEN b.record_id IS NULL THEN 'NEW_AFTER_BASELINE'
            WHEN c.record_id IS NULL THEN 'MISSING_AFTER_MIGRATION'
            WHEN b.row_hash<>c.row_hash THEN 'CHANGED'
            ELSE 'UNCHANGED' END
FROM dbo.phase2a_clone_rehearsal_manifest b
FULL OUTER JOIN @current_manifest c
  ON c.entity_name=b.entity_name AND c.record_id=b.record_id;

-- Preserve record-level diagnostics before failing the batch.
SELECT entity_name,record_id,comparison
FROM @record_differences
WHERE comparison<>'UNCHANGED'
ORDER BY entity_name,record_id;

-- Summary by entity; any non-zero difference count requires investigation.
SELECT COALESCE(b.entity_name,c.entity_name) AS entity_name,
       SUM(CASE WHEN b.record_id IS NOT NULL THEN 1 ELSE 0 END) AS baseline_records,
       SUM(CASE WHEN c.record_id IS NOT NULL THEN 1 ELSE 0 END) AS current_records,
       SUM(CASE WHEN b.record_id IS NOT NULL AND c.record_id IS NOT NULL AND b.row_hash<>c.row_hash
                THEN 1 ELSE 0 END) AS changed_records,
       SUM(CASE WHEN b.record_id IS NOT NULL AND c.record_id IS NULL THEN 1 ELSE 0 END) AS missing_records,
       SUM(CASE WHEN b.record_id IS NULL AND c.record_id IS NOT NULL THEN 1 ELSE 0 END) AS new_records
FROM dbo.phase2a_clone_rehearsal_manifest b
FULL OUTER JOIN @current_manifest c
  ON c.entity_name=b.entity_name AND c.record_id=b.record_id
GROUP BY COALESCE(b.entity_name,c.entity_name)
ORDER BY entity_name;

IF EXISTS (SELECT 1 FROM @record_differences WHERE comparison<>'UNCHANGED')
    THROW 50727,'Per-record baseline comparison failed: original Membership/Payment/Upgrade/CancellationRequest rows changed, went missing, or were added.',1;

PRINT 'Per-record baseline comparison passed: all four original tables match the complete preflight manifest.';

