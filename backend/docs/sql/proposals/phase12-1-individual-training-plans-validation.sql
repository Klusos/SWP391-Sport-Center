/*
  Read-only post-migration validation for Phase 12.1.
  Run in SSMS with SportCenterPtE2E explicitly selected as the connection database.
  This script never changes database state. It fails closed on identity mismatch.
*/
SET NOCOUNT ON;

DECLARE @ExpectedServer nvarchar(128) = N'sportcenter-recovery-sql';
DECLARE @ExpectedMachine nvarchar(128) = N'sportcenter-recovery-sql';
DECLARE @ExpectedDatabase sysname = N'SportCenterPtE2E';

IF CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')))
       <> CONVERT(varbinary(256), @ExpectedServer)
   OR CONVERT(varbinary(256), CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')))
       <> CONVERT(varbinary(256), @ExpectedMachine)
   OR SERVERPROPERTY('InstanceName') IS NOT NULL
    THROW 51220, 'Connected SQL Server identity does not match the verified default clone instance.', 1;
IF CONVERT(varbinary(256), DB_NAME()) <> CONVERT(varbinary(256), @ExpectedDatabase)
    THROW 51221, 'Current database does not match SportCenterPtE2E; this script will not switch databases.', 1;
IF CONVERT(nvarchar(60), DATABASEPROPERTYEX(DB_NAME(), 'Status')) COLLATE DATABASE_DEFAULT
       <> N'ONLINE' COLLATE DATABASE_DEFAULT
    THROW 51222, 'SportCenterPtE2E must be ONLINE.', 1;

DECLARE @Issues table (check_name nvarchar(100) COLLATE DATABASE_DEFAULT NOT NULL,
    detail nvarchar(1000) COLLATE DATABASE_DEFAULT NOT NULL);
DECLARE @Tables table (table_name sysname COLLATE DATABASE_DEFAULT PRIMARY KEY);
INSERT INTO @Tables VALUES
    (N'individual_training_plan'), (N'individual_training_plan_revision'),
    (N'individual_training_plan_week'), (N'individual_training_plan_exercise');

INSERT INTO @Issues
SELECT N'table', CONCAT(N'dbo.', t.table_name, N': missing or not a user table')
FROM @Tables t
WHERE OBJECT_ID(N'dbo.' + t.table_name, N'U') IS NULL;

DECLARE @Columns table (
    table_name sysname COLLATE DATABASE_DEFAULT, column_name sysname COLLATE DATABASE_DEFAULT,
    type_name sysname COLLATE DATABASE_DEFAULT,
    max_length smallint, precision_value tinyint, scale_value tinyint,
    is_nullable bit, is_identity bit, identity_seed int NULL, identity_increment int NULL,
    PRIMARY KEY (table_name, column_name)
);
INSERT INTO @Columns VALUES
 (N'individual_training_plan',N'id',N'int',4,10,0,0,1,1,1),
 (N'individual_training_plan',N'class_id',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan',N'member_id',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan',N'created_by',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan',N'created_at',N'datetime2',8,27,7,0,0,NULL,NULL),
 (N'individual_training_plan',N'updated_at',N'datetime2',8,27,7,0,0,NULL,NULL),
 (N'individual_training_plan_revision',N'id',N'int',4,10,0,0,1,1,1),
 (N'individual_training_plan_revision',N'plan_id',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_revision',N'revision_number',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_revision',N'goal',N'nvarchar',1000,0,0,0,0,NULL,NULL),
 (N'individual_training_plan_revision',N'description',N'nvarchar',4000,0,0,1,0,NULL,NULL),
 (N'individual_training_plan_revision',N'revised_by',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_revision',N'created_at',N'datetime2',8,27,7,0,0,NULL,NULL),
 (N'individual_training_plan_week',N'id',N'int',4,10,0,0,1,1,1),
 (N'individual_training_plan_week',N'revision_id',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_week',N'week_number',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_week',N'title',N'nvarchar',300,0,0,1,0,NULL,NULL),
 (N'individual_training_plan_week',N'description',N'nvarchar',2000,0,0,1,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'id',N'int',4,10,0,0,1,1,1),
 (N'individual_training_plan_exercise',N'week_id',N'int',4,10,0,0,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'name',N'nvarchar',300,0,0,0,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'sets',N'int',4,10,0,1,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'reps',N'int',4,10,0,1,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'duration_minutes',N'int',4,10,0,1,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'notes',N'nvarchar',1000,0,0,1,0,NULL,NULL),
 (N'individual_training_plan_exercise',N'sort_order',N'int',4,10,0,0,0,NULL,NULL);

INSERT INTO @Issues
SELECT N'column', CONCAT(N'dbo.', e.table_name, N'.', e.column_name, N': expected ', e.type_name,
    N'(', CASE WHEN e.type_name IN (N'nvarchar',N'varchar') THEN CONVERT(varchar(10), e.max_length / 2)
               WHEN e.type_name = N'datetime2' THEN CONVERT(varchar(10), e.scale_value) ELSE N'' END,
    N') nullable=', e.is_nullable, N' identity=', e.is_identity,
    N'; actual=', COALESCE(ty.name COLLATE DATABASE_DEFAULT, N'<missing>'), N'(', COALESCE(CONVERT(varchar(10), c.max_length), N'?'),
    N') nullable=', COALESCE(CONVERT(varchar(1), c.is_nullable), N'?'),
    N' identity=', COALESCE(CONVERT(varchar(1), c.is_identity), N'?'))
FROM @Columns e
LEFT JOIN sys.tables tb ON tb.schema_id = SCHEMA_ID(N'dbo')
    AND tb.name COLLATE DATABASE_DEFAULT = e.table_name COLLATE DATABASE_DEFAULT
LEFT JOIN sys.columns c ON c.object_id = tb.object_id
    AND c.name COLLATE DATABASE_DEFAULT = e.column_name COLLATE DATABASE_DEFAULT
LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
LEFT JOIN sys.identity_columns idc ON idc.object_id = c.object_id AND idc.column_id = c.column_id
WHERE c.column_id IS NULL OR ty.name COLLATE DATABASE_DEFAULT <> e.type_name COLLATE DATABASE_DEFAULT
   OR c.max_length <> e.max_length
   OR c.precision <> e.precision_value OR c.scale <> e.scale_value
   OR c.is_nullable <> e.is_nullable OR c.is_identity <> e.is_identity
   OR (e.is_identity = 1 AND (CONVERT(int, idc.seed_value) <> e.identity_seed
       OR CONVERT(int, idc.increment_value) <> e.identity_increment));

INSERT INTO @Issues
SELECT N'column set', CONCAT(N'dbo.', tb.name, N': unexpected column ', c.name)
FROM @Tables et JOIN sys.tables tb ON tb.schema_id = SCHEMA_ID(N'dbo')
    AND tb.name COLLATE DATABASE_DEFAULT = et.table_name COLLATE DATABASE_DEFAULT
JOIN sys.columns c ON c.object_id = tb.object_id
LEFT JOIN @Columns expected ON expected.table_name COLLATE DATABASE_DEFAULT = tb.name COLLATE DATABASE_DEFAULT
    AND expected.column_name COLLATE DATABASE_DEFAULT = c.name COLLATE DATABASE_DEFAULT
WHERE expected.column_name IS NULL;

DECLARE @KeyColumns table (
    constraint_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT,
    constraint_type char(2) COLLATE DATABASE_DEFAULT, column_name sysname COLLATE DATABASE_DEFAULT, key_ordinal int,
    PRIMARY KEY (constraint_name, key_ordinal)
);
INSERT INTO @KeyColumns VALUES
 (N'pk_individual_training_plan',N'individual_training_plan','PK',N'id',1),
 (N'pk_individual_training_plan_revision',N'individual_training_plan_revision','PK',N'id',1),
 (N'pk_individual_training_plan_week',N'individual_training_plan_week','PK',N'id',1),
 (N'pk_individual_training_plan_exercise',N'individual_training_plan_exercise','PK',N'id',1),
 (N'uq_individual_training_plan_class_member',N'individual_training_plan','UQ',N'class_id',1),
 (N'uq_individual_training_plan_class_member',N'individual_training_plan','UQ',N'member_id',2),
 (N'uq_individual_training_revision_number',N'individual_training_plan_revision','UQ',N'plan_id',1),
 (N'uq_individual_training_revision_number',N'individual_training_plan_revision','UQ',N'revision_number',2),
 (N'uq_individual_training_week_number',N'individual_training_plan_week','UQ',N'revision_id',1),
 (N'uq_individual_training_week_number',N'individual_training_plan_week','UQ',N'week_number',2),
 (N'uq_individual_training_exercise_week_order',N'individual_training_plan_exercise','UQ',N'week_id',1),
 (N'uq_individual_training_exercise_week_order',N'individual_training_plan_exercise','UQ',N'sort_order',2);

INSERT INTO @Issues
SELECT N'key', CONCAT(e.constraint_type, N' ', e.constraint_name, N' ordinal ', e.key_ordinal,
    N': expected ', e.column_name, N'; actual=', COALESCE(c.name COLLATE DATABASE_DEFAULT, N'<missing>'))
FROM @KeyColumns e
LEFT JOIN sys.key_constraints kc ON kc.name COLLATE DATABASE_DEFAULT = e.constraint_name COLLATE DATABASE_DEFAULT
    AND kc.parent_object_id = OBJECT_ID(N'dbo.' + e.table_name)
    AND kc.type COLLATE DATABASE_DEFAULT = e.constraint_type COLLATE DATABASE_DEFAULT
LEFT JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id
    AND ic.index_id = kc.unique_index_id AND ic.key_ordinal = e.key_ordinal
LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.object_id IS NULL OR c.name COLLATE DATABASE_DEFAULT <> e.column_name COLLATE DATABASE_DEFAULT;

INSERT INTO @Issues
SELECT N'key shape', CONCAT(kc.type COLLATE DATABASE_DEFAULT, N' ', kc.name COLLATE DATABASE_DEFAULT, N': unexpected key column count ',
    (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = kc.parent_object_id
      AND ic.index_id = kc.unique_index_id AND ic.key_ordinal > 0))
FROM sys.key_constraints kc
WHERE kc.parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
  AND kc.type COLLATE DATABASE_DEFAULT IN ('PK' COLLATE DATABASE_DEFAULT,'UQ' COLLATE DATABASE_DEFAULT)
  AND ((kc.type COLLATE DATABASE_DEFAULT = 'PK' COLLATE DATABASE_DEFAULT
        AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id AND ic.key_ordinal > 0) <> 1)
    OR (kc.type COLLATE DATABASE_DEFAULT = 'UQ' COLLATE DATABASE_DEFAULT
        AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id AND ic.key_ordinal > 0) <> 2));
INSERT INTO @Issues
SELECT N'key state', CONCAT(kc.type_desc COLLATE DATABASE_DEFAULT, N' ', kc.name COLLATE DATABASE_DEFAULT,
    N': backing index missing, disabled, hypothetical, or not unique')
FROM sys.key_constraints kc
LEFT JOIN sys.indexes i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
WHERE kc.parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
  AND kc.type COLLATE DATABASE_DEFAULT IN ('PK' COLLATE DATABASE_DEFAULT,'UQ' COLLATE DATABASE_DEFAULT)
  AND (i.index_id IS NULL OR i.is_disabled <> 0 OR i.is_hypothetical <> 0 OR i.is_unique <> 1);
IF (SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
     AND type COLLATE DATABASE_DEFAULT = 'PK' COLLATE DATABASE_DEFAULT) <> 4
    INSERT INTO @Issues VALUES (N'primary keys', N'Expected exactly four PK constraints across the four tables.');
IF (SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
     AND type COLLATE DATABASE_DEFAULT = 'UQ' COLLATE DATABASE_DEFAULT) <> 4
    INSERT INTO @Issues VALUES (N'unique constraints', N'Expected exactly four named UQ constraints across the four tables.');

DECLARE @ForeignKeys table (
    constraint_name sysname COLLATE DATABASE_DEFAULT, parent_table sysname COLLATE DATABASE_DEFAULT,
    parent_column sysname COLLATE DATABASE_DEFAULT, referenced_table sysname COLLATE DATABASE_DEFAULT,
    referenced_column sysname COLLATE DATABASE_DEFAULT
);
INSERT INTO @ForeignKeys VALUES
 (N'fk_individual_training_plan_class',N'individual_training_plan',N'class_id',N'class',N'id'),
 (N'fk_individual_training_plan_member',N'individual_training_plan',N'member_id',N'users',N'id'),
 (N'fk_individual_training_plan_created_by',N'individual_training_plan',N'created_by',N'users',N'id'),
 (N'fk_individual_training_revision_plan',N'individual_training_plan_revision',N'plan_id',N'individual_training_plan',N'id'),
 (N'fk_individual_training_revision_user',N'individual_training_plan_revision',N'revised_by',N'users',N'id'),
 (N'fk_individual_training_week_revision',N'individual_training_plan_week',N'revision_id',N'individual_training_plan_revision',N'id'),
 (N'fk_individual_training_exercise_week',N'individual_training_plan_exercise',N'week_id',N'individual_training_plan_week',N'id');

INSERT INTO @Issues
SELECT N'foreign key', CONCAT(e.constraint_name, N': expected dbo.', e.parent_table, N'.', e.parent_column,
    N' -> dbo.', e.referenced_table, N'.', e.referenced_column,
    N'; actual parent=', COALESCE(OBJECT_SCHEMA_NAME(fk.parent_object_id) COLLATE DATABASE_DEFAULT, N'<missing>'), N'.',
    COALESCE(OBJECT_NAME(fk.parent_object_id) COLLATE DATABASE_DEFAULT, N'<missing>'), N'.', COALESCE(pc.name COLLATE DATABASE_DEFAULT, N'<missing>'),
    N' referenced=', COALESCE(OBJECT_SCHEMA_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT, N'<missing>'), N'.',
    COALESCE(OBJECT_NAME(fk.referenced_object_id) COLLATE DATABASE_DEFAULT, N'<missing>'), N'.', COALESCE(rc.name COLLATE DATABASE_DEFAULT, N'<missing>'),
    N' disabled=', COALESCE(CONVERT(varchar(1), fk.is_disabled), N'?'),
    N' untrusted=', COALESCE(CONVERT(varchar(1), fk.is_not_trusted), N'?'),
    N' delete_action=', COALESCE(fk.delete_referential_action_desc, N'?'),
    N' update_action=', COALESCE(fk.update_referential_action_desc, N'?'))
FROM @ForeignKeys e
LEFT JOIN sys.foreign_keys fk ON fk.name COLLATE DATABASE_DEFAULT = e.constraint_name COLLATE DATABASE_DEFAULT
LEFT JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
LEFT JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
LEFT JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
WHERE fk.object_id IS NULL OR fk.parent_object_id <> OBJECT_ID(N'dbo.' + e.parent_table)
   OR fk.referenced_object_id <> OBJECT_ID(N'dbo.' + e.referenced_table)
   OR pc.name COLLATE DATABASE_DEFAULT <> e.parent_column COLLATE DATABASE_DEFAULT
   OR rc.name COLLATE DATABASE_DEFAULT <> e.referenced_column COLLATE DATABASE_DEFAULT
   OR fk.is_disabled <> 0 OR fk.is_not_trusted <> 0
   OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
   OR fk.is_not_for_replication <> 0
   OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) <> 1;
IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)) <> 7
    INSERT INTO @Issues VALUES (N'foreign keys', N'Expected exactly seven foreign keys across the four tables.');

DECLARE @Checks table (constraint_name sysname COLLATE DATABASE_DEFAULT,
    table_name sysname COLLATE DATABASE_DEFAULT,
    expected_definition nvarchar(1000) COLLATE DATABASE_DEFAULT);
INSERT INTO @Checks VALUES
 (N'ck_individual_training_revision_values',N'individual_training_plan_revision',N'(revision_number>(0)andlen(goal)>(0))'),
 (N'ck_individual_training_week_number',N'individual_training_plan_week',N'(week_number>=(1)andweek_number<=(52))'),
 (N'ck_individual_training_exercise_values',N'individual_training_plan_exercise',N'(len(name)>(0)and(setsisnullorsets>=(1)andsets<=(100))and(repsisnullorreps>=(1)andreps<=(1000))and(duration_minutesisnullorduration_minutes>=(1)andduration_minutes<=(240))andsort_order>=(0)andsort_order<=(1000))');

INSERT INTO @Issues
SELECT N'check constraint', CONCAT(e.constraint_name, N': expected normalized definition ', e.expected_definition,
    N'; actual=', COALESCE(cc.definition COLLATE DATABASE_DEFAULT, N'<missing>'),
    N'; disabled=', COALESCE(CONVERT(varchar(1), cc.is_disabled), N'?'),
    N' untrusted=', COALESCE(CONVERT(varchar(1), cc.is_not_trusted), N'?'))
FROM @Checks e
LEFT JOIN sys.check_constraints cc ON cc.name COLLATE DATABASE_DEFAULT = e.constraint_name COLLATE DATABASE_DEFAULT
    AND cc.parent_object_id = OBJECT_ID(N'dbo.' + e.table_name)
CROSS APPLY (SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    cc.definition COLLATE DATABASE_DEFAULT, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''), N']', N''))
    COLLATE DATABASE_DEFAULT AS normalized_text) n
WHERE cc.object_id IS NULL OR n.normalized_text COLLATE DATABASE_DEFAULT <> e.expected_definition COLLATE DATABASE_DEFAULT
   OR cc.is_disabled <> 0 OR cc.is_not_trusted <> 0;
IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)) <> 3
    INSERT INTO @Issues VALUES (N'check constraints', N'Expected exactly three CHECK constraints across the four tables.');

DECLARE @IndexKeys table (
    index_name sysname COLLATE DATABASE_DEFAULT, table_name sysname COLLATE DATABASE_DEFAULT,
    key_ordinal int, column_name sysname COLLATE DATABASE_DEFAULT, is_descending_key bit
);
INSERT INTO @IndexKeys VALUES
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
 (N'ix_individual_training_exercise_week_order',N'individual_training_plan_exercise',3,N'id',0);

INSERT INTO @Issues
SELECT N'index', CONCAT(e.index_name, N' key ordinal ', e.key_ordinal, N': expected ', e.column_name,
    N' DESC=', e.is_descending_key, N'; actual=', COALESCE(c.name COLLATE DATABASE_DEFAULT, N'<missing>'),
    N' DESC=', COALESCE(CONVERT(varchar(1), ic.is_descending_key), N'?'),
    N' disabled=', COALESCE(CONVERT(varchar(1), i.is_disabled), N'?'))
FROM @IndexKeys e
LEFT JOIN sys.indexes i ON i.object_id = OBJECT_ID(N'dbo.' + e.table_name)
    AND i.name COLLATE DATABASE_DEFAULT = e.index_name COLLATE DATABASE_DEFAULT
LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = e.key_ordinal
LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.index_id IS NULL OR i.type <> 2 OR i.is_unique <> 0 OR i.is_disabled <> 0
   OR i.is_hypothetical <> 0 OR i.has_filter <> 0
   OR c.name COLLATE DATABASE_DEFAULT <> e.column_name COLLATE DATABASE_DEFAULT
   OR ic.is_descending_key <> e.is_descending_key;

DECLARE @ExpectedIndexes table (index_name sysname COLLATE DATABASE_DEFAULT,
    table_name sysname COLLATE DATABASE_DEFAULT, key_count int);
INSERT INTO @ExpectedIndexes VALUES
 (N'ix_individual_training_plan_member_updated',N'individual_training_plan',3),
 (N'ix_individual_training_plan_class',N'individual_training_plan',2),
 (N'ix_individual_training_revision_plan_number',N'individual_training_plan_revision',2),
 (N'ix_individual_training_week_revision_order',N'individual_training_plan_week',3),
 (N'ix_individual_training_exercise_week_order',N'individual_training_plan_exercise',3);
INSERT INTO @Issues
SELECT N'index shape', CONCAT(e.index_name, N': expected ', e.key_count,
    N' key columns, actual=', COALESCE(CONVERT(varchar(10), actual.key_count), N'<missing>'),
    N'; included columns=', COALESCE(CONVERT(varchar(10), actual.included_count), N'?'))
FROM @ExpectedIndexes e
LEFT JOIN sys.indexes i ON i.object_id = OBJECT_ID(N'dbo.' + e.table_name)
    AND i.name COLLATE DATABASE_DEFAULT = e.index_name COLLATE DATABASE_DEFAULT
OUTER APPLY (SELECT SUM(CASE WHEN ic.key_ordinal > 0 THEN 1 ELSE 0 END) key_count,
                    SUM(CASE WHEN ic.is_included_column = 1 THEN 1 ELSE 0 END) included_count
             FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id) actual
WHERE i.index_id IS NULL OR actual.key_count <> e.key_count OR actual.included_count <> 0;
IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
      AND name COLLATE DATABASE_DEFAULT IN
          (SELECT index_name COLLATE DATABASE_DEFAULT FROM @ExpectedIndexes)) <> 5
    INSERT INTO @Issues VALUES (N'indexes', N'Expected all five named query indexes.');

SELECT CASE WHEN EXISTS (SELECT 1 FROM @Issues) THEN N'FAIL' ELSE N'PASS' END AS validation_status,
       (SELECT COUNT(*) FROM @Issues) AS mismatch_count,
       N'SportCenterPtE2E' AS expected_database,
       N'sportcenter-recovery-sql' AS expected_server;

SELECT check_name, detail FROM @Issues ORDER BY check_name, detail;

-- Supplemental exact definitions and metadata for review; these are read-only catalog queries.
SELECT s.name AS schema_name, t.name AS table_name, c.column_id, c.name AS column_name,
       ty.name AS sql_type, c.max_length, c.precision, c.scale, c.is_nullable, c.is_identity,
       idc.seed_value AS identity_seed, idc.increment_value AS identity_increment
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id
LEFT JOIN sys.identity_columns idc ON idc.object_id=c.object_id AND idc.column_id=c.column_id
WHERE s.name COLLATE DATABASE_DEFAULT = N'dbo' COLLATE DATABASE_DEFAULT
  AND t.name COLLATE DATABASE_DEFAULT IN (SELECT table_name COLLATE DATABASE_DEFAULT FROM @Tables)
ORDER BY t.name,c.column_id;

SELECT kc.name AS constraint_name, kc.type_desc, t.name AS table_name,
       STRING_AGG(c.name, N',') WITHIN GROUP (ORDER BY ic.key_ordinal) AS key_columns
FROM sys.key_constraints kc JOIN sys.tables t ON t.object_id=kc.parent_object_id
JOIN sys.index_columns ic ON ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE kc.parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
GROUP BY kc.name,kc.type_desc,t.name ORDER BY t.name,kc.type_desc,kc.name;

SELECT fk.name AS constraint_name, OBJECT_SCHEMA_NAME(fk.parent_object_id) AS parent_schema,
       OBJECT_NAME(fk.parent_object_id) AS parent_table, pc.name AS parent_column,
       OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS referenced_schema,
       OBJECT_NAME(fk.referenced_object_id) AS referenced_table, rc.name AS referenced_column,
       fk.is_disabled, fk.is_not_trusted, fk.delete_referential_action_desc, fk.update_referential_action_desc
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
WHERE fk.parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
ORDER BY parent_table,constraint_name;

SELECT cc.name AS constraint_name, OBJECT_NAME(cc.parent_object_id) AS table_name,
       cc.definition, cc.is_disabled, cc.is_not_trusted
FROM sys.check_constraints cc
WHERE cc.parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
ORDER BY table_name,constraint_name;

SELECT i.name AS index_name, OBJECT_NAME(i.object_id) AS table_name, i.type_desc,
       i.is_unique, i.is_disabled, i.has_filter, ic.key_ordinal, c.name AS column_name,
       ic.is_descending_key, ic.is_included_column
FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id IN (SELECT OBJECT_ID(N'dbo.' + table_name) FROM @Tables)
  AND i.name COLLATE DATABASE_DEFAULT IN (SELECT index_name COLLATE DATABASE_DEFAULT FROM @ExpectedIndexes)
ORDER BY table_name,index_name,ic.key_ordinal,ic.index_column_id;
