-- SQL Server 2016+ manual migration for Phase 4B.1 ClassSession Attendance.
-- Back up SportCenterDB and stop application writers before running.
-- Execute as one standalone batch; do not wrap this script in another transaction.
-- Idempotent and non-destructive: it creates missing Attendance objects and never drops data.
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
    THROW 50401, 'Select SportCenterDB before running the Class Attendance migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50402, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase4B1.ClassAttendance.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50403, 'Could not acquire the Class Attendance migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.class_session', N'U') IS NULL OR OBJECT_ID(N'dbo.users', N'U') IS NULL
        THROW 50404, 'Expected dbo.class_session and dbo.users tables.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.class_session')
                    AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND is_identity = 1)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users')
                      AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND is_identity = 1)
        THROW 50405, 'dbo.class_session.id and dbo.users.id must be INT IDENTITY keys.', 1;

    IF OBJECT_ID(N'dbo.attendance') IS NOT NULL AND OBJECT_ID(N'dbo.attendance', N'U') IS NULL
        THROW 50406, 'dbo.attendance is occupied by a non-table object.', 1;
    IF OBJECT_ID(N'dbo.attendance_history') IS NOT NULL AND OBJECT_ID(N'dbo.attendance_history', N'U') IS NULL
        THROW 50407, 'dbo.attendance_history is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.attendance', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.attendance (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_attendance PRIMARY KEY,
            class_session_id INT NOT NULL,
            member_id INT NOT NULL,
            status VARCHAR(30) NOT NULL,
            marked_by INT NOT NULL,
            marked_at DATETIME2(7) NOT NULL,
            note NVARCHAR(500) NULL,
            CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED')),
            CONSTRAINT uq_attendance_session_member UNIQUE (class_session_id, member_id),
            CONSTRAINT fk_attendance_session FOREIGN KEY (class_session_id) REFERENCES dbo.class_session(id),
            CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_attendance_marked_by FOREIGN KEY (marked_by) REFERENCES dbo.users(id)
        );
    END;

    IF OBJECT_ID(N'dbo.attendance_history', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.attendance_history (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_attendance_history PRIMARY KEY,
            attendance_id INT NOT NULL,
            old_status VARCHAR(30) NULL,
            new_status VARCHAR(30) NOT NULL,
            changed_by INT NOT NULL,
            changed_at DATETIME2(7) NOT NULL,
            reason NVARCHAR(500) NULL,
            CONSTRAINT ck_attendance_history_old_status CHECK
                (old_status IS NULL OR old_status IN ('PRESENT', 'ABSENT', 'EXCUSED')),
            CONSTRAINT ck_attendance_history_new_status CHECK
                (new_status IN ('PRESENT', 'ABSENT', 'EXCUSED')),
            CONSTRAINT fk_attendance_history_attendance FOREIGN KEY (attendance_id) REFERENCES dbo.attendance(id),
            CONSTRAINT fk_attendance_history_changed_by FOREIGN KEY (changed_by) REFERENCES dbo.users(id)
        );
    END;

    IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.attendance', N'U')) <> 7
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.attendance_history', N'U')) <> 7
        THROW 50408, 'Existing attendance tables have unexpected columns; inspect them before migration.', 1;

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            (N'attendance', N'id', N'int', 4, 0, 0, 1, 0),
            (N'attendance', N'class_session_id', N'int', 4, 0, 0, 0, 0),
            (N'attendance', N'member_id', N'int', 4, 0, 0, 0, 0),
            (N'attendance', N'status', N'varchar', 30, 0, 0, 0, 0),
            (N'attendance', N'marked_by', N'int', 4, 0, 0, 0, 0),
            (N'attendance', N'marked_at', N'datetime2', 8, 0, 7, 0, 0),
            (N'attendance', N'note', N'nvarchar', 1000, 1, 0, 0, 0),
            (N'attendance_history', N'id', N'int', 4, 0, 0, 1, 0),
            (N'attendance_history', N'attendance_id', N'int', 4, 0, 0, 0, 0),
            (N'attendance_history', N'old_status', N'varchar', 30, 1, 0, 0, 0),
            (N'attendance_history', N'new_status', N'varchar', 30, 0, 0, 0, 0),
            (N'attendance_history', N'changed_by', N'int', 4, 0, 0, 0, 0),
            (N'attendance_history', N'changed_at', N'datetime2', 8, 0, 7, 0, 0),
            (N'attendance_history', N'reason', N'nvarchar', 1000, 1, 0, 0, 0)
        ) expected(table_name, column_name, type_name, max_length, is_nullable, scale, is_identity, unused)
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.' + expected.table_name, N'U')
                               AND c.name = expected.column_name
        LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL OR t.name <> expected.type_name OR t.is_user_defined <> 0
           OR c.user_type_id <> c.system_type_id OR c.max_length <> expected.max_length
           OR c.is_nullable <> expected.is_nullable OR c.is_identity <> expected.is_identity
           OR (expected.scale <> 0 AND c.scale <> expected.scale)
           OR c.is_computed = 1 OR c.generated_always_type <> 0
    )
        THROW 50409, 'Existing Attendance column metadata does not match the JPA contract.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.attendance') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.attendance_history') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    )
        THROW 50410, 'Attendance tables must have enabled single-column id primary keys.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance')
               AND name = N'ck_attendance_status'
               AND (is_disabled = 1 OR is_not_trusted = 1 OR
                    LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                    NOT IN (N'statusin(''present'',''absent'',''excused'')',
                            N'status=''present''orstatus=''absent''orstatus=''excused''',
                            N'status=''present''orstatus=''excused''orstatus=''absent''',
                            N'status=''absent''orstatus=''present''orstatus=''excused''',
                            N'status=''absent''orstatus=''excused''orstatus=''present''',
                            N'status=''excused''orstatus=''present''orstatus=''absent''',
                            N'status=''excused''orstatus=''absent''orstatus=''present''')))
        THROW 50411, 'Existing ck_attendance_status is disabled, untrusted, or incompatible.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance')
                   AND name = N'ck_attendance_status' AND is_disabled = 0 AND is_not_trusted = 0)
        ALTER TABLE dbo.attendance WITH CHECK ADD CONSTRAINT ck_attendance_status
            CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED'));

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance_history')
               AND name = N'ck_attendance_history_old_status'
               AND (is_disabled = 1 OR is_not_trusted = 1 OR
                    LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                    NOT IN (N'old_statusisnullorold_statusin(''present'',''absent'',''excused'')',
                            N'old_statusisnullorold_status=''present''orold_status=''absent''orold_status=''excused''',
                            N'old_statusisnullorold_status=''present''orold_status=''excused''orold_status=''absent''',
                            N'old_statusisnullorold_status=''absent''orold_status=''present''orold_status=''excused''',
                            N'old_statusisnullorold_status=''absent''orold_status=''excused''orold_status=''present''',
                            N'old_statusisnullorold_status=''excused''orold_status=''present''orold_status=''absent''',
                            N'old_statusisnullorold_status=''excused''orold_status=''absent''orold_status=''present''')))
        THROW 50412, 'Existing ck_attendance_history_old_status is disabled, untrusted, or incompatible.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance_history')
                   AND name = N'ck_attendance_history_old_status' AND is_disabled = 0 AND is_not_trusted = 0)
        ALTER TABLE dbo.attendance_history WITH CHECK ADD CONSTRAINT ck_attendance_history_old_status
            CHECK (old_status IS NULL OR old_status IN ('PRESENT', 'ABSENT', 'EXCUSED'));

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance_history')
               AND name = N'ck_attendance_history_new_status'
               AND (is_disabled = 1 OR is_not_trusted = 1 OR
                    LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                    NOT IN (N'new_statusin(''present'',''absent'',''excused'')',
                            N'new_status=''present''ornew_status=''absent''ornew_status=''excused''',
                            N'new_status=''present''ornew_status=''excused''ornew_status=''absent''',
                            N'new_status=''absent''ornew_status=''present''ornew_status=''excused''',
                            N'new_status=''absent''ornew_status=''excused''ornew_status=''present''',
                            N'new_status=''excused''ornew_status=''present''ornew_status=''absent''',
                            N'new_status=''excused''ornew_status=''absent''ornew_status=''present''')))
        THROW 50413, 'Existing ck_attendance_history_new_status is disabled, untrusted, or incompatible.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.attendance_history')
                   AND name = N'ck_attendance_history_new_status' AND is_disabled = 0 AND is_not_trusted = 0)
        ALTER TABLE dbo.attendance_history WITH CHECK ADD CONSTRAINT ck_attendance_history_new_status
            CHECK (new_status IN ('PRESENT', 'ABSENT', 'EXCUSED'));

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.attendance')
               AND name = N'uq_attendance_session_member')
       AND NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.attendance') AND i.name = N'uq_attendance_session_member'
          AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 0
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns included
                          WHERE included.object_id = i.object_id AND included.index_id = i.index_id
                            AND included.is_included_column = 1)
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
               AND k.index_id = i.index_id AND k.key_ordinal > 0) = 2
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 1 AND k.is_descending_key = 0 AND c.name = N'class_session_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 2 AND k.is_descending_key = 0 AND c.name = N'member_id')
    )
        THROW 50414, 'Existing uq_attendance_session_member does not enforce the required unique key.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.attendance')
                   AND name = N'uq_attendance_session_member' AND is_unique = 1 AND is_disabled = 0)
        ALTER TABLE dbo.attendance ADD CONSTRAINT uq_attendance_session_member UNIQUE (class_session_id, member_id);

    IF EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE (fk.parent_object_id = OBJECT_ID(N'dbo.attendance') AND c.name IN (N'class_session_id', N'member_id', N'marked_by')
               OR fk.parent_object_id = OBJECT_ID(N'dbo.attendance_history') AND c.name IN (N'attendance_id', N'changed_by'))
          AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.delete_referential_action <> 0
               OR fk.update_referential_action <> 0 OR (SELECT COUNT(*) FROM sys.foreign_key_columns x
                   WHERE x.constraint_object_id = fk.object_id) <> 1
               OR fkc.referenced_object_id <> CASE
                    WHEN fk.parent_object_id = OBJECT_ID(N'dbo.attendance') AND c.name = N'class_session_id'
                        THEN OBJECT_ID(N'dbo.class_session')
                    WHEN fk.parent_object_id = OBJECT_ID(N'dbo.attendance_history') AND c.name = N'attendance_id'
                        THEN OBJECT_ID(N'dbo.attendance')
                    ELSE OBJECT_ID(N'dbo.users') END
               OR COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) <> N'id')
    )
        THROW 50415, 'An incompatible Attendance foreign key exists; inspect it before migration.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc
                   ON fkc.constraint_object_id = fk.object_id JOIN sys.columns c
                   ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.attendance') AND c.name = N'class_session_id'
                     AND fkc.referenced_object_id = OBJECT_ID(N'dbo.class_session')
                     AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
                     AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
                     AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
                     AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    BEGIN
        IF OBJECT_ID(N'dbo.fk_attendance_session', N'F') IS NOT NULL
            THROW 50416, 'Expected fk_attendance_session name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.attendance WITH CHECK ADD CONSTRAINT fk_attendance_session
            FOREIGN KEY (class_session_id) REFERENCES dbo.class_session(id);
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc
                   ON fkc.constraint_object_id = fk.object_id JOIN sys.columns c
                   ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.attendance') AND c.name = N'member_id'
                     AND fkc.referenced_object_id = OBJECT_ID(N'dbo.users')
                     AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
                     AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
                     AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
                     AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    BEGIN
        IF OBJECT_ID(N'dbo.fk_attendance_member', N'F') IS NOT NULL
            THROW 50417, 'Expected fk_attendance_member name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.attendance WITH CHECK ADD CONSTRAINT fk_attendance_member
            FOREIGN KEY (member_id) REFERENCES dbo.users(id);
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc
                   ON fkc.constraint_object_id = fk.object_id JOIN sys.columns c
                   ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.attendance') AND c.name = N'marked_by'
                     AND fkc.referenced_object_id = OBJECT_ID(N'dbo.users')
                     AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
                     AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
                     AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
                     AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    BEGIN
        IF OBJECT_ID(N'dbo.fk_attendance_marked_by', N'F') IS NOT NULL
            THROW 50418, 'Expected fk_attendance_marked_by name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.attendance WITH CHECK ADD CONSTRAINT fk_attendance_marked_by
            FOREIGN KEY (marked_by) REFERENCES dbo.users(id);
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc
                   ON fkc.constraint_object_id = fk.object_id JOIN sys.columns c
                   ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.attendance_history') AND c.name = N'attendance_id'
                     AND fkc.referenced_object_id = OBJECT_ID(N'dbo.attendance')
                     AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
                     AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
                     AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
                     AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    BEGIN
        IF OBJECT_ID(N'dbo.fk_attendance_history_attendance', N'F') IS NOT NULL
            THROW 50419, 'Expected fk_attendance_history_attendance name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.attendance_history WITH CHECK ADD CONSTRAINT fk_attendance_history_attendance
            FOREIGN KEY (attendance_id) REFERENCES dbo.attendance(id);
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc
                   ON fkc.constraint_object_id = fk.object_id JOIN sys.columns c
                   ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                   WHERE fk.parent_object_id = OBJECT_ID(N'dbo.attendance_history') AND c.name = N'changed_by'
                     AND fkc.referenced_object_id = OBJECT_ID(N'dbo.users')
                     AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
                     AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
                     AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
                     AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    BEGIN
        IF OBJECT_ID(N'dbo.fk_attendance_history_changed_by', N'F') IS NOT NULL
            THROW 50420, 'Expected fk_attendance_history_changed_by name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.attendance_history WITH CHECK ADD CONSTRAINT fk_attendance_history_changed_by
            FOREIGN KEY (changed_by) REFERENCES dbo.users(id);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;