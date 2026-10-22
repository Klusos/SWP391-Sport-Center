-- Phase 9.2A.1-C, Checkpoint 1: durable renewal checkout attempts and explicit next-period Auto Renew choice.
-- Apply after phase9-2a1-membership-renewal-scheduler.sql and before deploying the Checkpoint 1 backend.
-- This is additive. It creates no payment attempts and does not change existing Payment or Membership rows.
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
    THROW 50971, 'Select SportCenterDB before applying the renewal checkout-attempt migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50972, 'Run the renewal checkout-attempt migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
    THROW 50973, 'Apply the Phase 9.2A.1-A/B migrations before this migration.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter:Phase9.2A.1-C:CheckoutAttempts',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 30000;
    IF @lockResult < 0
        THROW 50974, 'Could not acquire the renewal checkout-attempt migration lock.', 1;

    -- Validate the Stage A/B fields on which this migration depends.
    IF COL_LENGTH(N'dbo.payment', N'method') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'purpose') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'status') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'renewal_membership_id') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'purchased_months') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'monthly_price_snapshot') IS NULL
        THROW 50975, 'Required Stage A/B payment columns are missing.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'renewal_membership_id'
          AND t.name = N'int' AND c.is_nullable = 1 AND c.is_identity = 0 AND c.is_computed = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'method'
          AND t.name = N'varchar' AND c.max_length = 20 AND c.is_nullable = 1
          AND c.is_identity = 0 AND c.is_computed = 0 AND c.default_object_id = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment')
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.membership')
          AND child.name = N'renewal_membership_id' AND parent.name = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.name = N'ux_payment_renewal_membership'
          AND i.is_unique = 1 AND i.is_disabled = 0 AND i.has_filter = 1
          AND c.name = N'renewal_membership_id' AND ic.key_ordinal = 1
          AND ic.is_included_column = 0 AND i.is_hypothetical = 0
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.key_ordinal > 0) = 1
          AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N'')
              = N'RENEWAL_MEMBERSHIP_IDISNOTNULL'
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_method'
          AND is_disabled = 0 AND is_not_trusted = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_membership_purchase_snapshot'
          AND is_disabled = 0 AND is_not_trusted = 0
    )
        THROW 50976, 'Required Stage A/B payment mapping, trusted CHECK, FK, or unique index is missing or incompatible.', 1;

    -- Reject an incompatible pre-existing choice column; otherwise add it without data backfill.
    IF COL_LENGTH(N'dbo.payment', N'renewal_auto_renew_choice') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD renewal_auto_renew_choice BIT NULL;';
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'renewal_auto_renew_choice'
          AND t.name = N'bit' AND c.is_nullable = 1 AND c.is_identity = 0 AND c.is_computed = 0
          AND c.default_object_id = 0
    )
        THROW 50977, 'dbo.payment.renewal_auto_renew_choice must be nullable BIT without a default.', 1;
    IF EXISTS (
        SELECT 1 FROM sys.check_constraints cc
        WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment')
          AND cc.name <> N'ck_payment_renewal_auto_renew_choice'
          AND CHARINDEX(N'RENEWAL_AUTO_RENEW_CHOICE', UPPER(cc.definition)) > 0
    ) OR EXISTS (
        SELECT 1 FROM sys.index_columns ic
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'renewal_auto_renew_choice'
    )
        THROW 50987, 'An unexpected Payment CHECK or index references renewal_auto_renew_choice.', 1;

    DECLARE @choiceCheckId INT = OBJECT_ID(N'dbo.payment');
    DECLARE @choiceCheckDefinition NVARCHAR(MAX);
    SELECT @choiceCheckDefinition = cc.definition FROM sys.check_constraints cc
    WHERE cc.parent_object_id = @choiceCheckId AND cc.name = N'ck_payment_renewal_auto_renew_choice';
    IF @choiceCheckDefinition IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_renewal_auto_renew_choice
            CHECK (renewal_auto_renew_choice IS NULL OR
                  (renewal_membership_id IS NOT NULL AND purpose = ''MEMBERSHIP''));';
    SELECT @choiceCheckDefinition = cc.definition FROM sys.check_constraints cc
    WHERE cc.parent_object_id = @choiceCheckId AND cc.name = N'ck_payment_renewal_auto_renew_choice';
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = @choiceCheckId
               AND name = N'ck_payment_renewal_auto_renew_choice'
               AND (is_disabled = 1 OR is_not_trusted = 1))
        THROW 50978, 'The renewal Auto Renew choice CHECK is disabled or untrusted.', 1;
    DECLARE @choiceNormalized NVARCHAR(MAX) = UPPER(@choiceCheckDefinition);
    SET @choiceNormalized = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@choiceNormalized,
        N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'');
    WHILE LEN(@choiceNormalized) >= 2 AND LEFT(@choiceNormalized, 1) = N'(' AND RIGHT(@choiceNormalized, 1) = N')'
        SET @choiceNormalized = SUBSTRING(@choiceNormalized, 2, LEN(@choiceNormalized) - 2);
    IF @choiceNormalized NOT IN (
        N'RENEWAL_AUTO_RENEW_CHOICEISNULLOR(RENEWAL_MEMBERSHIP_IDISNOTNULLANDPURPOSE=''MEMBERSHIP'')',
        N'RENEWAL_AUTO_RENEW_CHOICEISNULLORRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURPOSE=''MEMBERSHIP''',
        N'(RENEWAL_AUTO_RENEW_CHOICEISNULL)OR((RENEWAL_MEMBERSHIP_IDISNOTNULL)AND(PURPOSE=''MEMBERSHIP''))'
    )
        THROW 50979, 'The renewal Auto Renew choice CHECK has an unexpected definition.', 1;

    DECLARE @attemptExists BIT = CASE WHEN OBJECT_ID(N'dbo.payment_checkout_attempt', N'U') IS NULL THEN 0 ELSE 1 END;
    IF @attemptExists = 0
    BEGIN
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.payment_checkout_attempt (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_payment_checkout_attempt PRIMARY KEY,
                payment_id INT NOT NULL,
                provider VARCHAR(20) NOT NULL,
                transaction_reference VARCHAR(40) NOT NULL,
                amount DECIMAL(12,2) NOT NULL,
                created_at DATETIME2(6) NOT NULL,
                expires_at DATETIME2(6) NOT NULL,
                status VARCHAR(30) NOT NULL,
                provider_transaction_id VARCHAR(100) NULL,
                provider_amount DECIMAL(12,2) NULL,
                provider_response_code VARCHAR(20) NULL,
                verified_at DATETIME2(6) NULL,
                reconciliation_reason NVARCHAR(500) NULL,
                CONSTRAINT fk_payment_checkout_attempt_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
                CONSTRAINT ck_payment_checkout_attempt_provider CHECK (provider = ''VNPAY''),
                CONSTRAINT ck_payment_checkout_attempt_reference_namespace CHECK (transaction_reference LIKE ''RNL%''),
                CONSTRAINT ck_payment_checkout_attempt_status CHECK (status IN (''CREATED'', ''URL_ISSUED'', ''FAILED'', ''CAPTURED'', ''RECONCILIATION_REQUIRED'')),
                CONSTRAINT ck_payment_checkout_attempt_amount CHECK (amount > 0),
                CONSTRAINT ck_payment_checkout_attempt_expiry CHECK (expires_at > created_at)
            );
            CREATE UNIQUE INDEX ux_payment_checkout_attempt_reference
                ON dbo.payment_checkout_attempt(transaction_reference);
            CREATE INDEX ix_payment_checkout_attempt_payment_created
                ON dbo.payment_checkout_attempt(payment_id, created_at, id);';
    END;

    -- Exact table shape validation makes a partial or hand-built object fail closed on rerun.
    -- SQL Server column metadata reports INT as (precision 10, scale 0),
    -- DECIMAL(12,2) as (12,2), character types with precision 0, and
    -- DATETIME2(6) as (precision 26, scale 6) with an 8-byte max_length.
    IF EXISTS (
        SELECT expected.column_name FROM (VALUES
            (N'id', N'int', 4, 10, 0, 0, 1),
            (N'payment_id', N'int', 4, 10, 0, 0, 0),
            (N'provider', N'varchar', 20, 0, 0, 0, 0),
            (N'transaction_reference', N'varchar', 40, 0, 0, 0, 0),
            (N'amount', N'decimal', 9, 12, 2, 0, 0),
            (N'created_at', N'datetime2', 8, 26, 6, 0, 0),
            (N'expires_at', N'datetime2', 8, 26, 6, 0, 0),
            (N'status', N'varchar', 30, 0, 0, 0, 0),
            (N'provider_transaction_id', N'varchar', 100, 0, 0, 1, 0),
            (N'provider_amount', N'decimal', 9, 12, 2, 1, 0),
            (N'provider_response_code', N'varchar', 20, 0, 0, 1, 0),
            (N'verified_at', N'datetime2', 8, 26, 6, 1, 0),
            (N'reconciliation_reason', N'nvarchar', 1000, 0, 0, 1, 0)
        ) expected(column_name, type_name, max_length, precision_value, scale_value, is_nullable, is_identity)
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.payment_checkout_attempt') AND c.name = expected.column_name
        LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL OR t.name <> expected.type_name OR c.max_length <> expected.max_length
           OR c.precision <> expected.precision_value OR c.scale <> expected.scale_value
           OR c.is_nullable <> expected.is_nullable OR c.is_identity <> expected.is_identity OR c.is_computed <> 0
           OR c.default_object_id <> 0
    ) OR EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND c.name NOT IN (N'id', N'payment_id', N'provider', N'transaction_reference', N'amount', N'created_at',
              N'expires_at', N'status', N'provider_transaction_id', N'provider_amount', N'provider_response_code',
              N'verified_at', N'reconciliation_reason')
    ) OR (SELECT COUNT(*) FROM sys.identity_columns
          WHERE object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')) <> 1
       OR TRY_CONVERT(DECIMAL(38,0), IDENT_SEED(N'dbo.payment_checkout_attempt')) <> 1
       OR TRY_CONVERT(DECIMAL(38,0), IDENT_INCR(N'dbo.payment_checkout_attempt')) <> 1
        THROW 50980, 'Existing payment_checkout_attempt columns do not match the expected schema.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.key_constraints kc
        JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE kc.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND kc.name = N'pk_payment_checkout_attempt' AND kc.type = N'PK'
          AND c.name = N'id' AND ic.key_ordinal = 1
          AND ic.is_descending_key = 0 AND ic.is_included_column = 0
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
               AND x.index_id = kc.unique_index_id) = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND fk.name = N'fk_payment_checkout_attempt_payment'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.payment')
          AND child.name = N'payment_id' AND parent.name = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.is_not_for_replication = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND i.index_id = (SELECT kc.unique_index_id FROM sys.key_constraints kc
                            WHERE kc.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
                              AND kc.name = N'pk_payment_checkout_attempt')
          AND i.type = 1 AND i.is_primary_key = 1
          AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 0
    ) OR (SELECT COUNT(*) FROM sys.indexes
          WHERE object_id = OBJECT_ID(N'dbo.payment_checkout_attempt') AND index_id > 0) <> 3
        THROW 50981, 'The checkout-attempt primary key or trusted Payment foreign key is missing or incompatible.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND i.name = N'ux_payment_checkout_attempt_reference' AND i.type = 2
          AND i.is_unique = 1 AND i.is_unique_constraint = 0 AND i.is_primary_key = 0 AND i.is_disabled = 0
          AND i.is_hypothetical = 0 AND i.has_filter = 0
          AND ic.key_ordinal = 1 AND c.name = N'transaction_reference'
          AND ic.is_descending_key = 0 AND ic.is_included_column = 0
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id AND x.index_id = i.index_id) = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
          AND i.name = N'ix_payment_checkout_attempt_payment_created' AND i.type = 2
          AND i.is_disabled = 0 AND i.is_unique = 0 AND i.is_unique_constraint = 0 AND i.is_primary_key = 0
          AND i.is_hypothetical = 0 AND i.has_filter = 0
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id AND x.index_id = i.index_id) = 3
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id AND x.index_id = i.index_id
               AND x.key_ordinal > 0) = 3
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 1 AND ic.is_included_column = 0
                        AND ic.is_descending_key = 0 AND c.name = N'payment_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 2 AND ic.is_included_column = 0
                        AND ic.is_descending_key = 0 AND c.name = N'created_at')
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 3 AND ic.is_included_column = 0
                        AND ic.is_descending_key = 0 AND c.name = N'id')
    )
        THROW 50982, 'A required checkout-attempt unique or lookup index is missing or incompatible.', 1;

    -- Verify CHECK names, trust/enablement, and definitions. Normalization allows SQL Server's
    -- brackets, whitespace, redundant outer parentheses, and known IN-to-OR status serialization.
    IF EXISTS (
        SELECT 1 FROM (VALUES
            (N'ck_payment_checkout_attempt_provider'), (N'ck_payment_checkout_attempt_reference_namespace'),
            (N'ck_payment_checkout_attempt_status'),
            (N'ck_payment_checkout_attempt_amount'), (N'ck_payment_checkout_attempt_expiry')
        ) expected(name)
        WHERE NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
            WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
              AND cc.name = expected.name AND cc.is_disabled = 0 AND cc.is_not_trusted = 0)
    )
        THROW 50983, 'A required checkout-attempt CHECK is missing, disabled, or untrusted.', 1;
    IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')) <> 5
       OR (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')) <> 1
        THROW 50986, 'The checkout-attempt table contains unexpected CHECK constraints or foreign keys.', 1;

    DECLARE @checks TABLE (constraint_name SYSNAME PRIMARY KEY, normalized NVARCHAR(MAX));
    INSERT @checks(constraint_name, normalized)
    SELECT cc.name,
        UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.definition,
            N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N''))
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt');
    -- Each allowed CHECK is a simple comparison, except status which is a
    -- disjunction of equality atoms. Parentheses around these exact atoms,
    -- literals, or the whole disjunction do not change their logic.
    UPDATE @checks SET normalized = REPLACE(REPLACE(normalized, N'(', N''), N')', N'');
    DECLARE @statusCheck NVARCHAR(MAX);
    DECLARE @statusResidual NVARCHAR(MAX);
    SELECT @statusCheck = normalized FROM @checks
    WHERE constraint_name = N'ck_payment_checkout_attempt_status';
    SET @statusResidual = @statusCheck;
    SET @statusResidual = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@statusResidual,
        N'STATUS=''CREATED''', N''), N'STATUS=''URL_ISSUED''', N''),
        N'STATUS=''FAILED''', N''), N'STATUS=''CAPTURED''', N''),
        N'STATUS=''RECONCILIATION_REQUIRED''', N'');
    SET @statusResidual = REPLACE(@statusResidual, N'OR', N'');
    IF EXISTS (SELECT 1 FROM @checks WHERE constraint_name = N'ck_payment_checkout_attempt_provider'
               AND normalized <> N'PROVIDER=''VNPAY''')
       OR EXISTS (SELECT 1 FROM @checks WHERE constraint_name = N'ck_payment_checkout_attempt_reference_namespace'
               AND normalized <> N'TRANSACTION_REFERENCELIKE''RNL%''')
       OR EXISTS (SELECT 1 FROM @checks WHERE constraint_name = N'ck_payment_checkout_attempt_amount'
               AND normalized <> N'AMOUNT>0')
       OR EXISTS (SELECT 1 FROM @checks WHERE constraint_name = N'ck_payment_checkout_attempt_expiry'
               AND normalized <> N'EXPIRES_AT>CREATED_AT')
       OR @statusCheck IS NULL
       OR (@statusCheck <> N'STATUSIN''CREATED'',''URL_ISSUED'',''FAILED'',''CAPTURED'',''RECONCILIATION_REQUIRED'''
           AND NOT (
               -- SQL Server can expand IN into OR predicates and reorder them.
               -- Require the exact five approved equalities, each once, and exactly four ORs.
               (LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'OR', N''))) / 2 = 4
               AND @statusResidual = N''
               AND LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'STATUS=''CREATED''', N''))
                   = LEN(N'STATUS=''CREATED''')
               AND LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'STATUS=''URL_ISSUED''', N''))
                   = LEN(N'STATUS=''URL_ISSUED''')
               AND LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'STATUS=''FAILED''', N''))
                   = LEN(N'STATUS=''FAILED''')
               AND LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'STATUS=''CAPTURED''', N''))
                   = LEN(N'STATUS=''CAPTURED''')
               AND LEN(@statusCheck) - LEN(REPLACE(@statusCheck, N'STATUS=''RECONCILIATION_REQUIRED''', N''))
                   = LEN(N'STATUS=''RECONCILIATION_REQUIRED''')
           ))
        THROW 50984, 'A checkout-attempt CHECK has an unexpected definition.', 1;

    -- The UNIQUE reference constraint must remain global across attempts, including failures/captures.
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_checkout_attempt')
               AND name = N'ux_payment_checkout_attempt_reference' AND has_filter = 1)
        THROW 50985, 'Checkout transaction references must be unique without a filter.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
