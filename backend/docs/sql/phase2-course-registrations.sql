-- SQL Server 2016+ manual migration for Phase 2 CourseClass Registration.
-- Back up the target database and stop application writers before running.
-- Run as one standalone batch, without GO or an outer transaction.
-- Idempotent: does not drop tables, columns, indexes, constraints, or data.
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
    THROW 50201, 'Select SportCenterDB before running the Course Registration migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50202, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase2.CourseRegistration.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50203, 'Could not acquire the Course Registration migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL OR OBJECT_ID(N'dbo.[class]', N'U') IS NULL
        THROW 50204, 'Expected dbo.users and dbo.[class] tables.', 1;
    IF OBJECT_ID(N'dbo.course_registration') IS NOT NULL
       AND OBJECT_ID(N'dbo.course_registration', N'U') IS NULL
        THROW 50205, 'dbo.course_registration is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.course_registration', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.course_registration (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_course_registration PRIMARY KEY,
            member_id INT NOT NULL,
            class_id INT NOT NULL,
            status VARCHAR(20) NOT NULL CONSTRAINT df_course_registration_status DEFAULT ('CONFIRMED'),
            registered_at DATETIME2(7) NOT NULL CONSTRAINT df_course_registration_registered_at DEFAULT (SYSDATETIME()),
            cancelled_at DATETIME2(7) NULL,
            original_price DECIMAL(12,2) NOT NULL,
            membership_discount DECIMAL(12,2) NOT NULL,
            final_price DECIMAL(12,2) NOT NULL,
            CONSTRAINT ck_course_registration_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
            CONSTRAINT fk_course_registration_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_course_registration_class FOREIGN KEY (class_id) REFERENCES dbo.[class](id)
        );
    END;

    IF COL_LENGTH(N'dbo.course_registration', N'id') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'member_id') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'class_id') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'status') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'registered_at') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'cancelled_at') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'original_price') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'membership_discount') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'final_price') IS NULL
        THROW 50206, 'Existing dbo.course_registration is incomplete; inspect it before migration.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'id'
          AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
          AND c.is_nullable = 0 AND c.is_identity = 1 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'member_id'
          AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
          AND c.is_nullable = 0 AND c.is_identity = 0 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'class_id'
          AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
          AND c.is_nullable = 0 AND c.is_identity = 0 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'status'
          AND c.system_type_id = TYPE_ID(N'varchar') AND c.user_type_id = c.system_type_id
          AND c.max_length = 20 AND c.is_nullable = 0 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'registered_at'
          AND c.system_type_id = TYPE_ID(N'datetime2') AND c.user_type_id = c.system_type_id
          AND c.scale IN (6, 7) AND c.is_nullable = 0 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'cancelled_at'
          AND c.system_type_id = TYPE_ID(N'datetime2') AND c.user_type_id = c.system_type_id
          AND c.scale IN (6, 7) AND c.is_nullable = 1 AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'original_price'
          AND c.system_type_id = TYPE_ID(N'decimal') AND c.user_type_id = c.system_type_id
          AND c.precision = 12 AND c.scale = 2 AND c.is_nullable = 0
          AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'membership_discount'
          AND c.system_type_id = TYPE_ID(N'decimal') AND c.user_type_id = c.system_type_id
          AND c.precision = 12 AND c.scale = 2 AND c.is_nullable = 0
          AND c.is_computed = 0 AND c.generated_always_type = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'final_price'
          AND c.system_type_id = TYPE_ID(N'decimal') AND c.user_type_id = c.system_type_id
          AND c.precision = 12 AND c.scale = 2 AND c.is_nullable = 0
          AND c.is_computed = 0 AND c.generated_always_type = 0
    )
        THROW 50207, 'Existing dbo.course_registration column metadata does not match the CourseClass migration contract.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    )
        THROW 50210, 'dbo.course_registration must have an enabled single-column id primary key.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.course_registration')
          AND name = N'ck_course_registration_status'
          AND (is_disabled = 1 OR is_not_trusted = 1 OR
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                   NOT IN (N'statusin(''confirmed'',''cancelled'')',
                           N'status=''confirmed''orstatus=''cancelled''',
                           N'status=''cancelled''orstatus=''confirmed'''))
    )
        THROW 50213, 'Existing ck_course_registration_status constraint is disabled, untrusted, or incompatible.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.course_registration')
          AND name = N'ck_course_registration_status' AND is_disabled = 0 AND is_not_trusted = 0
    )
        ALTER TABLE dbo.course_registration WITH CHECK ADD CONSTRAINT ck_course_registration_status
            CHECK (status IN ('CONFIRMED', 'CANCELLED'));

    IF EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.course_registration')
          AND c.name IN (N'member_id', N'class_id')
          AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.delete_referential_action <> 0
               OR fk.update_referential_action <> 0 OR fkc.referenced_object_id <> CASE c.name
                    WHEN N'member_id' THEN OBJECT_ID(N'dbo.users') ELSE OBJECT_ID(N'dbo.[class]') END
               OR COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) <> N'id'
               OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) <> 1)
    )
        THROW 50211, 'An incompatible foreign key exists on a CourseClass Registration relationship column.', 1;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'ux_course_registration_confirmed')
       AND NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration')
          AND i.name = N'ux_course_registration_confirmed' AND i.is_unique = 1 AND i.is_disabled = 0
          AND i.is_hypothetical = 0 AND i.has_filter = 1 AND i.ignore_dup_key = 0
          AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
              IN (N'status=''confirmed''', N'''confirmed''=status')
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 2
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 1 AND c.name = N'member_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 2 AND c.name = N'class_id')
    )
        THROW 50212, 'Existing ux_course_registration_confirmed index does not match the required unique filtered index.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'member_id'
          AND fkc.referenced_object_id = OBJECT_ID(N'dbo.users')
          AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    )
    BEGIN
        IF OBJECT_ID(N'dbo.fk_course_registration_member', N'F') IS NOT NULL
            THROW 50208, 'Expected member foreign-key name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.course_registration WITH CHECK ADD CONSTRAINT fk_course_registration_member
            FOREIGN KEY (member_id) REFERENCES dbo.users(id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'class_id'
          AND fkc.referenced_object_id = OBJECT_ID(N'dbo.[class]')
          AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    )
    BEGIN
        IF OBJECT_ID(N'dbo.fk_course_registration_class', N'F') IS NOT NULL
            THROW 50209, 'Expected CourseClass foreign-key name is already used by an incompatible object.', 1;
        ALTER TABLE dbo.course_registration WITH CHECK ADD CONSTRAINT fk_course_registration_class
            FOREIGN KEY (class_id) REFERENCES dbo.[class](id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
          AND name = N'ux_course_registration_confirmed' AND is_unique = 1 AND is_disabled = 0
    )
    BEGIN
        CREATE UNIQUE INDEX ux_course_registration_confirmed
            ON dbo.course_registration(member_id, class_id)
            WHERE status = 'CONFIRMED';
    END;

    IF EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.course_registration')
          AND name = N'ix_course_registration_class_status'
    ) AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration')
          AND i.name = N'ix_course_registration_class_status'
          AND i.type = 2 AND i.is_unique = 0 AND i.is_primary_key = 0
          AND i.is_unique_constraint = 0 AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.has_filter = 0 AND i.filter_definition IS NULL AND i.ignore_dup_key = 0
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 4
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.is_included_column = 1) = 0
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 1 AND k.is_included_column = 0
                        AND k.is_descending_key = 0 AND c.name = N'class_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 2 AND k.is_included_column = 0
                        AND k.is_descending_key = 0 AND c.name = N'status')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 3 AND k.is_included_column = 0
                        AND k.is_descending_key = 1 AND c.name = N'registered_at')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 4 AND k.is_included_column = 0
                        AND k.is_descending_key = 1 AND c.name = N'id')
    )
        THROW 50214, 'Existing ix_course_registration_class_status index is incompatible; inspect it manually.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration') AND name = N'ix_course_registration_class_status')
        CREATE INDEX ix_course_registration_class_status
            ON dbo.course_registration(class_id ASC, status ASC, registered_at DESC, id DESC);

    IF EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.course_registration')
          AND name = N'ix_course_registration_member_status'
    ) AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration')
          AND i.name = N'ix_course_registration_member_status'
          AND i.type = 2 AND i.is_unique = 0 AND i.is_primary_key = 0
          AND i.is_unique_constraint = 0 AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.has_filter = 0 AND i.filter_definition IS NULL AND i.ignore_dup_key = 0
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 4
          AND (SELECT COUNT(*) FROM sys.index_columns k
               WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.is_included_column = 1) = 0
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 1 AND k.is_included_column = 0
                        AND k.is_descending_key = 0 AND c.name = N'member_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 2 AND k.is_included_column = 0
                        AND k.is_descending_key = 0 AND c.name = N'status')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 3 AND k.is_included_column = 0
                        AND k.is_descending_key = 1 AND c.name = N'registered_at')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c
                      ON c.object_id = k.object_id AND c.column_id = k.column_id
                      WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                        AND k.key_ordinal = 4 AND k.is_included_column = 0
                        AND k.is_descending_key = 1 AND c.name = N'id')
    )
        THROW 50215, 'Existing ix_course_registration_member_status index is incompatible; inspect it manually.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration') AND name = N'ix_course_registration_member_status')
        CREATE INDEX ix_course_registration_member_status
            ON dbo.course_registration(member_id ASC, status ASC, registered_at DESC, id DESC);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
