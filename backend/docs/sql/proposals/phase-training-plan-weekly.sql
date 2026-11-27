-- Additive Weekly General Training Plan migration for the authorized QA clone only.
-- Target: LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12.
-- Apply only after a verified COPY_ONLY CHECKSUM backup. No historical content is rewritten.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF @@SERVERNAME <> N'LAPTOP-416VBDLK'
    THROW 51401, 'Unexpected SQL Server instance; weekly training plan migration aborted.', 1;
IF DB_NAME() <> N'SportCenterDemo_PTOutcome_8CF1' OR DB_ID() <> 12
    THROW 51402, 'Expected SportCenterDemo_PTOutcome_8CF1 DBID 12; migration aborted.', 1;
IF DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 51403, 'Target database must be ONLINE.', 1;
IF @@TRANCOUNT <> 0
    THROW 51404, 'Run migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult int;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.TrainingPlan.WeeklySchema.DB12',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 51405, 'Could not acquire weekly training plan migration lock.', 1;

    IF OBJECT_ID(N'dbo.training_plan', N'U') IS NULL
       OR OBJECT_ID(N'dbo.training_exercise', N'U') IS NULL
       OR OBJECT_ID(N'dbo.users', N'U') IS NULL
        THROW 51406, 'Required training_plan, training_exercise, or users table is missing.', 1;
    IF OBJECT_ID(N'dbo.training_plan_week') IS NOT NULL
       OR COL_LENGTH(N'dbo.training_exercise', N'week_id') IS NOT NULL
        THROW 51407, 'Weekly schema objects already exist or are partially applied; inspect manually.', 1;
    IF EXISTS (SELECT 1 FROM sys.objects WHERE name IN
        (N'pk_training_plan_week',N'uq_training_plan_week_number',N'uq_training_plan_week_id_plan',
         N'ck_training_plan_week_number',N'fk_training_plan_week_plan',N'fk_training_plan_week_created_by',
         N'fk_training_plan_week_updated_by',N'fk_training_exercise_week',N'fk_training_exercise_week_plan',
         N'ix_training_exercise_week_order'))
        THROW 51408, 'A planned constraint or index name is already occupied; inspect manually.', 1;

    DECLARE @compatibleColumnCount int = (
        SELECT COUNT(*) FROM sys.columns WHERE
            (object_id=OBJECT_ID(N'dbo.training_plan') AND name=N'id'
                AND system_type_id=TYPE_ID(N'int') AND is_identity=1 AND is_nullable=0)
            OR (object_id=OBJECT_ID(N'dbo.training_exercise') AND name=N'id'
                AND system_type_id=TYPE_ID(N'int') AND is_identity=1 AND is_nullable=0)
            OR (object_id=OBJECT_ID(N'dbo.training_exercise') AND name=N'plan_id'
                AND system_type_id=TYPE_ID(N'int') AND is_nullable=0)
    );
    IF @compatibleColumnCount <> 3
        THROW 51409, 'Existing training table shape is not compatible with the reviewed migration.', 1;
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'fk_training_exercise_plan'
          AND (is_disabled=1 OR is_not_trusted=1))
        THROW 51410, 'Existing exercise-to-plan relationship is disabled or untrusted.', 1;

    CREATE TABLE dbo.training_plan_week (
        id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_plan_week PRIMARY KEY,
        plan_id int NOT NULL,
        week_number int NOT NULL,
        title nvarchar(150) NULL,
        goal nvarchar(500) NULL,
        description nvarchar(1000) NULL,
        created_by int NOT NULL,
        updated_by int NOT NULL,
        created_at datetime2(7) NOT NULL,
        updated_at datetime2(7) NOT NULL,
        CONSTRAINT uq_training_plan_week_number UNIQUE (plan_id, week_number),
        CONSTRAINT uq_training_plan_week_id_plan UNIQUE (id, plan_id),
        CONSTRAINT ck_training_plan_week_number CHECK (week_number > 0),
        CONSTRAINT fk_training_plan_week_plan FOREIGN KEY (plan_id) REFERENCES dbo.training_plan(id),
        CONSTRAINT fk_training_plan_week_created_by FOREIGN KEY (created_by) REFERENCES dbo.users(id),
        CONSTRAINT fk_training_plan_week_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.users(id)
    );

    ALTER TABLE dbo.training_exercise ADD week_id int NULL;
    -- The single-column FK satisfies the JPA association; the composite FK additionally
    -- guarantees that an exercise's week belongs to its retained plan_id.
    ALTER TABLE dbo.training_exercise WITH CHECK ADD CONSTRAINT fk_training_exercise_week
        FOREIGN KEY (week_id) REFERENCES dbo.training_plan_week(id);
    ALTER TABLE dbo.training_exercise WITH CHECK ADD CONSTRAINT fk_training_exercise_week_plan
        FOREIGN KEY (week_id, plan_id) REFERENCES dbo.training_plan_week(id, plan_id);
    CREATE INDEX ix_training_exercise_week_order
        ON dbo.training_exercise(week_id, sort_order, id);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
