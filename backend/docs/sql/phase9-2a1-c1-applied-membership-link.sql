-- Phase 9.2A.1-C, Checkpoint 1.1: persist the winning checkout attempt's Membership.
-- Apply after phase9-2a1-c-renewal-checkout-attempts.sql and before deploying code
-- that writes PaymentCheckoutAttempt.appliedMembership. No historical link is inferred.
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
    THROW 50991, 'Select SportCenterDB before applying the applied-membership link migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50992, 'Run the applied-membership link migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment_checkout_attempt', N'U') IS NULL
   OR OBJECT_ID(N'dbo.Membership', N'U') IS NULL
    THROW 50993, 'Apply Checkpoint 1 and the core Membership schema before this migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter:Phase9.2A.1-C:AppliedMembershipLink',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 30000;
    IF @lockResult < 0
        THROW 50994, 'Could not acquire the applied-membership migration lock.', 1;

    DECLARE @attemptObjectId INT = OBJECT_ID(N'dbo.payment_checkout_attempt');
    DECLARE @membershipObjectId INT = OBJECT_ID(N'dbo.Membership');
    DECLARE @linkAlreadyExists BIT = CASE
        WHEN COL_LENGTH(N'dbo.payment_checkout_attempt', N'applied_membership_id') IS NULL THEN 0 ELSE 1 END;

    -- Confirm the deployed Checkpoint 1 table and its required baseline objects before extension.
    IF EXISTS (SELECT 1 FROM (VALUES
            (N'id'), (N'payment_id'), (N'provider'), (N'transaction_reference'), (N'amount'),
            (N'created_at'), (N'expires_at'), (N'status'), (N'provider_transaction_id'),
            (N'provider_amount'), (N'provider_response_code'), (N'verified_at'), (N'reconciliation_reason')) expected(column_name)
        WHERE COL_LENGTH(N'dbo.payment_checkout_attempt', expected.column_name) IS NULL)
       OR (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id = @attemptObjectId) <> 5
       OR EXISTS (SELECT 1 FROM (VALUES
            (N'ck_payment_checkout_attempt_provider'),
            (N'ck_payment_checkout_attempt_reference_namespace'),
            (N'ck_payment_checkout_attempt_status'),
            (N'ck_payment_checkout_attempt_amount'),
            (N'ck_payment_checkout_attempt_expiry')) expected(name)
            WHERE NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                WHERE cc.parent_object_id = @attemptObjectId AND cc.name = expected.name
                  AND cc.is_disabled = 0 AND cc.is_not_trusted = 0))
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
            JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
            WHERE fk.parent_object_id = @attemptObjectId AND fk.name = N'fk_payment_checkout_attempt_payment'
              AND fk.referenced_object_id = OBJECT_ID(N'dbo.payment')
              AND child.name = N'payment_id' AND parent.name = N'id'
              AND fk.is_disabled = 0 AND fk.is_not_trusted = 0 AND fk.is_not_for_replication = 0
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = @attemptObjectId AND i.name = N'ux_payment_checkout_attempt_reference'
              AND i.type = 2 AND i.is_unique = 1 AND i.is_unique_constraint = 0
              AND i.is_primary_key = 0 AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 0
              AND c.name = N'transaction_reference' AND ic.key_ordinal = 1
              AND ic.is_descending_key = 0 AND ic.is_included_column = 0
              AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
                   AND x.index_id = i.index_id) = 1)
       OR NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.object_id = @attemptObjectId
            AND i.name = N'ix_payment_checkout_attempt_payment_created'
            AND i.type = 2 AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 0
            AND i.is_unique = 0 AND i.is_unique_constraint = 0 AND i.is_primary_key = 0
            AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
                 AND x.index_id = i.index_id) = 3
            AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
                 AND x.index_id = i.index_id AND x.key_ordinal > 0) = 3
            AND EXISTS (SELECT 1 FROM sys.index_columns x JOIN sys.columns c
                ON c.object_id = x.object_id AND c.column_id = x.column_id
                WHERE x.object_id = i.object_id AND x.index_id = i.index_id AND x.key_ordinal = 1
                  AND x.is_included_column = 0 AND x.is_descending_key = 0 AND c.name = N'payment_id')
            AND EXISTS (SELECT 1 FROM sys.index_columns x JOIN sys.columns c
                ON c.object_id = x.object_id AND c.column_id = x.column_id
                WHERE x.object_id = i.object_id AND x.index_id = i.index_id AND x.key_ordinal = 2
                  AND x.is_included_column = 0 AND x.is_descending_key = 0 AND c.name = N'created_at')
            AND EXISTS (SELECT 1 FROM sys.index_columns x JOIN sys.columns c
                ON c.object_id = x.object_id AND c.column_id = x.column_id
                WHERE x.object_id = i.object_id AND x.index_id = i.index_id AND x.key_ordinal = 3
                  AND x.is_included_column = 0 AND x.is_descending_key = 0 AND c.name = N'id'))
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = @attemptObjectId) <> 13 + @linkAlreadyExists
       OR EXISTS (SELECT expected.column_name FROM (VALUES
            (N'id', N'int', 4, 10, 0, 0, 1), (N'payment_id', N'int', 4, 10, 0, 0, 0),
            (N'provider', N'varchar', 20, 0, 0, 0, 0), (N'transaction_reference', N'varchar', 40, 0, 0, 0, 0),
            (N'amount', N'decimal', 9, 12, 2, 0, 0), (N'created_at', N'datetime2', 8, 26, 6, 0, 0),
            (N'expires_at', N'datetime2', 8, 26, 6, 0, 0), (N'status', N'varchar', 30, 0, 0, 0, 0),
            (N'provider_transaction_id', N'varchar', 100, 0, 0, 1, 0),
            (N'provider_amount', N'decimal', 9, 12, 2, 1, 0),
            (N'provider_response_code', N'varchar', 20, 0, 0, 1, 0),
            (N'verified_at', N'datetime2', 8, 26, 6, 1, 0),
            (N'reconciliation_reason', N'nvarchar', 1000, 0, 0, 1, 0))
            expected(column_name, type_name, max_length, precision_value, scale_value, is_nullable, is_identity)
            LEFT JOIN sys.columns c ON c.object_id = @attemptObjectId AND c.name = expected.column_name
            LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
            WHERE c.column_id IS NULL OR t.name <> expected.type_name OR c.max_length <> expected.max_length
               OR c.precision <> expected.precision_value OR c.scale <> expected.scale_value
               OR c.is_nullable <> expected.is_nullable OR c.is_identity <> expected.is_identity
               OR c.is_computed <> 0 OR c.default_object_id <> 0)
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints kc
            JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE kc.parent_object_id = @attemptObjectId AND kc.name = N'pk_payment_checkout_attempt'
              AND kc.type = N'PK' AND c.name = N'id' AND ic.key_ordinal = 1
              AND ic.is_descending_key = 0 AND ic.is_included_column = 0
              AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
                   AND x.index_id = kc.unique_index_id) = 1)
       OR (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id = @attemptObjectId) <> 1 + @linkAlreadyExists
       OR (SELECT COUNT(*) FROM sys.indexes WHERE object_id = @attemptObjectId AND index_id > 0) <> 3 + (2 * @linkAlreadyExists)
       OR (SELECT COUNT(*) FROM sys.identity_columns WHERE object_id = @attemptObjectId) <> 1
       OR TRY_CONVERT(DECIMAL(38,0), IDENT_SEED(N'dbo.payment_checkout_attempt')) <> 1
       OR TRY_CONVERT(DECIMAL(38,0), IDENT_INCR(N'dbo.payment_checkout_attempt')) <> 1
        THROW 50995, 'Checkpoint 1 payment_checkout_attempt schema is missing, incompatible, or partial.', 1;

    -- Checkpoint 1 CHECK definitions must still enforce the predicates this extension relies on.
    DECLARE @c1Checks TABLE (constraint_name SYSNAME PRIMARY KEY, normalized NVARCHAR(MAX));
    INSERT @c1Checks(constraint_name, normalized)
    SELECT cc.name, UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.definition,
        N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N''))
    FROM sys.check_constraints cc WHERE cc.parent_object_id = @attemptObjectId;
    UPDATE @c1Checks SET normalized = REPLACE(REPLACE(normalized, N'(', N''), N')', N'');
    DECLARE @c1Status NVARCHAR(MAX), @c1StatusResidual NVARCHAR(MAX);
    SELECT @c1Status = normalized FROM @c1Checks WHERE constraint_name = N'ck_payment_checkout_attempt_status';
    SET @c1StatusResidual = @c1Status;
    SET @c1StatusResidual = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@c1StatusResidual,
        N'STATUS=''CREATED''', N''), N'STATUS=''URL_ISSUED''', N''), N'STATUS=''FAILED''', N''),
        N'STATUS=''CAPTURED''', N''), N'STATUS=''RECONCILIATION_REQUIRED''', N'');
    SET @c1StatusResidual = REPLACE(@c1StatusResidual, N'OR', N'');
    IF EXISTS (SELECT 1 FROM @c1Checks WHERE constraint_name = N'ck_payment_checkout_attempt_provider'
               AND normalized <> N'PROVIDER=''VNPAY''')
       OR EXISTS (SELECT 1 FROM @c1Checks WHERE constraint_name = N'ck_payment_checkout_attempt_reference_namespace'
               AND normalized <> N'TRANSACTION_REFERENCELIKE''RNL%''')
       OR EXISTS (SELECT 1 FROM @c1Checks WHERE constraint_name = N'ck_payment_checkout_attempt_amount'
               AND normalized <> N'AMOUNT>0')
       OR EXISTS (SELECT 1 FROM @c1Checks WHERE constraint_name = N'ck_payment_checkout_attempt_expiry'
               AND normalized <> N'EXPIRES_AT>CREATED_AT')
       OR @c1Status IS NULL
       OR (@c1Status <> N'STATUSIN''CREATED'',''URL_ISSUED'',''FAILED'',''CAPTURED'',''RECONCILIATION_REQUIRED'''
           AND NOT (
               (LEN(@c1Status) - LEN(REPLACE(@c1Status, N'OR', N''))) / 2 = 4
               AND @c1StatusResidual = N''
               AND LEN(@c1Status) - LEN(REPLACE(@c1Status, N'STATUS=''CREATED''', N'')) = LEN(N'STATUS=''CREATED''')
               AND LEN(@c1Status) - LEN(REPLACE(@c1Status, N'STATUS=''URL_ISSUED''', N'')) = LEN(N'STATUS=''URL_ISSUED''')
               AND LEN(@c1Status) - LEN(REPLACE(@c1Status, N'STATUS=''FAILED''', N'')) = LEN(N'STATUS=''FAILED''')
               AND LEN(@c1Status) - LEN(REPLACE(@c1Status, N'STATUS=''CAPTURED''', N'')) = LEN(N'STATUS=''CAPTURED''')
               AND LEN(@c1Status) - LEN(REPLACE(@c1Status, N'STATUS=''RECONCILIATION_REQUIRED''', N''))
                   = LEN(N'STATUS=''RECONCILIATION_REQUIRED''')
           ))
        THROW 50995, 'A Checkpoint 1 CHECK predicate has an incompatible definition.', 1;

    -- Membership.id is the Integer identity PK in the JPA mapping and is the FK target.
    IF NOT EXISTS (SELECT 1 FROM sys.columns c
            JOIN sys.types t ON t.user_type_id = c.user_type_id
            JOIN sys.key_constraints kc ON kc.parent_object_id = c.object_id AND kc.type = N'PK'
            JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
                AND ic.column_id = c.column_id AND ic.key_ordinal = 1
            WHERE c.object_id = @membershipObjectId AND c.name = N'id' AND t.name = N'int'
              AND c.is_identity = 1 AND c.is_computed = 0 AND ic.is_included_column = 0
              AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
                   AND x.index_id = kc.unique_index_id) = 1)
        THROW 50996, 'dbo.Membership.id is not the expected INT identity primary key.', 1;

    -- Only a nullable INT column is accepted. No default, identity, computed column, or backfill.
    IF COL_LENGTH(N'dbo.payment_checkout_attempt', N'applied_membership_id') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment_checkout_attempt ADD applied_membership_id INT NULL;';

    IF NOT EXISTS (SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = @attemptObjectId AND c.name = N'applied_membership_id'
          AND t.name = N'int' AND c.max_length = 4 AND c.precision = 10 AND c.scale = 0
          AND c.is_nullable = 1 AND c.is_identity = 0 AND c.is_computed = 0
          AND c.default_object_id = 0)
       OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = @attemptObjectId) <> 14
        THROW 50997, 'applied_membership_id must be the only additive nullable INT column.', 1;

    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = @attemptObjectId
               AND name = N'fk_payment_checkout_attempt_applied_membership')
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
            JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
            WHERE fk.parent_object_id = @attemptObjectId
              AND fk.name = N'fk_payment_checkout_attempt_applied_membership'
              AND fk.referenced_object_id = @membershipObjectId
              AND child.name = N'applied_membership_id' AND parent.name = N'id'
              AND fk.is_disabled = 0 AND fk.is_not_trusted = 0 AND fk.is_not_for_replication = 0
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
            THROW 50998, 'The existing applied Membership foreign key has an incompatible definition.', 1;
    END
    ELSE
    BEGIN
        IF EXISTS (SELECT 1 FROM sys.foreign_key_columns fkc
            JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
            WHERE fkc.parent_object_id = @attemptObjectId AND c.name = N'applied_membership_id')
            THROW 50998, 'An unexpected foreign key already references applied_membership_id.', 1;
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment_checkout_attempt WITH CHECK ADD CONSTRAINT
            fk_payment_checkout_attempt_applied_membership FOREIGN KEY (applied_membership_id)
            REFERENCES dbo.Membership(id); ALTER TABLE dbo.payment_checkout_attempt
            CHECK CONSTRAINT fk_payment_checkout_attempt_applied_membership;';
    END;

    -- A winning attempt is unique both for its resulting Membership and for its parent Payment.
    -- Non-winning/reconciliation attempts keep applied_membership_id NULL and remain unrestricted.
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = @attemptObjectId
                   AND name = N'ux_payment_checkout_attempt_applied_membership')
        EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_payment_checkout_attempt_applied_membership
            ON dbo.payment_checkout_attempt(applied_membership_id)
            WHERE applied_membership_id IS NOT NULL;';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = @attemptObjectId
                   AND name = N'ux_payment_checkout_attempt_applied_payment')
        EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_payment_checkout_attempt_applied_payment
            ON dbo.payment_checkout_attempt(payment_id)
            WHERE applied_membership_id IS NOT NULL;';

    -- Validate FK exactly, including trust, enablement, target, actions, and single-column shape.
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = @attemptObjectId
          AND fk.name = N'fk_payment_checkout_attempt_applied_membership'
          AND fk.referenced_object_id = @membershipObjectId
          AND child.name = N'applied_membership_id' AND parent.name = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0 AND fk.is_not_for_replication = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
        THROW 50999, 'The applied Membership foreign key is missing, disabled, untrusted, or incompatible.', 1;

    DECLARE @expectedIndexes TABLE (index_name SYSNAME, column_name SYSNAME);
    INSERT @expectedIndexes VALUES
        (N'ux_payment_checkout_attempt_applied_membership', N'applied_membership_id'),
        (N'ux_payment_checkout_attempt_applied_payment', N'payment_id');
    IF EXISTS (SELECT 1 FROM @expectedIndexes e WHERE NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = @attemptObjectId AND i.name = e.index_name AND i.type = 2
          AND i.is_unique = 1 AND i.is_unique_constraint = 0 AND i.is_primary_key = 0
          AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 1
          AND c.name = e.column_name AND ic.key_ordinal = 1 AND ic.is_descending_key = 0
          AND ic.is_included_column = 0
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id) = 1
          AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), N'[', N''), N']', N''),
              N' ', N''), N'(', N''), N')', N'') = N'APPLIED_MEMBERSHIP_IDISNOTNULL'
    ))
        THROW 51000, 'A filtered applied-membership uniqueness index is missing or incompatible.', 1;
    IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id = @attemptObjectId) <> 2
       OR (SELECT COUNT(*) FROM sys.indexes WHERE object_id = @attemptObjectId AND index_id > 0) <> 5
        THROW 51001, 'The extended checkout-attempt table has unexpected foreign keys or indexes.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
