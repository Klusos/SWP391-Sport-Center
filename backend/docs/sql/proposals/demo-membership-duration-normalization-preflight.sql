/*
  READ-ONLY preflight for the local demo Membership duration review.
  No UPDATE/DELETE/DDL. Guarded for the identity observed during preflight.
  If this database is restored/moved, change the guard only after a new identity check.
*/
SET NOCOUNT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @businessToday date = CONVERT(date,
    SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time');

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
    THROW 50501, 'Target identity differs from the reviewed local SportCenterDemo instance. Stop.', 1;

SELECT CONVERT(sysname, SERVERPROPERTY('ServerName')) AS server_property_name,
       CONVERT(sysname, @@SERVERNAME) AS server_name,
       DB_NAME() AS database_name,
       SCHEMA_NAME() AS default_schema,
       @businessToday AS business_today;

-- Plans: expected result is no rows if every stored plan already uses one month.
SELECT id, name, status, duration_months, price
FROM dbo.membership_plan
WHERE duration_months <> 1
ORDER BY id;

-- Date candidates: end_date is exclusive; DATEADD(month, 1, start_date) mirrors
-- LocalDate.plusMonths(1) calendar-month behavior. This is only a preview.
SELECT m.id AS membership_id,
       m.member_id,
       u.name AS member_name,
       m.plan_id,
       mp.name AS plan_name,
       mp.duration_months,
       m.start_date,
       m.end_date AS current_exclusive_end_date,
       DATEADD(month, 1, m.start_date) AS proposed_exclusive_end_date,
       m.status AS stored_status,
       CASE
           WHEN m.status = 'ACTIVE' AND m.start_date > @businessToday THEN 'QUEUED_BY_FUTURE_START'
           WHEN m.status = 'ACTIVE' AND m.start_date <= @businessToday AND m.end_date > @businessToday THEN 'CURRENTLY_ENTITLED'
           WHEN m.status = 'ACTIVE' AND m.end_date <= @businessToday THEN 'DATE_EXPIRED_BUT_STORED_ACTIVE'
           ELSE m.status
       END AS effective_stage,
       m.auto_renew,
       m.purchase_payment_id,
       purchase.purpose AS purchase_purpose,
       purchase.status AS purchase_payment_status,
       purchase.created_at AS payment_created_at,
       purchase.paid_at,
       (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade u
        WHERE u.source_membership_id = m.id) AS upgrade_source_count,
       (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade u
        WHERE u.result_membership_id = m.id) AS upgrade_result_count
FROM dbo.membership AS m
JOIN dbo.users AS u ON u.id = m.member_id
JOIN dbo.membership_plan AS mp ON mp.id = m.plan_id
LEFT JOIN dbo.payment AS purchase ON purchase.id = m.purchase_payment_id
WHERE m.end_date <> DATEADD(month, 1, m.start_date)
ORDER BY m.member_id, m.start_date, m.id;

-- Upgrade relationships for every candidate; successful upgrade result periods
-- intentionally inherit the source period's remaining end date.
SELECT mu.id AS upgrade_id,
       mu.source_membership_id,
       mu.result_membership_id,
       mu.target_plan_id,
       mu.remaining_days,
       mu.payment_id,
       p.status AS payment_status,
       p.created_at AS payment_created_at,
       p.paid_at
FROM dbo.membership_upgrade AS mu
JOIN dbo.payment AS p ON p.id = mu.payment_id
WHERE mu.source_membership_id IN (
          SELECT id FROM dbo.membership WHERE end_date <> DATEADD(month, 1, start_date))
   OR mu.result_membership_id IN (
          SELECT id FROM dbo.membership WHERE end_date <> DATEADD(month, 1, start_date))
ORDER BY mu.id;

-- Unlinked successful membership payments on the same member and plan are
-- surfaced as ambiguous evidence; do not assume they belong to a candidate.
SELECT m.id AS candidate_membership_id,
       m.member_id,
       m.plan_id,
       p.id AS unlinked_payment_id,
       p.status AS payment_status,
       p.created_at AS payment_created_at,
       p.paid_at
FROM dbo.membership AS m
JOIN dbo.payment AS p
  ON p.member_id = m.member_id
 AND p.purpose = 'MEMBERSHIP'
 AND p.reference_id = m.plan_id
WHERE m.end_date <> DATEADD(month, 1, m.start_date)
  AND m.purchase_payment_id IS NULL
  AND p.status IN ('SUCCESS', 'REFUND_REQUIRED', 'REFUNDED')
ORDER BY m.id, p.created_at, p.id;

-- Pending Membership/Upgrade payments for affected members.
SELECT p.id AS payment_id,
       p.member_id,
       p.purpose,
       p.reference_id,
       p.status,
       p.created_at,
       p.paid_at
FROM dbo.payment AS p
WHERE p.status = 'PENDING'
  AND p.purpose IN ('MEMBERSHIP', 'UPGRADE')
  AND p.member_id IN (
      SELECT DISTINCT member_id
      FROM dbo.membership
      WHERE end_date <> DATEADD(month, 1, start_date))
ORDER BY p.member_id, p.created_at, p.id;

-- Future ACTIVE rows are queued periods even though the stored status is ACTIVE.
SELECT m.id AS queued_membership_id,
       m.member_id,
       m.plan_id,
       m.start_date,
       m.end_date,
       m.auto_renew,
       m.purchase_payment_id,
       p.status AS purchase_payment_status,
       p.created_at AS payment_created_at,
       p.paid_at
FROM dbo.membership AS m
LEFT JOIN dbo.payment AS p ON p.id = m.purchase_payment_id
WHERE m.status = 'ACTIVE'
  AND m.start_date > @businessToday
  AND m.member_id IN (
      SELECT DISTINCT member_id
      FROM dbo.membership
      WHERE end_date <> DATEADD(month, 1, start_date))
ORDER BY m.member_id, m.start_date, m.id;
