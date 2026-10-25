-- SQL Server 2016+ manual migration for Phase 10.1 / Flow 4 Training (plans, exercises,
-- per-session training results, progress evaluations).
-- Back up SportCenterDB and stop application writers before running.
-- Execute as one standalone batch; do not wrap this script in another transaction.
-- Additive and idempotent: it creates the four missing tables and never alters or drops data.
-- Every table is new, so later statements in this batch compile against them through deferred
-- name resolution; no statement references a column added to a pre-existing table.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDB'
    THROW 51001, 'Select SportCenterDB before running the Flow 4 Training migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 51002, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase10.1.Flow4Training.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 51003, 'Could not acquire the Flow 4 Training migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.class', N'U') IS NULL OR OBJECT_ID(N'dbo.class_session', N'U') IS NULL
       OR OBJECT_ID(N'dbo.users', N'U') IS NULL
        THROW 51004, 'Expected dbo.class, dbo.class_session and dbo.users tables.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.class')
                    AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND is_identity = 1)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.class_session')
                      AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND is_identity = 1)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users')
                      AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND is_identity = 1)
        THROW 51005, 'dbo.class.id, dbo.class_session.id and dbo.users.id must be INT IDENTITY keys.', 1;

    IF (OBJECT_ID(N'dbo.training_plan') IS NOT NULL AND OBJECT_ID(N'dbo.training_plan', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.training_exercise') IS NOT NULL AND OBJECT_ID(N'dbo.training_exercise', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.training_result') IS NOT NULL AND OBJECT_ID(N'dbo.training_result', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.progress_evaluation') IS NOT NULL AND OBJECT_ID(N'dbo.progress_evaluation', N'U') IS NULL)
        THROW 51006, 'A Flow 4 Training table name is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.training_plan', N'U') IS NULL
        CREATE TABLE dbo.training_plan (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_plan PRIMARY KEY,
            class_id INT NOT NULL,
            goal NVARCHAR(500) NOT NULL,
            description NVARCHAR(2000) NULL,
            updated_by INT NOT NULL,
            created_at DATETIME2(7) NOT NULL,
            updated_at DATETIME2(7) NOT NULL,
            CONSTRAINT uq_training_plan_class UNIQUE (class_id),
            CONSTRAINT fk_training_plan_class FOREIGN KEY (class_id) REFERENCES dbo.class(id),
            CONSTRAINT fk_training_plan_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_training_plan_goal CHECK (LEN(goal) > 0)
        );

    IF OBJECT_ID(N'dbo.training_exercise', N'U') IS NULL
        CREATE TABLE dbo.training_exercise (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_exercise PRIMARY KEY,
            plan_id INT NOT NULL,
            name NVARCHAR(150) NOT NULL,
            sets INT NULL,
            reps INT NULL,
            duration_minutes INT NULL,
            notes NVARCHAR(500) NULL,
            sort_order INT NOT NULL,
            CONSTRAINT fk_training_exercise_plan FOREIGN KEY (plan_id) REFERENCES dbo.training_plan(id),
            CONSTRAINT ck_training_exercise_values CHECK (
                LEN(name) > 0 AND sort_order >= 0
                AND (sets IS NULL OR sets BETWEEN 1 AND 100)
                AND (reps IS NULL OR reps BETWEEN 1 AND 1000)
                AND (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 240))
        );

    IF OBJECT_ID(N'dbo.training_result', N'U') IS NULL
        CREATE TABLE dbo.training_result (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_result PRIMARY KEY,
            class_session_id INT NOT NULL,
            member_id INT NOT NULL,
            score INT NOT NULL,
            notes NVARCHAR(1000) NULL,
            recorded_by INT NOT NULL,
            recorded_at DATETIME2(7) NOT NULL,
            CONSTRAINT uq_training_result_session_member UNIQUE (class_session_id, member_id),
            CONSTRAINT fk_training_result_session FOREIGN KEY (class_session_id) REFERENCES dbo.class_session(id),
            CONSTRAINT fk_training_result_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_training_result_recorded_by FOREIGN KEY (recorded_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_training_result_score CHECK (score BETWEEN 1 AND 10)
        );

    IF OBJECT_ID(N'dbo.progress_evaluation', N'U') IS NULL
        CREATE TABLE dbo.progress_evaluation (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_progress_evaluation PRIMARY KEY,
            class_id INT NOT NULL,
            member_id INT NOT NULL,
            rating INT NOT NULL,
            comment NVARCHAR(1000) NOT NULL,
            evaluated_by INT NOT NULL,
            evaluated_at DATETIME2(7) NOT NULL,
            CONSTRAINT fk_progress_evaluation_class FOREIGN KEY (class_id) REFERENCES dbo.class(id),
            CONSTRAINT fk_progress_evaluation_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_progress_evaluation_evaluated_by FOREIGN KEY (evaluated_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_progress_evaluation_values CHECK (rating BETWEEN 1 AND 5 AND LEN(comment) > 0)
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.training_exercise')
                   AND name = N'ix_training_exercise_plan_order')
        CREATE INDEX ix_training_exercise_plan_order ON dbo.training_exercise(plan_id, sort_order, id);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.training_result')
                   AND name = N'ix_training_result_member')
        CREATE INDEX ix_training_result_member ON dbo.training_result(member_id);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.progress_evaluation')
                   AND name = N'ix_progress_evaluation_class_member')
        CREATE INDEX ix_progress_evaluation_class_member
            ON dbo.progress_evaluation(class_id, member_id, evaluated_at DESC, id DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.progress_evaluation')
                   AND name = N'ix_progress_evaluation_member')
        CREATE INDEX ix_progress_evaluation_member ON dbo.progress_evaluation(member_id, evaluated_at DESC);

    -- Rerun safety: an existing table must still expose the columns the application maps.
    IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.training_plan')
        AND name IN (N'id', N'class_id', N'goal', N'description', N'updated_by', N'created_at', N'updated_at')) <> 7
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.training_exercise')
        AND name IN (N'id', N'plan_id', N'name', N'sets', N'reps', N'duration_minutes', N'notes', N'sort_order')) <> 8
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.training_result')
        AND name IN (N'id', N'class_session_id', N'member_id', N'score', N'notes', N'recorded_by', N'recorded_at')) <> 7
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.progress_evaluation')
        AND name IN (N'id', N'class_id', N'member_id', N'rating', N'comment', N'evaluated_by', N'evaluated_at')) <> 7
        THROW 51007, 'An existing Flow 4 Training table is missing expected columns; inspect it manually.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
