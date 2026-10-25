-- SQL Server 2016+ manual migration for CourseRegistration payment holds.
-- Run after phase2-course-registrations.sql (and phase8-7b-facility-sports.sql when deploying both).
-- Back up SportCenterDB, stop application writers, and rehearse against a restored copy first.
-- Hibernate remains ddl-auto=validate. This script is not executed automatically.
-- Existing registrations retain their status and price snapshots. payment_deadline remains NULL
-- for historical rows; new PENDING_PAYMENT rows must carry a deadline.
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
    THROW 50801, 'Select SportCenterDB before running the Course Registration hold migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50802, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.course_registration', N'U') IS NULL
    THROW 50803, 'Expected dbo.course_registration; run phase2-course-registrations.sql first.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 50817, 'Expected dbo.payment; apply phase2-payment.sql and reconcile existing course payments first.', 1;
IF EXISTS (SELECT 1 FROM dbo.payment WHERE purpose = 'COURSE_REGISTRATION' AND status = 'PENDING')
    THROW 50818, 'Pending legacy CourseRegistration payments exist; reconcile them before enabling deadline-based settlement.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'id' AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id
               AND is_nullable = 0 AND is_identity = 1)
   OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'member_id' AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id
               AND is_nullable = 0)
   OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'class_id' AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id
               AND is_nullable = 0)
   OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'status' AND system_type_id = TYPE_ID(N'varchar') AND user_type_id = system_type_id
               AND max_length = 20 AND is_nullable = 0)
   OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'registered_at' AND system_type_id = TYPE_ID(N'datetime2')
               AND user_type_id = system_type_id AND scale IN (6, 7) AND is_nullable = 0)
        THROW 50816, 'Core CourseRegistration metadata differs from the Phase 2 migration contract.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase8.7C.CourseRegistrationHolds',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50804, 'Could not acquire the registration migration lock; retry with writers stopped.', 1;

    IF COL_LENGTH(N'dbo.course_registration', N'payment_deadline') IS NULL
        ALTER TABLE dbo.course_registration ADD payment_deadline DATETIME2(6) NULL;

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'payment_deadline'
          AND c.system_type_id = TYPE_ID(N'datetime2') AND c.user_type_id = c.system_type_id
          AND c.scale = 6 AND c.is_nullable = 1 AND c.is_identity = 0
          AND c.is_computed = 0 AND c.generated_always_type = 0
    )
        THROW 50805, 'payment_deadline must be nullable DATETIME2(6); inspect the existing column manually.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.check_constraints cc
        WHERE cc.parent_object_id = OBJECT_ID(N'dbo.course_registration')
          AND (cc.name <> N'ck_course_registration_status'
               AND LOWER(cc.definition) LIKE N'%status%')
    )
        THROW 50806, 'An additional CourseRegistration CHECK constraint references status; inspect it before migration.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.check_constraints cc
        WHERE cc.parent_object_id = OBJECT_ID(N'dbo.course_registration')
          AND cc.name = N'ck_course_registration_status'
          AND (cc.is_disabled = 1 OR cc.is_not_trusted = 1
               OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                  NOT IN (N'statusin(''confirmed'',''cancelled'')',
                          N'status=''confirmed''orstatus=''cancelled''',
                          N'status=''cancelled''orstatus=''confirmed''',
                          N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')andstatus<>''pending_payment''orpayment_deadlineisnotnull')))
        THROW 50807, 'The named CourseRegistration status CHECK is disabled, untrusted, or has an unexpected definition.', 1;

    IF EXISTS (SELECT 1 FROM dbo.course_registration WHERE status NOT IN
        ('CONFIRMED', 'CANCELLED', 'PENDING_PAYMENT', 'EXPIRED'))
        THROW 50808, 'Unexpected CourseRegistration status values exist; inspect the data before migration.', 1;
    IF EXISTS (SELECT 1 FROM dbo.course_registration
        WHERE status = 'PENDING_PAYMENT' AND payment_deadline IS NULL)
        THROW 50809, 'Pending registrations already exist without payment_deadline; inspect and resolve them manually.', 1;
    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.is_unique = 1
          AND i.is_hypothetical = 0
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
               AND k.index_id = i.index_id AND k.key_ordinal > 0) = 2
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
               ON c.object_id = ic.object_id AND c.column_id = ic.column_id
               WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND c.name = N'member_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
               ON c.object_id = ic.object_id AND c.column_id = ic.column_id
               WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND c.name = N'class_id')
          AND (i.name NOT IN (N'ux_course_registration_confirmed', N'ux_course_registration_pending')
               OR i.has_filter <> 1 OR i.is_disabled = 1)
    )
        THROW 50813, 'An additional unique member/class index may block valid re-registration; inspect it manually.', 1;

    -- Existing index protects confirmed history. The additional filtered index protects active holds;
    -- service-level MEMBER and CourseClass row locks serialize cross-status checks and capacity.
    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.name = N'ux_course_registration_pending'
          AND (i.type <> 2 OR i.is_unique <> 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1
               OR i.is_unique_constraint = 1 OR i.ignore_dup_key <> 0 OR i.has_filter <> 1
               OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                    NOT IN (N'status=''pending_payment''', N'''pending_payment''=status')
               OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                    AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 2
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'member_id')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 2 AND ic.is_descending_key = 0 AND c.name = N'class_id')
               OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                    AND inc.index_id = i.index_id AND inc.is_included_column = 1))
    )
        THROW 50810, 'ux_course_registration_pending exists with incompatible metadata.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.name = N'ux_course_registration_confirmed'
          AND (i.type <> 2 OR i.is_unique <> 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1
               OR i.is_unique_constraint = 1 OR i.ignore_dup_key <> 0 OR i.has_filter <> 1
               OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition, N'[', N''), N']', N''), N'(', N''), N')', N''), N' ', N''))
                    NOT IN (N'status=''confirmed''', N'''confirmed''=status')
               OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                    AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 2
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'member_id')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 2 AND ic.is_descending_key = 0 AND c.name = N'class_id')
               OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                    AND inc.index_id = i.index_id AND inc.is_included_column = 1))
    )
        THROW 50815, 'ux_course_registration_confirmed exists with incompatible metadata.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.name = N'ix_course_registration_expiry'
          AND (i.type <> 2 OR i.is_unique = 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1
               OR i.has_filter = 1
               OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                    AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 3
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'status')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 2 AND ic.is_descending_key = 0 AND c.name = N'payment_deadline')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 3 AND ic.is_descending_key = 0 AND c.name = N'id')
               OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                    AND inc.index_id = i.index_id AND inc.is_included_column = 1))
    )
        THROW 50811, 'ix_course_registration_expiry exists with incompatible metadata.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.course_registration') AND i.name = N'ix_course_registration_class_deadline'
          AND (i.type <> 2 OR i.is_unique = 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1
               OR i.has_filter = 1
               OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                    AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 3
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'class_id')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 2 AND ic.is_descending_key = 0 AND c.name = N'status')
               OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                      AND ic.key_ordinal = 3 AND ic.is_descending_key = 0 AND c.name = N'payment_deadline')
               OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                    AND inc.index_id = i.index_id AND inc.is_included_column = 1))
    )
        THROW 50814, 'ix_course_registration_class_deadline exists with incompatible metadata.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.course_registration')
               AND name = N'ck_course_registration_status')
        ALTER TABLE dbo.course_registration DROP CONSTRAINT ck_course_registration_status;

    ALTER TABLE dbo.course_registration WITH CHECK ADD CONSTRAINT ck_course_registration_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')
               AND (status <> 'PENDING_PAYMENT' OR payment_deadline IS NOT NULL));
    ALTER TABLE dbo.course_registration CHECK CONSTRAINT ck_course_registration_status;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
                   AND name = N'ux_course_registration_pending')
        CREATE UNIQUE INDEX ux_course_registration_pending
            ON dbo.course_registration(member_id ASC, class_id ASC)
            WHERE status = 'PENDING_PAYMENT';

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
                   AND name = N'ux_course_registration_confirmed')
        CREATE UNIQUE INDEX ux_course_registration_confirmed
            ON dbo.course_registration(member_id ASC, class_id ASC)
            WHERE status = 'CONFIRMED';

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
                   AND name = N'ix_course_registration_expiry')
        CREATE INDEX ix_course_registration_expiry
            ON dbo.course_registration(status ASC, payment_deadline ASC, id ASC);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.course_registration')
                   AND name = N'ix_course_registration_class_deadline')
        CREATE INDEX ix_course_registration_class_deadline
            ON dbo.course_registration(class_id ASC, status ASC, payment_deadline ASC);

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.check_constraints cc ON cc.parent_object_id = c.object_id
        WHERE c.object_id = OBJECT_ID(N'dbo.course_registration') AND c.name = N'payment_deadline'
          AND cc.name = N'ck_course_registration_status' AND cc.is_disabled = 0 AND cc.is_not_trusted = 0
    )
        THROW 50812, 'The new status/deadline CHECK constraint did not validate.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
