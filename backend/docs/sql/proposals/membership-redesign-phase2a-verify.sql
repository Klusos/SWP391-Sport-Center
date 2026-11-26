/*
  DRAFT ONLY — Phase 2A post-migration verification.
  Read-only SELECT/metadata checks. Run only after an approved migration and
  compare the entire output to its immediately preceding preflight manifest.
*/
SET NOCOUNT ON;
DECLARE @expectedServer sysname=N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname=N'SportCenterDemo';
IF CONVERT(sysname,SERVERPROPERTY('ServerName'))<>@expectedServer
   OR DB_NAME()<>@expectedDatabase OR SCHEMA_NAME()<>N'dbo'
    THROW 50630,'Identity mismatch. Stop; verification is invalid for this target.',1;

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
