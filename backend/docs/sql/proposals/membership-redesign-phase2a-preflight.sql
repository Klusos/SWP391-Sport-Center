/*
  DRAFT ONLY — Phase 2A Membership Redesign read-only preflight.
  This file contains SELECT and metadata reads only. Do not interpret the TLS
  exception as target authentication; follow the owner-approved local-only
  connection guidance in docs/membership-phase2a-schema-migration-design.md.
  Expected identity is the locally reviewed development/test database only.
*/
SET NOCOUNT ON;
DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @businessDate date = CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time');

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50601, 'Identity guard failed. Stop; no preflight result is valid for another target.', 1;

SELECT @@SERVERNAME AS server_name,
       CONVERT(sysname, SERVERPROPERTY('InstanceName')) AS instance_name,
       DB_NAME() AS database_name,
       SCHEMA_NAME() AS default_schema,
       @businessDate AS business_date_vietnam;

-- Current migration targets and any partial prior attempt.
SELECT s.name AS schema_name, t.name AS table_name, c.name AS column_name,
       ty.name AS data_type, c.max_length, c.precision, c.scale, c.is_nullable
FROM sys.schemas s
JOIN sys.tables t ON t.schema_id=s.schema_id
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE s.name=N'dbo'
  AND (t.name IN (N'membership',N'membership_plan',N'payment',N'membership_upgrade',
                  N'membership_cancellation_request',N'audit_log',N'membership_renewal_intent')
       OR t.name LIKE N'%refund%')
ORDER BY t.name,c.column_id;

-- Current foreign keys, enabled/trusted state and delete/update actions.
SELECT f.name AS fk_name, OBJECT_NAME(f.parent_object_id) AS parent_table,
       pc.name AS parent_column, OBJECT_NAME(f.referenced_object_id) AS referenced_table,
       rc.name AS referenced_column, f.is_disabled, f.is_not_trusted,
       f.delete_referential_action_desc, f.update_referential_action_desc
FROM sys.foreign_keys f
JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id
JOIN sys.columns pc ON pc.object_id=fc.parent_object_id AND pc.column_id=fc.parent_column_id
JOIN sys.columns rc ON rc.object_id=fc.referenced_object_id AND rc.column_id=fc.referenced_column_id
WHERE OBJECT_SCHEMA_NAME(f.parent_object_id)=N'dbo'
  AND (OBJECT_NAME(f.parent_object_id) IN (N'membership',N'payment',N'membership_upgrade',N'membership_cancellation_request',N'audit_log')
       OR OBJECT_NAME(f.referenced_object_id) IN (N'membership',N'payment',N'membership_renewal_intent'))
ORDER BY parent_table,f.name,fc.constraint_column_id;

-- Existing indexes and exact keys/filters; retain established indexes unchanged.
SELECT OBJECT_NAME(i.object_id) AS table_name,i.name AS index_name,i.is_unique,
       i.is_primary_key,i.is_disabled,i.has_filter,i.filter_definition,
       ic.key_ordinal,c.name AS key_column
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id IN (OBJECT_ID(N'dbo.membership'),OBJECT_ID(N'dbo.membership_plan'),
                       OBJECT_ID(N'dbo.payment'),OBJECT_ID(N'dbo.membership_upgrade'),
                       OBJECT_ID(N'dbo.membership_cancellation_request'))
  AND i.index_id>0 AND ic.is_included_column=0
ORDER BY table_name,index_name,ic.key_ordinal;

-- Baseline counts; keep the result set with the approved backup/rehearsal record.
SELECT N'Membership' AS entity, status AS state, COUNT_BIG(*) AS row_count
FROM dbo.membership GROUP BY status
UNION ALL
SELECT N'Payment:MEMBERSHIP/UPGRADE',CONCAT(purpose,N'/',status),COUNT_BIG(*)
FROM dbo.payment WHERE purpose IN ('MEMBERSHIP','UPGRADE') GROUP BY purpose,status
UNION ALL
SELECT N'CancellationRequest',status,COUNT_BIG(*)
FROM dbo.membership_cancellation_request GROUP BY status
ORDER BY entity,state;

-- Plans with current terms; current Plan values are not historical snapshots.
SELECT id,name,status,duration_months,price,course_discount_percent,
       personal_training_discount_percent,allow_personal_training_plan,allow_one_on_one_coaching
FROM dbo.membership_plan ORDER BY id;

-- Date/status inventory and duration review, with no personal names/contact details.
SELECT m.id AS membership_id,m.member_id,m.plan_id,mp.duration_months AS current_plan_months,
       m.start_date,m.end_date,m.status,m.auto_renew,m.purchase_payment_id,
       p.purpose AS linked_payment_purpose,p.status AS linked_payment_status,
       p.amount AS linked_payment_amount,p.paid_at,p.refunded_at,p.refunded_by,
       CASE WHEN m.start_date>@businessDate THEN 1 ELSE 0 END AS future_start,
       CASE WHEN m.status='ACTIVE' AND m.start_date<=@businessDate AND m.end_date>@businessDate THEN 1 ELSE 0 END AS effective_active,
       CASE WHEN m.end_date<>DATEADD(month,mp.duration_months,m.start_date) THEN 1 ELSE 0 END AS differs_from_current_plan_duration
FROM dbo.membership m
JOIN dbo.membership_plan mp ON mp.id=m.plan_id
LEFT JOIN dbo.payment p ON p.id=m.purchase_payment_id
ORDER BY m.member_id,m.start_date,m.id;

-- Membership attempts by state and refund fields. No note text or user PII.
SELECT purpose,status,method,COUNT_BIG(*) AS row_count,
       SUM(CASE WHEN refunded_at IS NOT NULL THEN 1 ELSE 0 END) AS has_refund_time,
       SUM(CASE WHEN refunded_by IS NOT NULL THEN 1 ELSE 0 END) AS has_refund_actor
FROM dbo.payment WHERE purpose IN ('MEMBERSHIP','UPGRADE')
GROUP BY purpose,status,method ORDER BY purpose,status,method;

-- Cancellation/refund reconciliation for future or cancelled Memberships.
SELECT r.id AS cancellation_request_id,r.membership_id,r.member_id,r.status AS request_status,
       r.requested_at,r.decided_at,m.status AS membership_status,m.start_date,m.end_date,
       m.purchase_payment_id,p.status AS payment_status,p.amount,p.paid_at,p.refunded_at,p.refunded_by
FROM dbo.membership_cancellation_request r
JOIN dbo.membership m ON m.id=r.membership_id
LEFT JOIN dbo.payment p ON p.id=m.purchase_payment_id
WHERE r.status='PENDING' OR m.status='CANCELLED' OR m.start_date>@businessDate
ORDER BY r.id;

-- Payment attempts with no direct Membership purchase link and Upgrade attempt chains.
SELECT p.id AS payment_id,p.member_id,p.purpose,p.reference_id,p.status,p.method,p.amount,
       p.created_at,p.paid_at,p.refunded_at,p.refunded_by,
       (SELECT COUNT_BIG(*) FROM dbo.membership m WHERE m.purchase_payment_id=p.id) AS linked_membership_count,
       (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade u WHERE u.payment_id=p.id) AS linked_upgrade_attempt_count
FROM dbo.payment p
WHERE p.purpose IN ('MEMBERSHIP','UPGRADE')
  AND (p.status IN ('PENDING','REFUND_REQUIRED','REFUNDED')
       OR NOT EXISTS (SELECT 1 FROM dbo.membership m WHERE m.purchase_payment_id=p.id)
          AND NOT EXISTS (SELECT 1 FROM dbo.membership_upgrade u WHERE u.payment_id=p.id))
ORDER BY p.id;

-- Migration gate for the proposed one-successful-result-per-source Upgrade index.
-- Any returned row requires owner review; this is not an automatic data repair.
WITH successful_upgrade_results AS (
    SELECT u.id AS upgrade_id,u.source_membership_id,u.payment_id,u.result_membership_id,
           COUNT_BIG(*) OVER (PARTITION BY u.source_membership_id) AS result_count
    FROM dbo.membership_upgrade u
    WHERE u.result_membership_id IS NOT NULL
)
SELECT upgrade_id,source_membership_id,payment_id,result_membership_id,result_count
FROM successful_upgrade_results
WHERE result_count > 1
ORDER BY source_membership_id,upgrade_id;

-- Successful Upgrade Payments that have no corresponding Upgrade attempt/result.
SELECT p.id AS payment_id,p.member_id,p.reference_id,p.status,p.amount,p.paid_at
FROM dbo.payment p
LEFT JOIN dbo.membership_upgrade u ON u.payment_id=p.id
WHERE p.purpose='UPGRADE' AND p.status='SUCCESS'
  AND (u.id IS NULL OR u.result_membership_id IS NULL)
ORDER BY p.id;

-- Potentially unresolved/contradictory refund state; empty result is not proof of provider settlement.
SELECT id AS payment_id,member_id,purpose,status,paid_at,refunded_at,refunded_by
FROM dbo.payment
WHERE purpose IN ('MEMBERSHIP','UPGRADE')
  AND ((status='REFUNDED' AND (refunded_at IS NULL OR refunded_by IS NULL))
       OR (refunded_at IS NOT NULL AND status<>'REFUNDED'))
ORDER BY id;

-- Legacy multi-month rows and upgrade chains to include in owner manifest.
SELECT m.id AS membership_id,m.member_id,m.status,m.start_date,m.end_date,
       m.purchase_payment_id,p.status AS payment_status,p.purpose,p.amount,
       COUNT(u.id) AS upgrade_relation_count
FROM dbo.membership m
LEFT JOIN dbo.payment p ON p.id=m.purchase_payment_id
LEFT JOIN dbo.membership_upgrade u ON u.source_membership_id=m.id OR u.result_membership_id=m.id
WHERE DATEDIFF(day,m.start_date,m.end_date)>45
   OR m.id IN (2,3,2002,3002,1002,1003,4002,4004,5005)
GROUP BY m.id,m.member_id,m.status,m.start_date,m.end_date,m.purchase_payment_id,p.status,p.purpose,p.amount
ORDER BY m.id;
