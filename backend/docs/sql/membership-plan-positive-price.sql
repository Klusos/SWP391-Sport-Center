/* Owner-approved migration draft. NOT EXECUTED.
   Requires separate explicit approval before applying to DB5.
   Take and verify a database backup before execution. */
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF @@SERVERNAME <> N'LAPTOP-416VBDLK' OR DB_NAME() <> N'SportCenterDemo' OR DB_ID() <> 5
    THROW 51410, 'Wrong target. Expected LAPTOP-416VBDLK / SportCenterDemo / DBID 5.', 1;
IF DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 51411, 'SportCenterDemo must be ONLINE.', 1;
IF OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL
    THROW 51412, 'dbo.membership_plan is missing.', 1;
IF EXISTS (SELECT 1 FROM dbo.membership_plan WHERE price IS NULL OR price <= 0)
    THROW 51413, 'Non-positive Membership Plan prices exist; review them without automatic data changes.', 1;

BEGIN TRANSACTION;
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan')
             AND name = N'ck_membership_plan_price_positive')
BEGIN
    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan')
                 AND name = N'ck_membership_plan_price_positive'
                 AND (is_disabled = 1 OR is_not_trusted = 1
                      OR definition NOT LIKE N'%price%>%0%'))
        THROW 51414, 'Constraint name exists with incompatible, disabled, or untrusted definition.', 1;
END
ELSE
BEGIN
    ALTER TABLE dbo.membership_plan WITH CHECK
      ADD CONSTRAINT ck_membership_plan_price_positive CHECK (price > 0);
    ALTER TABLE dbo.membership_plan CHECK CONSTRAINT ck_membership_plan_price_positive;
END;
COMMIT TRANSACTION;

SELECT DB_NAME() AS database_name, DB_ID() AS database_id, name, is_disabled, is_not_trusted, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan')
  AND name = N'ck_membership_plan_price_positive';
