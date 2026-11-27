-- Read-only validation for the Weekly General Training Plan migration.
-- Confirm server/database identity before running.
IF @@SERVERNAME <> N'LAPTOP-416VBDLK'
    THROW 51421, 'Unexpected SQL Server instance.', 1;
IF DB_NAME() <> N'SportCenterDemo_PTOutcome_8CF1' OR DB_ID() <> 12
    THROW 51422, 'Expected SportCenterDemo_PTOutcome_8CF1 DBID 12.', 1;
SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name, DB_ID() AS database_id,
       DATABASEPROPERTYEX(DB_NAME(), 'Status') AS database_status;
SELECT COUNT_BIG(*) AS training_plans FROM dbo.training_plan;
SELECT COUNT_BIG(*) AS training_exercises FROM dbo.training_exercise;
SELECT COUNT_BIG(*) AS historical_exercises_still_unassigned FROM dbo.training_exercise WHERE week_id IS NULL;
SELECT COUNT_BIG(*) AS weekly_sections FROM dbo.training_plan_week;
SELECT COUNT_BIG(*) AS cross_plan_week_references
FROM dbo.training_exercise e JOIN dbo.training_plan_week w ON w.id=e.week_id
WHERE e.plan_id<>w.plan_id;
SELECT fk.name, fk.is_disabled, fk.is_not_trusted
FROM sys.foreign_keys fk
WHERE fk.name IN (N'fk_training_plan_week_plan',N'fk_training_plan_week_created_by',
 N'fk_training_plan_week_updated_by',N'fk_training_exercise_week',N'fk_training_exercise_week_plan')
ORDER BY fk.name;
DBCC CHECKCONSTRAINTS (N'dbo.training_plan_week') WITH ALL_CONSTRAINTS;
DBCC CHECKCONSTRAINTS (N'dbo.training_exercise') WITH ALL_CONSTRAINTS;
