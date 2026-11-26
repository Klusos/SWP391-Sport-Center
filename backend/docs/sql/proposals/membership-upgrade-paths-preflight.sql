/*
  READ-ONLY PREFLIGHT for the Phase 4B directed Membership Upgrade Path table.
  Draft only; do not execute without separate owner approval.
*/
SET NOCOUNT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';

SELECT CONVERT(sysname, SERVERPROPERTY('ServerName')) AS server_name,
       DB_NAME() AS database_name,
       SCHEMA_NAME() AS schema_name;

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50700, 'Target identity mismatch. Preflight stopped.', 1;

IF OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade', N'U') IS NULL
    THROW 50701, 'Required Membership tables are missing.', 1;

DECLARE @requiredUpgradeQuoteColumns TABLE (column_name sysname NOT NULL PRIMARY KEY);
INSERT INTO @requiredUpgradeQuoteColumns (column_name) VALUES
    (N'target_plan_price_snapshot'),
    (N'source_period_start_date'),
    (N'source_period_end_date'),
    (N'credit_valuation_date'),
    (N'source_total_days'),
    (N'source_unused_days'),
    (N'target_plan_name_snapshot'),
    (N'target_plan_description_snapshot'),
    (N'target_duration_months_snapshot'),
    (N'source_paid_value_basis_amount'),
    (N'target_course_discount_percent_snapshot'),
    (N'target_pt_discount_percent_snapshot'),
    (N'target_allow_pt_plan_snapshot'),
    (N'target_allow_one_on_one_snapshot'),
    (N'applied_credit_amount');

SELECT required.column_name, actual.type_name, actual.max_length, actual.precision, actual.scale,
       CASE WHEN actual.column_id IS NULL THEN N'MISSING' ELSE N'PRESENT' END AS column_state
FROM @requiredUpgradeQuoteColumns required
LEFT JOIN (
    SELECT c.name AS column_name, c.column_id, ty.name AS type_name,
           c.max_length, c.precision, c.scale
    FROM sys.columns c
    JOIN sys.types ty ON ty.user_type_id=c.user_type_id
    WHERE c.object_id=OBJECT_ID(N'dbo.membership_upgrade')
) actual ON actual.column_name=required.column_name
ORDER BY required.column_name;

IF EXISTS (
    SELECT 1
    FROM @requiredUpgradeQuoteColumns required
    LEFT JOIN sys.columns actual
      ON actual.object_id=OBJECT_ID(N'dbo.membership_upgrade')
     AND actual.name=required.column_name
    WHERE actual.column_id IS NULL
) THROW 50702, 'One or more Phase 2D frozen Upgrade quote columns are missing.', 1;

SELECT t.name AS table_name, c.name AS column_name, ty.name AS type_name,
       c.max_length, c.precision, c.scale, c.is_nullable
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id=t.schema_id
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE s.name=N'dbo'
  AND t.name IN (N'membership_plan', N'membership_upgrade')
ORDER BY t.name, c.column_id;

SELECT p.status, COUNT_BIG(*) AS plan_count,
       SUM(CASE WHEN p.price > 0 THEN 1 ELSE 0 END) AS positive_price_count
FROM dbo.membership_plan p
GROUP BY p.status
ORDER BY p.status;

SELECT p.id, p.name, p.status, p.price
FROM dbo.membership_plan p
WHERE p.status='ACTIVE' AND (p.price IS NULL OR p.price <= 0)
ORDER BY p.id;

SELECT CASE WHEN OBJECT_ID(N'dbo.membership_plan_upgrade', N'U') IS NULL
            THEN N'ABSENT (expected before migration)'
            ELSE N'PRESENT (inspect before any migration)' END AS path_table_state;

SELECT u.id AS upgrade_id, u.payment_id, u.source_membership_id, u.result_membership_id,
       p.status AS payment_status, p.purpose
FROM dbo.membership_upgrade u
LEFT JOIN dbo.payment p ON p.id=u.payment_id
WHERE p.purpose='UPGRADE' AND (u.target_plan_id IS NULL OR u.source_membership_id IS NULL);
