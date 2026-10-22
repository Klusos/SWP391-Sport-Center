-- Standalone repair for the verified partial Phase 8.7C CourseRegistration schema.
-- Target only SportCenterDB after rehearsing on a restored copy and taking a fresh backup.
-- Stop application writers before running. SQL Server 2016+; execute as one batch, no GO.
-- This script preserves rows. It converts payment_deadline DATETIME2(7) to DATETIME2(6),
-- replaces only the verified legacy status CHECK, and adds the missing Phase 8.7C indexes.
-- It does not modify the original phase8-7c-course-registration-holds.sql migration.
--
-- Expected source state: dbo.course_registration has exactly the Phase 2 core shape,
-- nullable payment_deadline as DATETIME2(7) (or already-compatible DATETIME2(6)), the
-- trusted/enabled legacy status CHECK, and the valid confirmed filtered unique index.
-- There must be no pending registrations, no pending COURSE_REGISTRATION payments, and
-- no non-NULL payment_deadline values. Unexpected structures/data cause a rollback.

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
    THROW 50901, 'Select SportCenterDB before running the Phase 8.7C repair.', 1;
IF @@TRANCOUNT <> 0
    THROW 50902, 'Run this repair without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.course_registration', N'U') IS NULL
    THROW 50903, 'Expected dbo.course_registration; no changes were made.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 50904, 'Expected dbo.payment to validate pending course payments; no changes were made.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase8.7C.CourseRegistrationHolds.Repair',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50905, 'Could not acquire the Phase 8.7C repair lock; retry with writers stopped.', 1;

    DECLARE @tableId INT = OBJECT_ID(N'dbo.course_registration', N'U');
    DECLARE @paymentId INT = OBJECT_ID(N'dbo.payment', N'U');
    DECLARE @deadlineColumnId INT;
    SELECT @deadlineColumnId=column_id FROM sys.columns WHERE object_id=@tableId AND name=N'payment_deadline';

    -- Fail closed unless every existing CourseRegistration column matches the Phase 2
    -- migration contract. DATETIME2(6/7) is allowed for the two historical timestamp fields.
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'id' AND system_type_id=TYPE_ID(N'int') AND user_type_id=system_type_id AND is_nullable=0 AND is_identity=1 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'member_id' AND system_type_id=TYPE_ID(N'int') AND user_type_id=system_type_id AND is_nullable=0 AND is_identity=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'class_id' AND system_type_id=TYPE_ID(N'int') AND user_type_id=system_type_id AND is_nullable=0 AND is_identity=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'status' AND system_type_id=TYPE_ID(N'varchar') AND user_type_id=system_type_id AND max_length=20 AND is_nullable=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'registered_at' AND system_type_id=TYPE_ID(N'datetime2') AND user_type_id=system_type_id AND scale IN (6,7) AND is_nullable=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'cancelled_at' AND system_type_id=TYPE_ID(N'datetime2') AND user_type_id=system_type_id AND scale IN (6,7) AND is_nullable=1 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'original_price' AND system_type_id=TYPE_ID(N'decimal') AND user_type_id=system_type_id AND precision=12 AND scale=2 AND is_nullable=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'membership_discount' AND system_type_id=TYPE_ID(N'decimal') AND user_type_id=system_type_id AND precision=12 AND scale=2 AND is_nullable=0 AND is_computed=0 AND generated_always_type=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'final_price' AND system_type_id=TYPE_ID(N'decimal') AND user_type_id=system_type_id AND precision=12 AND scale=2 AND is_nullable=0 AND is_computed=0 AND generated_always_type=0)
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id=@tableId) <> 10
        THROW 50906, 'CourseRegistration core column metadata differs from Phase 2; no repair was made.', 1;

    -- payment_deadline must be precisely nullable DATETIME2(7) or already repaired DATETIME2(6).
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'payment_deadline'
                   AND system_type_id=TYPE_ID(N'datetime2') AND user_type_id=system_type_id
                   AND scale IN (6,7) AND is_nullable=1 AND is_identity=0 AND is_computed=0
                   AND generated_always_type=0)
        THROW 50907, 'payment_deadline is missing or incompatible; expected nullable DATETIME2(7) or DATETIME2(6).', 1;

    -- Require the expected identity PK and exactly the two non-cascading Phase 2 FKs.
    IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id=@tableId AND is_primary_key=1 AND is_disabled=0) <> 1
       OR NOT EXISTS (SELECT 1 FROM sys.indexes i
          JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1
          JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
          WHERE i.object_id=@tableId AND i.is_primary_key=1 AND i.is_unique=1 AND c.name=N'id'
            AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal>0)=1)
        THROW 50908, 'CourseRegistration primary key does not match the expected single id key.', 1;

    IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id=@tableId) <> 2
       OR EXISTS (
          SELECT 1 FROM sys.foreign_keys fk
          WHERE fk.parent_object_id=@tableId
            AND (fk.is_disabled=1 OR fk.is_not_trusted=1 OR fk.delete_referential_action<>0 OR fk.update_referential_action<>0
                 OR (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id=fk.object_id)<>1
                 OR NOT EXISTS (
                    SELECT 1 FROM sys.foreign_key_columns fkc
                    JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
                    WHERE fkc.constraint_object_id=fk.object_id
                      AND ((pc.name=N'member_id' AND fkc.referenced_object_id=OBJECT_ID(N'dbo.users') AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'id')
                        OR (pc.name=N'class_id' AND fkc.referenced_object_id=OBJECT_ID(N'dbo.[class]') AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'id')))))
        THROW 50909, 'CourseRegistration foreign keys differ from the expected trusted member/class references.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns c ON c.object_id=fkc.parent_object_id AND c.column_id=fkc.parent_column_id WHERE fk.parent_object_id=@tableId AND c.name=N'member_id' AND fkc.referenced_object_id=OBJECT_ID(N'dbo.users') AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'id' AND fk.is_disabled=0 AND fk.is_not_trusted=0 AND fk.delete_referential_action=0 AND fk.update_referential_action=0)
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id JOIN sys.columns c ON c.object_id=fkc.parent_object_id AND c.column_id=fkc.parent_column_id WHERE fk.parent_object_id=@tableId AND c.name=N'class_id' AND fkc.referenced_object_id=OBJECT_ID(N'dbo.[class]') AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'id' AND fk.is_disabled=0 AND fk.is_not_trusted=0 AND fk.delete_referential_action=0 AND fk.update_referential_action=0)
        THROW 50910, 'The expected member_id and class_id foreign keys are not both present.', 1;

    -- This repair is for the reviewed legacy CHECK or a successful prior run. No additional
    -- CHECK constraints are expected on this table in the Phase 2 / 8.7C contract.
    IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=@tableId) <> 1
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@tableId AND name=N'ck_course_registration_status')
        THROW 50911, 'Unexpected CourseRegistration CHECK constraint set; inspect metadata before repair.', 1;

    DECLARE @checkDefinition NVARCHAR(MAX), @checkNorm NVARCHAR(MAX), @isLegacy BIT=0, @isTarget BIT=0;
    DECLARE @checkLength INT, @checkPosition INT, @checkDepth INT, @checkOutermost BIT;
    SELECT @checkDefinition=definition FROM sys.check_constraints WHERE parent_object_id=@tableId AND name=N'ck_course_registration_status';
    SET @checkNorm=LOWER(@checkDefinition);
    SET @checkNorm=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@checkNorm,N'[',N''),N']',N''),N' ',N''),CHAR(9),N''),CHAR(10),N'');
    SET @checkNorm=REPLACE(@checkNorm,CHAR(10),N'');
    SET @checkNorm=REPLACE(@checkNorm,CHAR(13),N'');
    -- Match the original migration: remove only parentheses enclosing the entire
    -- expression, retaining the inner grouping which determines AND/OR semantics.
    WHILE LEN(@checkNorm)>1 AND LEFT(@checkNorm,1)=N'(' AND RIGHT(@checkNorm,1)=N')'
    BEGIN
        SET @checkDepth=0;
        SET @checkOutermost=1;
        SET @checkLength=LEN(@checkNorm);
        SET @checkPosition=1;
        WHILE @checkPosition<=@checkLength
        BEGIN
            IF SUBSTRING(@checkNorm,@checkPosition,1)=N'(' SET @checkDepth+=1;
            ELSE IF SUBSTRING(@checkNorm,@checkPosition,1)=N')'
            BEGIN
                SET @checkDepth-=1;
                IF @checkDepth=0 AND @checkPosition<@checkLength
                BEGIN
                    SET @checkOutermost=0;
                    BREAK;
                END;
            END;
            SET @checkPosition+=1;
        END;
        IF @checkOutermost=1 AND @checkDepth=0
            SET @checkNorm=SUBSTRING(@checkNorm,2,LEN(@checkNorm)-2);
        ELSE BREAK;
    END;
    IF @checkNorm IN (N'statusin(''confirmed'',''cancelled'')', N'status=''confirmed''orstatus=''cancelled''', N'status=''cancelled''orstatus=''confirmed''') SET @isLegacy=1;
    IF @checkNorm IN (
        N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and(status<>''pending_payment''orpayment_deadlineisnotnull)',
        N'(statusin(''pending_payment'',''confirmed'',''cancelled'',''expired''))and((status<>''pending_payment'')or(payment_deadlineisnotnull))',
        N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and((status<>''pending_payment'')or(payment_deadlineisnotnull))',
        N'(statusin(''pending_payment'',''confirmed'',''cancelled'',''expired''))and(status<>''pending_payment''orpayment_deadlineisnotnull)',
        N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and((status<>''pending_payment''orpayment_deadlineisnotnull))',
        N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and(status<>''pending_payment''or(payment_deadlineisnotnull))'
    ) SET @isTarget=1;
    IF @isLegacy=0 AND @isTarget=0
        THROW 50912, 'ck_course_registration_status has an unexpected definition; no constraint will be dropped.', 1;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@tableId AND name=N'ck_course_registration_status' AND (is_disabled=1 OR is_not_trusted=1))
        THROW 50913, 'ck_course_registration_status must be enabled and trusted.', 1;

    -- Validate payment lookup metadata and ensure the payment rows do not need reconciliation.
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'purpose' AND system_type_id=TYPE_ID(N'varchar') AND is_nullable=0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@paymentId AND name=N'status' AND system_type_id=TYPE_ID(N'varchar') AND is_nullable=0)
        THROW 50914, 'Payment purpose/status columns do not match expected metadata.', 1;
    IF EXISTS (SELECT 1 FROM dbo.payment WHERE purpose='COURSE_REGISTRATION' AND status='PENDING')
        THROW 50915, 'Pending COURSE_REGISTRATION payments exist; reconcile them before repair.', 1;

    -- The stated source state has one confirmed row, no pending registration and all deadlines NULL.
    IF (SELECT COUNT(*) FROM dbo.course_registration WHERE status='CONFIRMED') <> 1
       OR EXISTS (SELECT 1 FROM dbo.course_registration WHERE status='PENDING_PAYMENT')
       OR EXISTS (SELECT 1 FROM dbo.course_registration WHERE payment_deadline IS NOT NULL)
       OR EXISTS (SELECT 1 FROM dbo.course_registration WHERE status NOT IN ('CONFIRMED','CANCELLED'))
        THROW 50916, 'Registration data differs from the reviewed partial state; no rows were changed.', 1;

    -- Convert only if needed. With zero non-NULL values this is lossless; the explicit
    -- fractional-precision test also protects a changed database before the ALTER runs.
    IF EXISTS (SELECT 1 FROM dbo.course_registration WHERE payment_deadline IS NOT NULL AND DATEPART(NANOSECOND,payment_deadline) % 1000 <> 0)
        THROW 50917, 'payment_deadline contains 100-nanosecond values that would be rounded by DATETIME2(6).', 1;

    -- Dependency restrictions below are specific to ALTER COLUMN and apply only when
    -- converting DATETIME2(7) to DATETIME2(6). On a successful rerun at scale 6, the
    -- expected deadline indexes are allowed and no ALTER COLUMN is attempted.
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'payment_deadline' AND scale=7)
       AND (
           EXISTS (SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.object_id=@tableId AND c.name=N'payment_deadline')
        OR EXISTS (SELECT 1 FROM sys.default_constraints d WHERE d.parent_object_id=@tableId AND d.parent_column_id=@deadlineColumnId)
        OR EXISTS (SELECT 1 FROM sys.foreign_key_columns fkc WHERE fkc.parent_object_id=@tableId AND fkc.parent_column_id=@deadlineColumnId)
        OR EXISTS (SELECT 1 FROM sys.check_constraints cc WHERE cc.parent_object_id=@tableId AND LOWER(cc.definition) LIKE N'%payment_deadline%')
        OR EXISTS (SELECT 1 FROM sys.computed_columns cc WHERE cc.object_id=@tableId AND LOWER(cc.definition) LIKE N'%payment_deadline%')
        OR EXISTS (SELECT 1 FROM sys.sql_expression_dependencies d WHERE d.referenced_id=@tableId AND d.referenced_minor_id=@deadlineColumnId)
       )
        THROW 50918, 'Unexpected dependency references payment_deadline; inspect it instead of allowing an implicit rebuild or drop.', 1;

    -- The confirmed filtered unique index is historical data protection and is validated,
    -- never recreated or altered by this repair.
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id=@tableId AND i.name=N'ux_course_registration_confirmed'
          AND i.type=2 AND i.is_unique=1 AND i.is_unique_constraint=0 AND i.is_disabled=0 AND i.is_hypothetical=0
          AND i.ignore_dup_key=0 AND i.has_filter=1
          AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition,N'[',N''),N']',N''),N'(',N''),N')',N''),N' ',N'')) IN (N'status=''confirmed''', N'''confirmed''=status')
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)=2
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.is_included_column=1)=0
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=1 AND k.is_descending_key=0 AND c.name=N'member_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=2 AND k.is_descending_key=0 AND c.name=N'class_id')
    ) THROW 50919, 'ux_course_registration_confirmed is missing or differs from the reviewed filtered unique index.', 1;

    -- Match the original migration's guard against additional unique member/class indexes
    -- that could block valid re-registration after this repair.
    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id=@tableId AND i.is_unique=1 AND i.is_hypothetical=0
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)=2
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND c.name=N'member_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND c.name=N'class_id')
          AND (i.name NOT IN (N'ux_course_registration_confirmed',N'ux_course_registration_pending') OR i.has_filter<>1 OR i.is_disabled=1)
    ) THROW 50924, 'An additional unique member/class index may block valid re-registration; inspect it before repair.', 1;

    -- Any same-named index is either absent or must be the exact expected definition.
    IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id=@tableId AND i.name=N'ux_course_registration_pending'
       AND (i.type<>2 OR i.is_unique<>1 OR i.is_unique_constraint<>0 OR i.is_disabled=1 OR i.is_hypothetical=1 OR i.ignore_dup_key<>0 OR i.has_filter<>1
         OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition,N'[',N''),N']',N''),N'(',N''),N')',N''),N' ',N'')) NOT IN (N'status=''pending_payment''',N'''pending_payment''=status')
         OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)<>2 OR EXISTS(SELECT 1 FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.is_included_column=1)
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=1 AND k.is_descending_key=0 AND c.name=N'member_id')
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=2 AND k.is_descending_key=0 AND c.name=N'class_id')))
        THROW 50920, 'ux_course_registration_pending exists with incompatible metadata.', 1;

    IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id=@tableId AND i.name=N'ix_course_registration_expiry'
       AND (i.type<>2 OR i.is_unique<>0 OR i.is_unique_constraint<>0 OR i.is_disabled=1 OR i.is_hypothetical=1 OR i.has_filter=1
         OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)<>3 OR EXISTS(SELECT 1 FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.is_included_column=1)
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=1 AND k.is_descending_key=0 AND c.name=N'status')
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=2 AND k.is_descending_key=0 AND c.name=N'payment_deadline')
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=3 AND k.is_descending_key=0 AND c.name=N'id')))
        THROW 50921, 'ix_course_registration_expiry exists with incompatible metadata.', 1;

    IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id=@tableId AND i.name=N'ix_course_registration_class_deadline'
       AND (i.type<>2 OR i.is_unique<>0 OR i.is_unique_constraint<>0 OR i.is_disabled=1 OR i.is_hypothetical=1 OR i.has_filter=1
         OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal>0)<>3 OR EXISTS(SELECT 1 FROM sys.index_columns k WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.is_included_column=1)
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=1 AND k.is_descending_key=0 AND c.name=N'class_id')
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=2 AND k.is_descending_key=0 AND c.name=N'status')
         OR NOT EXISTS(SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE k.object_id=i.object_id AND k.index_id=i.index_id AND k.key_ordinal=3 AND k.is_descending_key=0 AND c.name=N'payment_deadline')))
        THROW 50922, 'ix_course_registration_class_deadline exists with incompatible metadata.', 1;

    -- Convert the nullable timestamp without changing its nullability or dropping dependencies.
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'payment_deadline' AND scale=7)
        ALTER TABLE dbo.course_registration ALTER COLUMN payment_deadline DATETIME2(6) NULL;

    -- Replace only the verified trusted legacy CHECK. DDL is transactional in SQL Server;
    -- any later failure rolls this drop and its replacement back together.
    IF @isLegacy=1
    BEGIN
        ALTER TABLE dbo.course_registration DROP CONSTRAINT ck_course_registration_status;
        ALTER TABLE dbo.course_registration WITH CHECK ADD CONSTRAINT ck_course_registration_status
            CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED')
                   AND (status <> 'PENDING_PAYMENT' OR payment_deadline IS NOT NULL));
        ALTER TABLE dbo.course_registration CHECK CONSTRAINT ck_course_registration_status;
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ux_course_registration_pending')
        CREATE UNIQUE INDEX ux_course_registration_pending ON dbo.course_registration(member_id ASC,class_id ASC) WHERE status='PENDING_PAYMENT';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ix_course_registration_expiry')
        CREATE INDEX ix_course_registration_expiry ON dbo.course_registration(status ASC,payment_deadline ASC,id ASC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ix_course_registration_class_deadline')
        CREATE INDEX ix_course_registration_class_deadline ON dbo.course_registration(class_id ASC,status ASC,payment_deadline ASC);

    -- Postconditions are checked before commit; no data rows are inserted, updated or deleted.
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=@tableId AND name=N'payment_deadline' AND system_type_id=TYPE_ID(N'datetime2') AND scale=6 AND is_nullable=1)
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@tableId AND name=N'ck_course_registration_status' AND is_disabled=0 AND is_not_trusted=0)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ux_course_registration_pending' AND is_unique=1 AND is_disabled=0 AND has_filter=1)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ux_course_registration_confirmed' AND is_unique=1 AND is_disabled=0 AND has_filter=1)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ix_course_registration_expiry' AND is_disabled=0)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=@tableId AND name=N'ix_course_registration_class_deadline' AND is_disabled=0)
        THROW 50923, 'A Phase 8.7C repair postcondition failed; all repair DDL will be rolled back.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

/*
READ-ONLY PREFLIGHT (run before this repair on the rehearsal database):

SELECT DB_NAME() AS database_name;
SELECT c.name, TYPE_NAME(c.user_type_id) AS data_type, c.max_length, c.precision, c.scale,
       c.is_nullable, c.is_identity, c.is_computed
FROM sys.columns c
WHERE c.object_id=OBJECT_ID(N'dbo.course_registration')
ORDER BY c.column_id;
SELECT status, COUNT_BIG(*) AS row_count, COUNT_BIG(payment_deadline) AS populated_deadlines
FROM dbo.course_registration GROUP BY status;
SELECT purpose, status, COUNT_BIG(*) AS payment_count
FROM dbo.payment WHERE purpose='COURSE_REGISTRATION' GROUP BY purpose,status;
SELECT cc.name, cc.definition, cc.is_disabled, cc.is_not_trusted
FROM sys.check_constraints cc WHERE cc.parent_object_id=OBJECT_ID(N'dbo.course_registration');
SELECT fk.name, pc.name AS local_column, OBJECT_SCHEMA_NAME(fkc.referenced_object_id) AS referenced_schema,
       OBJECT_NAME(fkc.referenced_object_id) AS referenced_table, COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id) AS referenced_column,
       fk.is_disabled, fk.is_not_trusted, fk.delete_referential_action_desc, fk.update_referential_action_desc
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
WHERE fk.parent_object_id=OBJECT_ID(N'dbo.course_registration');
SELECT i.name, i.type_desc, i.is_unique, i.is_unique_constraint, i.is_disabled, i.is_hypothetical,
       i.has_filter, i.filter_definition, i.ignore_dup_key, i.fill_factor, i.is_padded,
       c.name AS column_name, ic.key_ordinal, ic.is_descending_key, ic.is_included_column
FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id=OBJECT_ID(N'dbo.course_registration') ORDER BY i.name,ic.key_ordinal,ic.index_column_id;

POST-REPAIR VERIFICATION (read-only):
SELECT c.name, TYPE_NAME(c.user_type_id) AS data_type, c.scale, c.is_nullable
FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.course_registration') AND c.name=N'payment_deadline';
SELECT cc.name,cc.definition,cc.is_disabled,cc.is_not_trusted
FROM sys.check_constraints cc WHERE cc.parent_object_id=OBJECT_ID(N'dbo.course_registration');
-- The definition must be the same expression added by the original migration, and the
-- constraint must be enabled/trusted. The repair's precheck uses the original migration's
-- parenthesis-preserving normalization and accepts its exact recognized expression forms.
DECLARE @verifyCheck NVARCHAR(MAX), @verifyNorm NVARCHAR(MAX), @verifyDepth INT,
        @verifyPos INT, @verifyLen INT, @verifyOutermost BIT;
SELECT @verifyCheck=definition
FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID(N'dbo.course_registration') AND name=N'ck_course_registration_status';
SET @verifyNorm=LOWER(@verifyCheck);
SET @verifyNorm=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@verifyNorm,N'[',N''),N']',N''),N' ',N''),CHAR(9),N''),CHAR(10),N'');
SET @verifyNorm=REPLACE(@verifyNorm,CHAR(13),N'');
WHILE LEN(@verifyNorm)>1 AND LEFT(@verifyNorm,1)=N'(' AND RIGHT(@verifyNorm,1)=N')'
BEGIN
    SET @verifyDepth=0; SET @verifyOutermost=1; SET @verifyLen=LEN(@verifyNorm); SET @verifyPos=1;
    WHILE @verifyPos<=@verifyLen
    BEGIN
        IF SUBSTRING(@verifyNorm,@verifyPos,1)=N'(' SET @verifyDepth+=1;
        ELSE IF SUBSTRING(@verifyNorm,@verifyPos,1)=N')'
        BEGIN
            SET @verifyDepth-=1;
            IF @verifyDepth=0 AND @verifyPos<@verifyLen BEGIN SET @verifyOutermost=0; BREAK; END;
        END;
        SET @verifyPos+=1;
    END;
    IF @verifyOutermost=1 AND @verifyDepth=0 SET @verifyNorm=SUBSTRING(@verifyNorm,2,LEN(@verifyNorm)-2);
    ELSE BREAK;
END;
SELECT CASE WHEN cc.is_disabled=0 AND cc.is_not_trusted=0 AND @verifyNorm IN (
       N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and(status<>''pending_payment''orpayment_deadlineisnotnull)',
       N'(statusin(''pending_payment'',''confirmed'',''cancelled'',''expired''))and((status<>''pending_payment'')or(payment_deadlineisnotnull))',
       N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and((status<>''pending_payment'')or(payment_deadlineisnotnull))',
       N'(statusin(''pending_payment'',''confirmed'',''cancelled'',''expired''))and(status<>''pending_payment''orpayment_deadlineisnotnull)',
       N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and((status<>''pending_payment''orpayment_deadlineisnotnull))',
       N'statusin(''pending_payment'',''confirmed'',''cancelled'',''expired'')and(status<>''pending_payment''or(payment_deadlineisnotnull))'
       ) THEN N'PASS' ELSE N'FAIL' END AS phase_87c_check_contract,
       cc.definition,cc.is_disabled,cc.is_not_trusted
FROM sys.check_constraints cc
WHERE cc.parent_object_id=OBJECT_ID(N'dbo.course_registration')
  AND cc.name=N'ck_course_registration_status';
SELECT i.name,i.type_desc,i.is_unique,i.is_disabled,i.has_filter,i.filter_definition,
       i.is_unique_constraint,i.ignore_dup_key,c.name AS column_name,
       ic.key_ordinal,ic.is_descending_key,ic.is_included_column
FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id=OBJECT_ID(N'dbo.course_registration')
  AND i.name IN (N'ux_course_registration_confirmed',N'ux_course_registration_pending',N'ix_course_registration_expiry',N'ix_course_registration_class_deadline')
ORDER BY i.name,ic.key_ordinal,ic.index_column_id;

-- Rerun check: the original Phase 8.7C migration should now recognize payment_deadline
-- DATETIME2(6), the trusted target CHECK, and all four named indexes without rebuilding them.
*/
