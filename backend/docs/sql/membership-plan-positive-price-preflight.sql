/* Read-only preflight for the owner-approved positive Membership Plan price rule.
   This script is guarded for the original DB5 target but performs no mutation. */
SET NOCOUNT ON;
IF @@SERVERNAME <> N'LAPTOP-416VBDLK' OR DB_NAME() <> N'SportCenterDemo' OR DB_ID() <> 5
    THROW 51400, 'Wrong target. Expected LAPTOP-416VBDLK / SportCenterDemo / DBID 5.', 1;
IF DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 51401, 'SportCenterDemo must be ONLINE.', 1;

SELECT N'LAPTOP-416VBDLK' AS expected_server, DB_NAME() AS database_name, DB_ID() AS database_id,
       DATABASEPROPERTYEX(DB_NAME(), 'Status') AS database_state;
SELECT COUNT_BIG(*) AS zero_or_negative_price_plans
FROM dbo.membership_plan
WHERE price IS NULL OR price <= 0;
SELECT name, is_disabled, is_not_trusted, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan')
  AND name = N'ck_membership_plan_price_positive';
