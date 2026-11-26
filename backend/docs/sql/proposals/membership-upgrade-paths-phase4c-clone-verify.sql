/* Phase 4C authorized clone-only copy. Exact database identity guard is required; do not use on original. */
/* READ-ONLY verification for the Phase 4B Upgrade Path table. */
SET NOCOUNT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo_Phase4C_Integration_20261120';
IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50720, 'Target identity mismatch. Verification stopped.', 1;

IF OBJECT_ID(N'dbo.membership_plan_upgrade', N'U') IS NULL
    THROW 50721, 'Upgrade Path table is missing.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
               AND name=N'fk_membership_plan_upgrade_from')
   OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
                  AND name=N'fk_membership_plan_upgrade_to')
    THROW 50724, 'One or more Upgrade Path foreign keys are missing.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
               AND name=N'uq_membership_plan_upgrade_directed_pair' AND type=N'UQ')
   OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
                  AND name=N'ck_membership_plan_upgrade_not_self')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
                  AND name=N'ix_membership_plan_upgrade_active_source')
    THROW 50725, 'A required Upgrade Path unique key, check, or lookup index is missing.', 1;

SELECT c.name AS column_name, ty.name AS type_name, c.max_length, c.precision, c.scale, c.is_nullable
FROM sys.columns c
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE c.object_id=OBJECT_ID(N'dbo.membership_plan_upgrade')
ORDER BY c.column_id;

SELECT fk.name, OBJECT_NAME(fk.parent_object_id) AS table_name,
       COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS column_name,
       OBJECT_NAME(fk.referenced_object_id) AS referenced_table,
       COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS referenced_column
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
WHERE fk.parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade');

SELECT name, type_desc, is_unique, has_filter, filter_definition
FROM sys.indexes
WHERE object_id=OBJECT_ID(N'dbo.membership_plan_upgrade') AND name IS NOT NULL
ORDER BY name;

SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID(N'dbo.membership_plan_upgrade');

SELECT COUNT_BIG(*) AS configured_path_count,
       SUM(CASE WHEN is_active=1 THEN 1 ELSE 0 END) AS active_path_count
FROM dbo.membership_plan_upgrade;

IF EXISTS (
    SELECT from_plan_id, to_plan_id
    FROM dbo.membership_plan_upgrade
    GROUP BY from_plan_id, to_plan_id HAVING COUNT_BIG(*) > 1
) THROW 50722, 'Duplicate directed Upgrade Path pair found.', 1;

IF EXISTS (SELECT 1 FROM dbo.membership_plan_upgrade WHERE from_plan_id=to_plan_id)
    THROW 50723, 'Self-referential Upgrade Path found.', 1;
