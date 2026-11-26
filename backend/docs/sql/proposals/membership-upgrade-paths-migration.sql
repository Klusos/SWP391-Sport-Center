/*
  DRAFT ONLY — Phase 4B explicit directed Membership Upgrade Paths.
  No SQL in this file has been executed. This is a separate additive migration
  from Phase 2A and must be reviewed, backed up, rehearsed on a clone, and
  separately approved before use.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @lockResult int;

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50710, 'Target identity mismatch. Upgrade Path migration stopped before DDL.', 1;

IF OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade', N'U') IS NULL
    THROW 50711, 'Required Membership tables are missing.', 1;

IF OBJECT_ID(N'dbo.membership_plan_upgrade', N'U') IS NOT NULL
    THROW 50712, 'Upgrade Path table already exists. Inspect deployment state; do not rerun blindly.', 1;

IF COL_LENGTH(N'dbo.membership_upgrade', N'target_plan_price_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'source_period_start_date') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'source_period_end_date') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'credit_valuation_date') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'source_total_days') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'source_unused_days') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_plan_name_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_plan_description_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_duration_months_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'source_paid_value_basis_amount') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_course_discount_percent_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_pt_discount_percent_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_allow_pt_plan_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'target_allow_one_on_one_snapshot') IS NULL
   OR COL_LENGTH(N'dbo.membership_upgrade', N'applied_credit_amount') IS NULL
    THROW 50713, 'Phase 2D frozen Upgrade quote columns are missing.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership_upgrade')
               AND name=N'ux_membership_upgrade_one_success_per_source'
               AND is_unique=1 AND has_filter=1)
    THROW 50714, 'Phase 2D successful-Upgrade filtered unique index is missing.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    EXEC @lockResult = sys.sp_getapplock
        @Resource=N'SportCenter-Membership-Upgrade-Paths-Schema',
        @LockMode=N'Exclusive', @LockOwner=N'Transaction', @LockTimeout=10000;
    IF @lockResult < 0
        THROW 50715, 'Could not acquire the Upgrade Path migration application lock.', 1;

    CREATE TABLE dbo.membership_plan_upgrade (
        id int IDENTITY(1,1) NOT NULL,
        from_plan_id int NOT NULL,
        to_plan_id int NOT NULL,
        is_active bit NOT NULL CONSTRAINT df_membership_plan_upgrade_is_active DEFAULT (1),
        created_at datetime2(6) NOT NULL CONSTRAINT df_membership_plan_upgrade_created_at DEFAULT (SYSUTCDATETIME()),
        updated_at datetime2(6) NOT NULL CONSTRAINT df_membership_plan_upgrade_updated_at DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT pk_membership_plan_upgrade PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_membership_plan_upgrade_from FOREIGN KEY (from_plan_id) REFERENCES dbo.membership_plan(id),
        CONSTRAINT fk_membership_plan_upgrade_to FOREIGN KEY (to_plan_id) REFERENCES dbo.membership_plan(id),
        CONSTRAINT ck_membership_plan_upgrade_not_self CHECK (from_plan_id <> to_plan_id),
        CONSTRAINT uq_membership_plan_upgrade_directed_pair UNIQUE (from_plan_id, to_plan_id)
    );

    CREATE INDEX ix_membership_plan_upgrade_active_source
        ON dbo.membership_plan_upgrade(from_plan_id, is_active, to_plan_id);

    COMMIT TRANSACTION;
    PRINT 'Draft Upgrade Path migration completed. This message does not imply owner approval.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
