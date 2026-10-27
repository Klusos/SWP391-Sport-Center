-- SQL Server 2022+ reviewed additive migration for Flow 4 Training.
-- Run as one standalone batch against the explicitly approved isolated SportCenterDB.
-- Creates only dbo.training_plan, dbo.training_exercise, dbo.training_result,
-- and dbo.progress_evaluation. Existing application data/tables are never altered.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) <> N'sportcenter-recovery-sql'
    THROW 51001, 'Unexpected SQL Server instance. Expected sportcenter-recovery-sql.', 1;
IF @@SERVERNAME <> N'sportcenter-recovery-sql'
    THROW 51002, 'Unexpected @@SERVERNAME. Expected sportcenter-recovery-sql.', 1;
IF DB_NAME() <> N'SportCenterDB'
    THROW 51003, 'Unexpected database. Select SportCenterDB before running the Flow 4 Training migration.', 1;
IF DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 51004, 'SportCenterDB must be ONLINE.', 1;
IF @@TRANCOUNT <> 0
    THROW 51005, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult int;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase10.1.Flow4Training.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 51006, 'Could not acquire the Flow 4 migration application lock.', 1;

    IF OBJECT_ID(N'dbo.class', N'U') IS NULL OR OBJECT_ID(N'dbo.class_session', N'U') IS NULL
       OR OBJECT_ID(N'dbo.users', N'U') IS NULL
        THROW 51007, 'Prerequisite tables dbo.class, dbo.class_session and dbo.users are required.', 1;

    -- FK targets must be ordinary INT IDENTITY primary keys, not merely same-named columns.
    IF EXISTS (
        SELECT v.table_name FROM (VALUES (N'class'), (N'class_session'), (N'users')) v(table_name)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.columns c
            WHERE c.object_id = OBJECT_ID(N'dbo.' + v.table_name) AND c.name COLLATE DATABASE_DEFAULT = N'id' COLLATE DATABASE_DEFAULT
              AND c.user_type_id = TYPE_ID(N'int') AND c.system_type_id = TYPE_ID(N'int')
              AND c.max_length = 4 AND c.precision = 10 AND c.scale = 0
              AND c.is_nullable = 0 AND c.is_identity = 1
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.key_constraints kc
            JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE kc.parent_object_id = OBJECT_ID(N'dbo.' + v.table_name)
              AND kc.type = N'PK' AND ic.key_ordinal = 1 AND c.name COLLATE DATABASE_DEFAULT = N'id' COLLATE DATABASE_DEFAULT
              AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
                   AND x.index_id = kc.unique_index_id AND x.key_ordinal > 0) = 1
        )
    ) THROW 51008, 'A prerequisite id column is not the expected INT IDENTITY primary key.', 1;

    IF (OBJECT_ID(N'dbo.training_plan') IS NOT NULL AND OBJECT_ID(N'dbo.training_plan', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.training_exercise') IS NOT NULL AND OBJECT_ID(N'dbo.training_exercise', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.training_result') IS NOT NULL AND OBJECT_ID(N'dbo.training_result', N'U') IS NULL)
       OR (OBJECT_ID(N'dbo.progress_evaluation') IS NOT NULL AND OBJECT_ID(N'dbo.progress_evaluation', N'U') IS NULL)
        THROW 51009, 'A Flow 4 object name is occupied by a non-table object.', 1;

    DECLARE @tableCount int =
        (SELECT COUNT(*) FROM (VALUES
            (OBJECT_ID(N'dbo.training_plan', N'U')),
            (OBJECT_ID(N'dbo.training_exercise', N'U')),
            (OBJECT_ID(N'dbo.training_result', N'U')),
            (OBJECT_ID(N'dbo.progress_evaluation', N'U'))
        ) v(object_id) WHERE object_id IS NOT NULL);
    IF @tableCount NOT IN (0, 4)
        THROW 51010, 'Partial Flow 4 schema detected. No repair was attempted; inspect manually.', 1;

    IF @tableCount = 0
    BEGIN
        CREATE TABLE dbo.training_plan (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_plan PRIMARY KEY,
            class_id int NOT NULL,
            goal nvarchar(500) NOT NULL,
            description nvarchar(2000) NULL,
            updated_by int NOT NULL,
            created_at datetime2(7) NOT NULL,
            updated_at datetime2(7) NOT NULL,
            CONSTRAINT uq_training_plan_class UNIQUE (class_id),
            CONSTRAINT fk_training_plan_class FOREIGN KEY (class_id) REFERENCES dbo.class(id),
            CONSTRAINT fk_training_plan_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_training_plan_goal CHECK (LEN(goal) > 0)
        );
        CREATE TABLE dbo.training_exercise (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_exercise PRIMARY KEY,
            plan_id int NOT NULL,
            name nvarchar(150) NOT NULL,
            sets int NULL, reps int NULL, duration_minutes int NULL,
            notes nvarchar(500) NULL, sort_order int NOT NULL,
            CONSTRAINT fk_training_exercise_plan FOREIGN KEY (plan_id) REFERENCES dbo.training_plan(id),
            CONSTRAINT ck_training_exercise_values CHECK (
                LEN(name) > 0 AND sort_order >= 0
                AND (sets IS NULL OR sets BETWEEN 1 AND 100)
                AND (reps IS NULL OR reps BETWEEN 1 AND 1000)
                AND (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 240))
        );
        CREATE TABLE dbo.training_result (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_training_result PRIMARY KEY,
            class_session_id int NOT NULL, member_id int NOT NULL, score int NOT NULL,
            notes nvarchar(1000) NULL, recorded_by int NOT NULL, recorded_at datetime2(7) NOT NULL,
            CONSTRAINT uq_training_result_session_member UNIQUE (class_session_id, member_id),
            CONSTRAINT fk_training_result_session FOREIGN KEY (class_session_id) REFERENCES dbo.class_session(id),
            CONSTRAINT fk_training_result_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_training_result_recorded_by FOREIGN KEY (recorded_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_training_result_score CHECK (score BETWEEN 1 AND 10)
        );
        CREATE TABLE dbo.progress_evaluation (
            id int IDENTITY(1,1) NOT NULL CONSTRAINT pk_progress_evaluation PRIMARY KEY,
            class_id int NOT NULL, member_id int NOT NULL, rating int NOT NULL,
            comment nvarchar(1000) NOT NULL, evaluated_by int NOT NULL, evaluated_at datetime2(7) NOT NULL,
            CONSTRAINT fk_progress_evaluation_class FOREIGN KEY (class_id) REFERENCES dbo.class(id),
            CONSTRAINT fk_progress_evaluation_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_progress_evaluation_evaluated_by FOREIGN KEY (evaluated_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_progress_evaluation_values CHECK (rating BETWEEN 1 AND 5 AND LEN(comment) > 0)
        );
        CREATE INDEX ix_training_exercise_plan_order ON dbo.training_exercise(plan_id, sort_order, id);
        CREATE INDEX ix_training_result_member ON dbo.training_result(member_id);
        CREATE INDEX ix_progress_evaluation_class_member
            ON dbo.progress_evaluation(class_id, member_id, evaluated_at DESC, id DESC);
        CREATE INDEX ix_progress_evaluation_member ON dbo.progress_evaluation(member_id, evaluated_at DESC);
    END;

    -- Exact column inventory: rejects missing/extra columns and any type, length, precision,
    -- scale, nullability, or identity mismatch. SQL Server metadata values are explicit.
    DECLARE @expectedColumns table (
        table_name sysname COLLATE DATABASE_DEFAULT, column_name sysname COLLATE DATABASE_DEFAULT, type_id int, max_length smallint,
        precision_value tinyint, scale_value tinyint, nullable bit, identity_column bit
    );
    INSERT @expectedColumns VALUES
    (N'training_plan',N'id',56,4,10,0,0,1),(N'training_plan',N'class_id',56,4,10,0,0,0),
    (N'training_plan',N'goal',231,1000,0,0,0,0),(N'training_plan',N'description',231,4000,0,0,1,0),
    (N'training_plan',N'updated_by',56,4,10,0,0,0),(N'training_plan',N'created_at',42,8,27,7,0,0),
    (N'training_plan',N'updated_at',42,8,27,7,0,0),
    (N'training_exercise',N'id',56,4,10,0,0,1),(N'training_exercise',N'plan_id',56,4,10,0,0,0),
    (N'training_exercise',N'name',231,300,0,0,0,0),(N'training_exercise',N'sets',56,4,10,0,1,0),
    (N'training_exercise',N'reps',56,4,10,0,1,0),(N'training_exercise',N'duration_minutes',56,4,10,0,1,0),
    (N'training_exercise',N'notes',231,1000,0,0,1,0),(N'training_exercise',N'sort_order',56,4,10,0,0,0),
    (N'training_result',N'id',56,4,10,0,0,1),(N'training_result',N'class_session_id',56,4,10,0,0,0),
    (N'training_result',N'member_id',56,4,10,0,0,0),(N'training_result',N'score',56,4,10,0,0,0),
    (N'training_result',N'notes',231,2000,0,0,1,0),(N'training_result',N'recorded_by',56,4,10,0,0,0),
    (N'training_result',N'recorded_at',42,8,27,7,0,0),
    (N'progress_evaluation',N'id',56,4,10,0,0,1),(N'progress_evaluation',N'class_id',56,4,10,0,0,0),
    (N'progress_evaluation',N'member_id',56,4,10,0,0,0),(N'progress_evaluation',N'rating',56,4,10,0,0,0),
    (N'progress_evaluation',N'comment',231,2000,0,0,0,0),(N'progress_evaluation',N'evaluated_by',56,4,10,0,0,0),
    (N'progress_evaluation',N'evaluated_at',42,8,27,7,0,0);

    IF EXISTS (
        SELECT 1 FROM @expectedColumns e
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.' + e.table_name) AND c.name COLLATE DATABASE_DEFAULT = e.column_name COLLATE DATABASE_DEFAULT
        WHERE c.column_id IS NULL OR c.user_type_id <> e.type_id OR c.system_type_id <> e.type_id
           OR c.max_length <> e.max_length OR c.precision <> e.precision_value OR c.scale <> e.scale_value
           OR c.is_nullable <> e.nullable OR c.is_identity <> e.identity_column
    ) OR EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id IN (OBJECT_ID(N'dbo.training_plan'), OBJECT_ID(N'dbo.training_exercise'),
                              OBJECT_ID(N'dbo.training_result'), OBJECT_ID(N'dbo.progress_evaluation'))
          AND NOT EXISTS (SELECT 1 FROM @expectedColumns e
                          WHERE e.table_name = OBJECT_NAME(c.object_id) AND e.column_name = c.name)
    ) THROW 51011, 'Flow 4 table column metadata differs from the reviewed mapping.', 1;

    -- IDENTITY seed/increment are part of the JPA-generated key contract.
    IF EXISTS (
        SELECT 1 FROM (VALUES (N'training_plan'),(N'training_exercise'),(N'training_result'),(N'progress_evaluation')) t(table_name)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.identity_columns i
            WHERE i.object_id=OBJECT_ID(N'dbo.'+t.table_name) AND i.name=N'id'
              AND CONVERT(decimal(38,0),i.seed_value)=1 AND CONVERT(decimal(38,0),i.increment_value)=1
        )
    ) THROW 51012, 'A Flow 4 identity column has an incompatible seed or increment.', 1;

    -- Six named key constraints must have exactly the reviewed ordered columns.
    DECLARE @expectedKeys table (constraint_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT, key_type char(2), key_columns nvarchar(100) COLLATE DATABASE_DEFAULT);
    INSERT @expectedKeys VALUES
    (N'pk_training_plan',N'training_plan','PK',N'id'),
    (N'uq_training_plan_class',N'training_plan','UQ',N'class_id'),
    (N'pk_training_exercise',N'training_exercise','PK',N'id'),
    (N'pk_training_result',N'training_result','PK',N'id'),
    (N'uq_training_result_session_member',N'training_result','UQ',N'class_session_id,member_id'),
    (N'pk_progress_evaluation',N'progress_evaluation','PK',N'id');
    IF (SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id IN
        (OBJECT_ID(N'dbo.training_plan'),OBJECT_ID(N'dbo.training_exercise'),OBJECT_ID(N'dbo.training_result'),OBJECT_ID(N'dbo.progress_evaluation'))) <> 6
       OR EXISTS (
        SELECT 1 FROM @expectedKeys e
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.key_constraints kc
            WHERE kc.name COLLATE DATABASE_DEFAULT=e.constraint_name COLLATE DATABASE_DEFAULT AND kc.parent_object_id=OBJECT_ID(N'dbo.'+e.table_name)
              AND kc.type COLLATE DATABASE_DEFAULT=e.key_type COLLATE DATABASE_DEFAULT
              AND (SELECT STRING_AGG(CONVERT(nvarchar(max),c.name COLLATE DATABASE_DEFAULT),N',' COLLATE DATABASE_DEFAULT) WITHIN GROUP (ORDER BY ic.key_ordinal)
                   FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
                   WHERE ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id AND ic.key_ordinal>0)=e.key_columns
              AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=kc.parent_object_id
                   AND ic.index_id=kc.unique_index_id AND ic.key_ordinal>0)=
                  (SELECT COUNT(*) FROM STRING_SPLIT(e.key_columns,N','))
        )
       ) THROW 51013, 'A Flow 4 primary/unique constraint has an incompatible key definition.', 1;

    -- Compare every CHECK against a small set of exact catalog renderings. Only brackets,
    -- whitespace, and SQL Server's parenthesized numeric literals are normalized; internal
    -- parentheses and AND/OR grouping are preserved byte-for-byte after that normalization.
    DECLARE @expectedChecks table (constraint_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT, expression_text nvarchar(1000) COLLATE DATABASE_DEFAULT);
    INSERT @expectedChecks VALUES
    (N'ck_training_plan_goal',N'training_plan',N'len(goal)>0'),
    (N'ck_training_plan_goal',N'training_plan',N'(len(goal)>0)'),
    (N'ck_training_exercise_values',N'training_exercise',N'(len(name)>0andsort_order>=0and(setsisnullorsets>=1andsets<=100)and(repsisnullorreps>=1andreps<=1000)and(duration_minutesisnullorduration_minutes>=1andduration_minutes<=240))'),
    (N'ck_training_result_score',N'training_result',N'(score>=1andscore<=10)'),
    (N'ck_progress_evaluation_values',N'progress_evaluation',N'(rating>=1andrating<=5andlen(comment)>0)'),
    (N'ck_training_exercise_values',N'training_exercise',N'len(name)>0andsort_order>=0and(setsisnullorsetsbetween1and100)and(repsisnullorrepsbetween1and1000)and(duration_minutesisnullorduration_minutesbetween1and240)'),
    (N'ck_training_exercise_values',N'training_exercise',N'(len(name)>0andsort_order>=0and(setsisnullorsetsbetween1and100)and(repsisnullorrepsbetween1and1000)and(duration_minutesisnullorduration_minutesbetween1and240))'),
    (N'ck_training_result_score',N'training_result',N'scorebetween1and10'),
    (N'ck_training_result_score',N'training_result',N'(scorebetween1and10)'),
    (N'ck_progress_evaluation_values',N'progress_evaluation',N'ratingbetween1and5andlen(comment)>0'),
    (N'ck_progress_evaluation_values',N'progress_evaluation',N'(ratingbetween1and5andlen(comment)>0)');
    DECLARE @actualChecks table (constraint_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT, expression_text nvarchar(1000) COLLATE DATABASE_DEFAULT, disabled bit, untrusted bit);
    INSERT @actualChecks SELECT cc.name, OBJECT_NAME(cc.parent_object_id), cc.definition, cc.is_disabled, cc.is_not_trusted
      FROM sys.check_constraints cc WHERE cc.parent_object_id IN
       (OBJECT_ID(N'dbo.training_plan'),OBJECT_ID(N'dbo.training_exercise'),OBJECT_ID(N'dbo.training_result'),OBJECT_ID(N'dbo.progress_evaluation'));
    UPDATE @actualChecks SET expression_text=LOWER(expression_text);
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'[',N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N']',N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N' ',N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,NCHAR(9),N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,NCHAR(10),N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,NCHAR(13),N'');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(0)',N'0');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(1)',N'1');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(5)',N'5');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(10)',N'10');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(100)',N'100');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(240)',N'240');
    UPDATE @actualChecks SET expression_text=REPLACE(expression_text,N'(1000)',N'1000');
    IF (SELECT COUNT(*) FROM @actualChecks)<>4
       OR EXISTS (SELECT 1 FROM @actualChecks WHERE disabled=1 OR untrusted=1)
       OR EXISTS (SELECT c.constraint_name FROM (VALUES
            (N'ck_training_plan_goal'),(N'ck_training_exercise_values'),
            (N'ck_training_result_score'),(N'ck_progress_evaluation_values')) c(constraint_name)
          WHERE NOT EXISTS (SELECT 1 FROM @actualChecks a JOIN @expectedChecks e
             ON e.constraint_name=a.constraint_name AND e.table_name=a.table_name AND e.expression_text=a.expression_text
             WHERE a.constraint_name=c.constraint_name))
        THROW 51014, 'A Flow 4 CHECK constraint is missing, disabled, untrusted or has a different expression.', 1;
    -- Named PK/UQ contracts and exact ordered key columns.
    IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.training_plan') AND name=N'pk_training_plan' AND type=N'PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.training_exercise') AND name=N'pk_training_exercise' AND type=N'PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.training_result') AND name=N'pk_training_result' AND type=N'PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.progress_evaluation') AND name=N'pk_progress_evaluation' AND type=N'PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.training_plan') AND name=N'uq_training_plan_class' AND type=N'UQ')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.training_result') AND name=N'uq_training_result_session_member' AND type=N'UQ')
        THROW 51012, 'A required primary or unique constraint is missing or incompatible.', 1;

    DECLARE @expectedFks table (constraint_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT, column_name sysname COLLATE DATABASE_DEFAULT, ref_table sysname COLLATE DATABASE_DEFAULT, ref_column sysname COLLATE DATABASE_DEFAULT);
    INSERT @expectedFks VALUES
    (N'fk_training_plan_class',N'training_plan',N'class_id',N'class',N'id'),
    (N'fk_training_plan_updated_by',N'training_plan',N'updated_by',N'users',N'id'),
    (N'fk_training_exercise_plan',N'training_exercise',N'plan_id',N'training_plan',N'id'),
    (N'fk_training_result_session',N'training_result',N'class_session_id',N'class_session',N'id'),
    (N'fk_training_result_member',N'training_result',N'member_id',N'users',N'id'),
    (N'fk_training_result_recorded_by',N'training_result',N'recorded_by',N'users',N'id'),
    (N'fk_progress_evaluation_class',N'progress_evaluation',N'class_id',N'class',N'id'),
    (N'fk_progress_evaluation_member',N'progress_evaluation',N'member_id',N'users',N'id'),
    (N'fk_progress_evaluation_evaluated_by',N'progress_evaluation',N'evaluated_by',N'users',N'id');
    IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id IN
        (OBJECT_ID(N'dbo.training_plan'),OBJECT_ID(N'dbo.training_exercise'),OBJECT_ID(N'dbo.training_result'),OBJECT_ID(N'dbo.progress_evaluation'))) <> 9
       OR EXISTS (
        SELECT 1 FROM @expectedFks e
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
            JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
            JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
            WHERE fk.name=e.constraint_name AND fk.parent_object_id=OBJECT_ID(N'dbo.'+e.table_name)
              AND fk.referenced_object_id=OBJECT_ID(N'dbo.'+e.ref_table)
              AND pc.name=e.column_name AND rc.name=e.ref_column
              AND fk.is_disabled=0 AND fk.is_not_trusted=0
              AND fk.delete_referential_action=0 AND fk.update_referential_action=0
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id=fk.object_id)=1
        )
       ) THROW 51013, 'A required Flow 4 foreign key is missing, untrusted, disabled, or incompatible.', 1;

    -- Exact index inventory and key ordering; constraint-backed indexes are validated above.
    IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id IN
        (OBJECT_ID(N'dbo.training_plan'),OBJECT_ID(N'dbo.training_exercise'),OBJECT_ID(N'dbo.training_result'),OBJECT_ID(N'dbo.progress_evaluation'))
        AND index_id > 0) <> 10
        THROW 51015, 'Flow 4 index inventory differs from the reviewed contract.', 1;
    DECLARE @expectedIndexes table (index_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT, key_columns nvarchar(200) COLLATE DATABASE_DEFAULT);
    INSERT @expectedIndexes VALUES
    (N'ix_training_exercise_plan_order',N'training_exercise',N'plan_id,sort_order,id'),
    (N'ix_training_result_member',N'training_result',N'member_id'),
    (N'ix_progress_evaluation_class_member',N'progress_evaluation',N'class_id,member_id,evaluated_at DESC,id DESC'),
    (N'ix_progress_evaluation_member',N'progress_evaluation',N'member_id,evaluated_at DESC');
    IF EXISTS (
        SELECT 1 FROM @expectedIndexes e
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            WHERE i.object_id=OBJECT_ID(N'dbo.'+e.table_name) AND i.name COLLATE DATABASE_DEFAULT=e.index_name COLLATE DATABASE_DEFAULT
              AND i.type=2 AND i.is_unique=0 AND i.is_disabled=0 AND i.has_filter=0
              AND (SELECT STRING_AGG(CONVERT(nvarchar(max),
                        CASE WHEN ic.is_descending_key=1 THEN c.name+N' DESC' ELSE c.name END),N',')
                    WITHIN GROUP (ORDER BY ic.key_ordinal)
                   FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
                   WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=e.key_columns
              AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id
                   AND ic.index_id=i.index_id AND ic.key_ordinal>0)=
                  (SELECT COUNT(*) FROM STRING_SPLIT(REPLACE(e.key_columns,N' DESC',N''),N','))
        )
    ) THROW 51016, 'A required Flow 4 non-unique index is missing or incompatible.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;



