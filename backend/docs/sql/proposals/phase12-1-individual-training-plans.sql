-- PROPOSAL ONLY: do not execute until the owner supplies and approves exact target identities.
-- Additive Individual Training Plan schema. Creates only four new dbo tables and their indexes.
-- No existing application table or row is altered.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

-- Values are pinned to the previously owner-verified Docker clone identity.
-- This does not authorize execution; the database must still be explicitly approved for migration.
DECLARE @ExpectedServer nvarchar(128) = N'sportcenter-recovery-sql';
DECLARE @ExpectedMachine nvarchar(128) = N'sportcenter-recovery-sql';
DECLARE @ExpectedDatabase sysname = N'SportCenterPtE2E';

IF CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')))
       <> CONVERT(varbinary(256), @ExpectedServer)
   OR CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')))
       <> CONVERT(varbinary(256), @ExpectedMachine)
   OR SERVERPROPERTY('InstanceName') IS NOT NULL
    THROW 51202, 'Connected SQL Server identity does not match the verified default clone instance.', 1;
IF CONVERT(varbinary(256), DB_NAME()) <> CONVERT(varbinary(256), @ExpectedDatabase)
    THROW 51204, 'Current database does not match the approved database guard; the script will not switch databases.', 1;
IF DATABASEPROPERTYEX(DB_NAME(), 'Status') <> 'ONLINE'
    THROW 51205, 'The approved database must be ONLINE.', 1;
IF @@TRANCOUNT <> 0
    THROW 51206, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LockResult int;
    EXEC @LockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase12.1.IndividualTrainingPlan.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @LockResult < 0
        THROW 51207, 'Could not acquire the Individual Training Plan migration lock.', 1;

    IF OBJECT_ID(N'dbo.class', N'U') IS NULL OR OBJECT_ID(N'dbo.users', N'U') IS NULL
        THROW 51208, 'Prerequisite tables dbo.class and dbo.users are required.', 1;

    IF (OBJECT_ID(N'dbo.individual_training_plan') IS NOT NULL AND OBJECT_ID(N'dbo.individual_training_plan', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.individual_training_plan_revision') IS NOT NULL AND OBJECT_ID(N'dbo.individual_training_plan_revision', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.individual_training_plan_week') IS NOT NULL AND OBJECT_ID(N'dbo.individual_training_plan_week', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.individual_training_plan_exercise') IS NOT NULL AND OBJECT_ID(N'dbo.individual_training_plan_exercise', N'U') IS NULL)
        THROW 51209, 'A proposed table name is already occupied by a non-table object.', 1;

    DECLARE @ExistingTables int = (SELECT COUNT(*) FROM (VALUES
        (OBJECT_ID(N'dbo.individual_training_plan', N'U')),
        (OBJECT_ID(N'dbo.individual_training_plan_revision', N'U')),
        (OBJECT_ID(N'dbo.individual_training_plan_week', N'U')),
        (OBJECT_ID(N'dbo.individual_training_plan_exercise', N'U'))
    ) t(object_id) WHERE object_id IS NOT NULL);
    IF @ExistingTables NOT IN (0, 4)
        THROW 51210, 'Partial Individual Training Plan schema detected. No repair was attempted.', 1;

    IF @ExistingTables = 0
    BEGIN
        CREATE TABLE dbo.individual_training_plan (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_individual_training_plan PRIMARY KEY,
            class_id int NOT NULL,
            member_id int NOT NULL,
            created_by int NOT NULL,
            created_at datetime2(7) NOT NULL,
            updated_at datetime2(7) NOT NULL,
            CONSTRAINT uq_individual_training_plan_class_member UNIQUE (class_id, member_id),
            CONSTRAINT fk_individual_training_plan_class FOREIGN KEY (class_id) REFERENCES dbo.class(id),
            CONSTRAINT fk_individual_training_plan_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_individual_training_plan_created_by FOREIGN KEY (created_by) REFERENCES dbo.users(id)
        );

        CREATE TABLE dbo.individual_training_plan_revision (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_individual_training_plan_revision PRIMARY KEY,
            plan_id int NOT NULL,
            revision_number int NOT NULL,
            goal nvarchar(500) NOT NULL,
            description nvarchar(2000) NULL,
            revised_by int NOT NULL,
            created_at datetime2(7) NOT NULL,
            CONSTRAINT uq_individual_training_revision_number UNIQUE (plan_id, revision_number),
            CONSTRAINT fk_individual_training_revision_plan FOREIGN KEY (plan_id)
                REFERENCES dbo.individual_training_plan(id),
            CONSTRAINT fk_individual_training_revision_user FOREIGN KEY (revised_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_individual_training_revision_values CHECK (revision_number > 0 AND LEN(goal) > 0)
        );

        CREATE TABLE dbo.individual_training_plan_week (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_individual_training_plan_week PRIMARY KEY,
            revision_id int NOT NULL,
            week_number int NOT NULL,
            title nvarchar(150) NULL,
            description nvarchar(1000) NULL,
            CONSTRAINT uq_individual_training_week_number UNIQUE (revision_id, week_number),
            CONSTRAINT fk_individual_training_week_revision FOREIGN KEY (revision_id)
                REFERENCES dbo.individual_training_plan_revision(id),
            CONSTRAINT ck_individual_training_week_number CHECK (week_number >= 1 AND week_number <= 52)
        );

        CREATE TABLE dbo.individual_training_plan_exercise (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_individual_training_plan_exercise PRIMARY KEY,
            week_id int NOT NULL,
            name nvarchar(150) NOT NULL,
            sets int NULL,
            reps int NULL,
            duration_minutes int NULL,
            notes nvarchar(500) NULL,
            sort_order int NOT NULL,
            CONSTRAINT uq_individual_training_exercise_week_order UNIQUE (week_id, sort_order),
            CONSTRAINT fk_individual_training_exercise_week FOREIGN KEY (week_id)
                REFERENCES dbo.individual_training_plan_week(id),
            CONSTRAINT ck_individual_training_exercise_values CHECK (
                LEN(name) > 0
                AND (sets IS NULL OR (sets >= 1 AND sets <= 100))
                AND (reps IS NULL OR (reps >= 1 AND reps <= 1000))
                AND (duration_minutes IS NULL OR (duration_minutes >= 1 AND duration_minutes <= 240))
                AND sort_order >= 0 AND sort_order <= 1000)
        );

        CREATE INDEX ix_individual_training_plan_member_updated
            ON dbo.individual_training_plan(member_id, updated_at DESC, id DESC);
        CREATE INDEX ix_individual_training_plan_class
            ON dbo.individual_training_plan(class_id, id);
        CREATE INDEX ix_individual_training_revision_plan_number
            ON dbo.individual_training_plan_revision(plan_id, revision_number DESC);
        CREATE INDEX ix_individual_training_week_revision_order
            ON dbo.individual_training_plan_week(revision_id, week_number, id);
        CREATE INDEX ix_individual_training_exercise_week_order
            ON dbo.individual_training_plan_exercise(week_id, sort_order, id);
    END
    ELSE
    BEGIN
        -- An all-present schema is a rerun only if its columns and named keys still match.
        DECLARE @ExpectedColumns table (
            table_name sysname, column_name sysname, type_name sysname,
            max_length smallint, precision_value tinyint, scale_value tinyint,
            is_nullable bit, is_identity bit
        );
        INSERT INTO @ExpectedColumns VALUES
            (N'individual_training_plan',N'id',N'int',4,10,0,0,1),
            (N'individual_training_plan',N'class_id',N'int',4,10,0,0,0),
            (N'individual_training_plan',N'member_id',N'int',4,10,0,0,0),
            (N'individual_training_plan',N'created_by',N'int',4,10,0,0,0),
            (N'individual_training_plan',N'created_at',N'datetime2',8,27,7,0,0),
            (N'individual_training_plan',N'updated_at',N'datetime2',8,27,7,0,0),
            (N'individual_training_plan_revision',N'id',N'int',4,10,0,0,1),
            (N'individual_training_plan_revision',N'plan_id',N'int',4,10,0,0,0),
            (N'individual_training_plan_revision',N'revision_number',N'int',4,10,0,0,0),
            (N'individual_training_plan_revision',N'goal',N'nvarchar',1000,0,0,0,0),
            (N'individual_training_plan_revision',N'description',N'nvarchar',4000,0,0,1,0),
            (N'individual_training_plan_revision',N'revised_by',N'int',4,10,0,0,0),
            (N'individual_training_plan_revision',N'created_at',N'datetime2',8,27,7,0,0),
            (N'individual_training_plan_week',N'id',N'int',4,10,0,0,1),
            (N'individual_training_plan_week',N'revision_id',N'int',4,10,0,0,0),
            (N'individual_training_plan_week',N'week_number',N'int',4,10,0,0,0),
            (N'individual_training_plan_week',N'title',N'nvarchar',300,0,0,1,0),
            (N'individual_training_plan_week',N'description',N'nvarchar',2000,0,0,1,0),
            (N'individual_training_plan_exercise',N'id',N'int',4,10,0,0,1),
            (N'individual_training_plan_exercise',N'week_id',N'int',4,10,0,0,0),
            (N'individual_training_plan_exercise',N'name',N'nvarchar',300,0,0,0,0),
            (N'individual_training_plan_exercise',N'sets',N'int',4,10,0,1,0),
            (N'individual_training_plan_exercise',N'reps',N'int',4,10,0,1,0),
            (N'individual_training_plan_exercise',N'duration_minutes',N'int',4,10,0,1,0),
            (N'individual_training_plan_exercise',N'notes',N'nvarchar',1000,0,0,1,0),
            (N'individual_training_plan_exercise',N'sort_order',N'int',4,10,0,0,0);

        IF (SELECT COUNT(*) FROM sys.columns WHERE object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan'), OBJECT_ID(N'dbo.individual_training_plan_revision'),
                OBJECT_ID(N'dbo.individual_training_plan_week'), OBJECT_ID(N'dbo.individual_training_plan_exercise'))) <> 26
           OR EXISTS (
                SELECT 1 FROM @ExpectedColumns e
                LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.' + e.table_name) AND c.name = e.column_name
                WHERE c.column_id IS NULL OR c.user_type_id <> TYPE_ID(e.type_name)
                   OR c.max_length <> e.max_length OR c.precision <> e.precision_value OR c.scale <> e.scale_value
                   OR c.is_nullable <> e.is_nullable OR c.is_identity <> e.is_identity)
            THROW 51211, 'Existing Individual Training Plan columns are incomplete or incompatible.', 1;

        DECLARE @ExpectedForeignKeys table (
            constraint_name sysname, parent_table sysname, parent_column sysname,
            referenced_table sysname, referenced_column sysname
        );
        INSERT INTO @ExpectedForeignKeys VALUES
            (N'fk_individual_training_plan_class',N'individual_training_plan',N'class_id',N'class',N'id'),
            (N'fk_individual_training_plan_member',N'individual_training_plan',N'member_id',N'users',N'id'),
            (N'fk_individual_training_plan_created_by',N'individual_training_plan',N'created_by',N'users',N'id'),
            (N'fk_individual_training_revision_plan',N'individual_training_plan_revision',N'plan_id',N'individual_training_plan',N'id'),
            (N'fk_individual_training_revision_user',N'individual_training_plan_revision',N'revised_by',N'users',N'id'),
            (N'fk_individual_training_week_revision',N'individual_training_plan_week',N'revision_id',N'individual_training_plan_revision',N'id'),
            (N'fk_individual_training_exercise_week',N'individual_training_plan_exercise',N'week_id',N'individual_training_plan_week',N'id');

        IF EXISTS (
            SELECT 1 FROM @ExpectedForeignKeys e
            LEFT JOIN sys.foreign_keys fk ON fk.name = e.constraint_name
            LEFT JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            LEFT JOIN sys.columns parent_column ON parent_column.object_id = fkc.parent_object_id
                AND parent_column.column_id = fkc.parent_column_id
            LEFT JOIN sys.columns referenced_column ON referenced_column.object_id = fkc.referenced_object_id
                AND referenced_column.column_id = fkc.referenced_column_id
            WHERE fk.object_id IS NULL OR fk.parent_object_id <> OBJECT_ID(N'dbo.' + e.parent_table)
                OR fk.referenced_object_id <> OBJECT_ID(N'dbo.' + e.referenced_table)
                OR parent_column.name <> e.parent_column OR referenced_column.name <> e.referenced_column
                OR fk.is_disabled = 1 OR fk.is_not_trusted = 1
                OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
                OR fk.is_not_for_replication = 1
                OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) <> 1
        )
            THROW 51213, 'Existing Individual Training Plan foreign keys are missing, untrusted, or incompatible.', 1;

        DECLARE @ExpectedUniqueColumns table (
            constraint_name sysname, table_name sysname, column_name sysname, key_ordinal int
        );
        INSERT INTO @ExpectedUniqueColumns VALUES
            (N'uq_individual_training_plan_class_member',N'individual_training_plan',N'class_id',1),
            (N'uq_individual_training_plan_class_member',N'individual_training_plan',N'member_id',2),
            (N'uq_individual_training_revision_number',N'individual_training_plan_revision',N'plan_id',1),
            (N'uq_individual_training_revision_number',N'individual_training_plan_revision',N'revision_number',2),
            (N'uq_individual_training_week_number',N'individual_training_plan_week',N'revision_id',1),
            (N'uq_individual_training_week_number',N'individual_training_plan_week',N'week_number',2),
            (N'uq_individual_training_exercise_week_order',N'individual_training_plan_exercise',N'week_id',1),
            (N'uq_individual_training_exercise_week_order',N'individual_training_plan_exercise',N'sort_order',2);

        IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan')
                AND name = N'pk_individual_training_plan' AND type = N'PK')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_revision')
                AND name = N'pk_individual_training_plan_revision' AND type = N'PK')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_week')
                AND name = N'pk_individual_training_plan_week' AND type = N'PK')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_exercise')
                AND name = N'pk_individual_training_plan_exercise' AND type = N'PK')
           OR EXISTS (
                SELECT 1
                FROM (VALUES
                    (N'pk_individual_training_plan',N'individual_training_plan'),
                    (N'pk_individual_training_plan_revision',N'individual_training_plan_revision'),
                    (N'pk_individual_training_plan_week',N'individual_training_plan_week'),
                    (N'pk_individual_training_plan_exercise',N'individual_training_plan_exercise')
                ) expected_pk(constraint_name,table_name)
                LEFT JOIN sys.key_constraints kc ON kc.parent_object_id = OBJECT_ID(N'dbo.' + expected_pk.table_name)
                    AND kc.name = expected_pk.constraint_name AND kc.type = N'PK'
                LEFT JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id
                    AND ic.index_id = kc.unique_index_id AND ic.key_ordinal = 1
                LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE kc.object_id IS NULL OR ic.key_ordinal IS NULL OR c.name <> N'id'
                    OR (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
                        AND x.index_id = kc.unique_index_id AND x.key_ordinal > 0) <> 1
            )
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan')
                AND name = N'uq_individual_training_plan_class_member' AND type = N'UQ')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_revision')
                AND name = N'uq_individual_training_revision_number' AND type = N'UQ')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_week')
                AND name = N'uq_individual_training_week_number' AND type = N'UQ')
           OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_exercise')
                AND name = N'uq_individual_training_exercise_week_order' AND type = N'UQ')
           OR EXISTS (
                SELECT 1 FROM @ExpectedUniqueColumns e
                LEFT JOIN sys.key_constraints kc ON kc.name = e.constraint_name
                    AND kc.parent_object_id = OBJECT_ID(N'dbo.' + e.table_name) AND kc.type = N'UQ'
                LEFT JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id
                    AND ic.index_id = kc.unique_index_id AND ic.key_ordinal = e.key_ordinal
                LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE kc.object_id IS NULL OR c.name <> e.column_name)
           OR EXISTS (
                SELECT 1 FROM sys.key_constraints kc
                WHERE kc.parent_object_id IN (OBJECT_ID(N'dbo.individual_training_plan'),
                    OBJECT_ID(N'dbo.individual_training_plan_revision'), OBJECT_ID(N'dbo.individual_training_plan_week'),
                    OBJECT_ID(N'dbo.individual_training_plan_exercise'))
                    AND kc.type = N'UQ'
                    AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = kc.parent_object_id
                        AND ic.index_id = kc.unique_index_id AND ic.key_ordinal > 0) <> 2)
           OR (SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan'), OBJECT_ID(N'dbo.individual_training_plan_revision'),
                OBJECT_ID(N'dbo.individual_training_plan_week'), OBJECT_ID(N'dbo.individual_training_plan_exercise'))
                AND type = N'UQ') <> 4
           OR (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan'), OBJECT_ID(N'dbo.individual_training_plan_revision'),
                OBJECT_ID(N'dbo.individual_training_plan_week'), OBJECT_ID(N'dbo.individual_training_plan_exercise'))) <> 7
           OR (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan'), OBJECT_ID(N'dbo.individual_training_plan_revision'),
                OBJECT_ID(N'dbo.individual_training_plan_week'), OBJECT_ID(N'dbo.individual_training_plan_exercise'))
                AND is_disabled = 0 AND is_not_trusted = 0) <> 7
           OR (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan_revision'), OBJECT_ID(N'dbo.individual_training_plan_week'),
                OBJECT_ID(N'dbo.individual_training_plan_exercise')) AND is_disabled = 0 AND is_not_trusted = 0) <> 3
           OR (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id IN (
                OBJECT_ID(N'dbo.individual_training_plan_revision'), OBJECT_ID(N'dbo.individual_training_plan_week'),
                OBJECT_ID(N'dbo.individual_training_plan_exercise'))) <> 3
           OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_revision')
                AND name = N'ck_individual_training_revision_values' AND is_disabled = 0 AND is_not_trusted = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_week')
                AND name = N'ck_individual_training_week_number' AND is_disabled = 0 AND is_not_trusted = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.individual_training_plan_exercise')
                AND name = N'ck_individual_training_exercise_values' AND is_disabled = 0 AND is_not_trusted = 0)
           OR EXISTS (
                SELECT 1
                FROM (VALUES
                    (N'ck_individual_training_revision_values',N'individual_training_plan_revision',N'(revision_number>(0)andlen(goal)>(0))'),
                    (N'ck_individual_training_week_number',N'individual_training_plan_week',N'(week_number>=(1)andweek_number<=(52))'),
                    (N'ck_individual_training_exercise_values',N'individual_training_plan_exercise',
                     N'(len(name)>(0)and(setsisnullor(sets>=(1)andsets<=(100)))and(repsisnullor(reps>=(1)andreps<=(1000)))and(duration_minutesisnullor(duration_minutes>=(1)andduration_minutes<=(240)))andsort_order>=(0)andsort_order<=(1000))')
                ) expected(constraint_name,table_name,definition_text)
                LEFT JOIN sys.check_constraints cc ON cc.name = expected.constraint_name
                    AND cc.parent_object_id = OBJECT_ID(N'dbo.' + expected.table_name)
                -- Ignore SQL Server's case, whitespace and identifier-bracket formatting only.
                -- Keep parentheses: removing them can make differently grouped logic look equivalent.
                CROSS APPLY (SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    cc.definition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''), N']', N'')))
                    AS normalized(definition_text)
                WHERE cc.object_id IS NULL OR normalized.definition_text <> expected.definition_text
            )
           OR EXISTS (
                SELECT 1
                FROM (VALUES
                    (N'ix_individual_training_plan_member_updated',N'individual_training_plan',1,N'member_id',0),
                    (N'ix_individual_training_plan_member_updated',N'individual_training_plan',2,N'updated_at',1),
                    (N'ix_individual_training_plan_member_updated',N'individual_training_plan',3,N'id',1),
                    (N'ix_individual_training_plan_class',N'individual_training_plan',1,N'class_id',0),
                    (N'ix_individual_training_plan_class',N'individual_training_plan',2,N'id',0),
                    (N'ix_individual_training_revision_plan_number',N'individual_training_plan_revision',1,N'plan_id',0),
                    (N'ix_individual_training_revision_plan_number',N'individual_training_plan_revision',2,N'revision_number',1),
                    (N'ix_individual_training_week_revision_order',N'individual_training_plan_week',1,N'revision_id',0),
                    (N'ix_individual_training_week_revision_order',N'individual_training_plan_week',2,N'week_number',0),
                    (N'ix_individual_training_week_revision_order',N'individual_training_plan_week',3,N'id',0),
                    (N'ix_individual_training_exercise_week_order',N'individual_training_plan_exercise',1,N'week_id',0),
                    (N'ix_individual_training_exercise_week_order',N'individual_training_plan_exercise',2,N'sort_order',0),
                    (N'ix_individual_training_exercise_week_order',N'individual_training_plan_exercise',3,N'id',0)
                ) e(index_name,table_name,key_ordinal,column_name,is_descending_key)
                LEFT JOIN sys.indexes i ON i.object_id = OBJECT_ID(N'dbo.' + e.table_name)
                    AND i.name = e.index_name
                LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id
                    AND ic.index_id = i.index_id AND ic.key_ordinal = e.key_ordinal
                LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE i.index_id IS NULL OR i.type <> 2 OR i.is_unique = 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1
                    OR i.has_filter = 1 OR ic.key_ordinal IS NULL OR c.name <> e.column_name
                    OR ic.is_descending_key <> e.is_descending_key
                    OR (SELECT COUNT(*) FROM sys.index_columns all_ic
                        WHERE all_ic.object_id = i.object_id AND all_ic.index_id = i.index_id) <>
                       (SELECT COUNT(*) FROM (VALUES
                            (N'ix_individual_training_plan_member_updated',3),
                            (N'ix_individual_training_plan_class',2),
                            (N'ix_individual_training_revision_plan_number',2),
                            (N'ix_individual_training_week_revision_order',3),
                            (N'ix_individual_training_exercise_week_order',3)
                        ) expected_counts(index_name,key_count)
                        WHERE expected_counts.index_name = e.index_name)
            )
           OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.individual_training_plan')
                AND name = N'ix_individual_training_plan_member_updated' AND is_disabled = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.individual_training_plan')
                AND name = N'ix_individual_training_plan_class' AND is_disabled = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.individual_training_plan_revision')
                AND name = N'ix_individual_training_revision_plan_number' AND is_disabled = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.individual_training_plan_week')
                AND name = N'ix_individual_training_week_revision_order' AND is_disabled = 0)
           OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.individual_training_plan_exercise')
                AND name = N'ix_individual_training_exercise_week_order' AND is_disabled = 0)
            THROW 51212, 'Existing Individual Training Plan schema is incomplete or incompatible; no repair was attempted.', 1;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
