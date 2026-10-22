-- Phase 9.2A.1-B: permit method-neutral, pending renewal requests.
-- Apply after phase9-2a1-membership-renewal-foundation.sql and before deploying Stage B.
-- No existing Payment values are changed. Review, back up, stop application writers, then run manually.
-- If definition validation fails, this read-only diagnostic shows the exact constraint SQL:
-- SELECT cc.name, cc.is_disabled, cc.is_not_trusted, cc.definition
-- FROM sys.check_constraints AS cc
-- WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment') AND cc.name = N'ck_payment_method';
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
    THROW 50951, 'Select SportCenterDB before applying the renewal scheduler migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50952, 'Run the renewal scheduler migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 50953, 'Expected dbo.payment before applying the renewal scheduler migration.', 1;
IF COL_LENGTH(N'dbo.payment', N'method') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'purpose') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'status') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'renewal_membership_id') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'purchased_months') IS NULL
   OR COL_LENGTH(N'dbo.payment', N'monthly_price_snapshot') IS NULL
    THROW 50954, 'Required payment columns are missing; apply prerequisite migrations first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter:Phase9.2A.1-B:RenewalMethod',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 30000;
    IF @lockResult < 0
        THROW 50955, 'Could not acquire the renewal scheduler migration lock.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'method'
          AND (t.name <> N'varchar' OR c.max_length <> 20 OR c.is_identity <> 0 OR c.is_computed <> 0
               OR c.default_object_id <> 0)
    )
        THROW 50956, 'dbo.payment.method must be VARCHAR(20) without identity, computed expression, or default.', 1;

    DECLARE @methodNullable BIT;
    SELECT @methodNullable = c.is_nullable
    FROM sys.columns c WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'method';

    DECLARE @checkDefinition NVARCHAR(MAX);
    SELECT @checkDefinition = cc.definition
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment') AND cc.name = N'ck_payment_method';
    IF @checkDefinition IS NULL
        THROW 50957, 'Expected dbo.payment.ck_payment_method; inspect the existing payment schema.', 1;

    DECLARE @normalized NVARCHAR(MAX) = UPPER(@checkDefinition);
    SET @normalized = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@normalized,
        N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'');
    WHILE LEN(@normalized) >= 2 AND LEFT(@normalized, 1) = N'(' AND RIGHT(@normalized, 1) = N')'
        SET @normalized = SUBSTRING(@normalized, 2, LEN(@normalized) - 2);

    DECLARE @oldIn1 NVARCHAR(MAX) = N'METHODIN(''ONLINE'',''COUNTER'')';
    DECLARE @oldOr1 NVARCHAR(MAX) = N'METHOD=''ONLINE''ORMETHOD=''COUNTER''';
    DECLARE @oldOr2 NVARCHAR(MAX) = N'METHOD=''COUNTER''ORMETHOD=''ONLINE''';

    DECLARE @newCanonical NVARCHAR(MAX) =
        N'(METHODISNOTNULLANDMETHODIN(''ONLINE'',''COUNTER''))OR(METHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT)';
    DECLARE @newExpandedA NVARCHAR(MAX) =
        N'(METHODISNOTNULLAND(METHOD=''ONLINE''ORMETHOD=''COUNTER''))OR(METHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT)';
    DECLARE @newExpandedB NVARCHAR(MAX) =
        N'(METHODISNOTNULLAND(METHOD=''COUNTER''ORMETHOD=''ONLINE''))OR(METHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT)';
    DECLARE @newExpandedScalarA NVARCHAR(MAX) =
        N'(METHODISNOTNULLAND(METHOD=''ONLINE''ORMETHOD=''COUNTER''))OR(METHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT)';
    DECLARE @newExpandedScalarB NVARCHAR(MAX) =
        N'(METHODISNOTNULLAND(METHOD=''COUNTER''ORMETHOD=''ONLINE''))OR(METHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT)';
    DECLARE @newSerializedA NVARCHAR(MAX) =
        N'METHODISNOTNULLAND(METHOD=''ONLINE''ORMETHOD=''COUNTER'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newSerializedB NVARCHAR(MAX) =
        N'METHODISNOTNULLAND(METHOD=''COUNTER''ORMETHOD=''ONLINE'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newSerializedNoScalarA NVARCHAR(MAX) =
        N'METHODISNOTNULLAND(METHOD=''ONLINE''ORMETHOD=''COUNTER'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newSerializedNoScalarB NVARCHAR(MAX) =
        N'METHODISNOTNULLAND(METHOD=''COUNTER''ORMETHOD=''ONLINE'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newCanonicalNoScalar NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHODIN(''ONLINE'',''COUNTER'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newCanonicalScalar NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHODIN(''ONLINE'',''COUNTER'')ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newExpandedNoGroupScalarA NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHOD=''ONLINE''ORMETHOD=''COUNTER''ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newExpandedNoGroupScalarB NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHOD=''COUNTER''ORMETHOD=''ONLINE''ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=(1)ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>(0)ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newExpandedNoGroupA NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHOD=''ONLINE''ORMETHOD=''COUNTER''ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';
    DECLARE @newExpandedNoGroupB NVARCHAR(MAX) =
        N'METHODISNOTNULLANDMETHOD=''COUNTER''ORMETHOD=''ONLINE''ORMETHODISNULLANDPURPOSE=''MEMBERSHIP''ANDSTATUSIN(''PENDING'',''CANCELLED'')ANDRENEWAL_MEMBERSHIP_IDISNOTNULLANDPURCHASED_MONTHSISNOTNULLANDPURCHASED_MONTHS=1ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT';

    DECLARE @isExpectedOld BIT = CASE WHEN @normalized IN (@oldIn1, @oldOr1, @oldOr2) THEN 1 ELSE 0 END;
    -- SQL Server may serialize this exact IN-list as an OR in either order, with redundant
    -- predicate parentheses. Canonicalize only those exact forms; all other logic remains checked.
    DECLARE @normalizedNew NVARCHAR(MAX) = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@normalized,
        N'(STATUS=''PENDING''ORSTATUS=''CANCELLED'')', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=''CANCELLED''ORSTATUS=''PENDING'')', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=(''PENDING'')ORSTATUS=(''CANCELLED''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=(''CANCELLED'')ORSTATUS=(''PENDING''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=''PENDING'')OR(STATUS=''CANCELLED''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=''CANCELLED'')OR(STATUS=''PENDING''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=(''PENDING''))OR(STATUS=(''CANCELLED'')))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=(''CANCELLED''))OR(STATUS=(''PENDING'')))', N'STATUSIN(''PENDING'',''CANCELLED'')');

    DECLARE @isExpectedNew BIT = CASE WHEN @normalizedNew IN (@newCanonical, @newExpandedA, @newExpandedB,
        @newExpandedScalarA, @newExpandedScalarB, @newSerializedA, @newSerializedB,
        @newSerializedNoScalarA, @newSerializedNoScalarB, @newCanonicalNoScalar, @newCanonicalScalar,
        @newExpandedNoGroupScalarA, @newExpandedNoGroupScalarB, @newExpandedNoGroupA, @newExpandedNoGroupB)
        THEN 1 ELSE 0 END;
    -- A prior revision of this Stage B migration allowed NULL only while PENDING.
    -- Recognize only its exact allowlisted forms so rerunning this corrected script upgrades that rule safely.
    DECLARE @legacyNormalized NVARCHAR(MAX) = REPLACE(REPLACE(REPLACE(@normalizedNew,
        N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        N'(STATUS=''PENDING'')', N'STATUS=''PENDING'''),
        N'STATUS=(''PENDING'')', N'STATUS=''PENDING''');
    DECLARE @isExpectedLegacyNew BIT = CASE WHEN @legacyNormalized IN (
        REPLACE(@newCanonical, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedScalarA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedScalarB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newSerializedA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newSerializedB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newSerializedNoScalarA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newSerializedNoScalarB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newCanonicalNoScalar, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newCanonicalScalar, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedNoGroupScalarA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedNoGroupScalarB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedNoGroupA, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''),
        REPLACE(@newExpandedNoGroupB, N'STATUSIN(''PENDING'',''CANCELLED'')', N'STATUS=''PENDING'''))
        THEN 1 ELSE 0 END;

    IF EXISTS (SELECT 1 FROM sys.check_constraints cc
               WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment') AND cc.name = N'ck_payment_method'
                 AND (cc.is_disabled = 1 OR cc.is_not_trusted = 1))
        THROW 50958, 'dbo.payment.ck_payment_method is disabled or untrusted.', 1;
    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
          AND name = N'ck_payment_membership_purchase_snapshot'
          AND is_disabled = 0 AND is_not_trusted = 0
    )
        THROW 50965, 'The Phase 9.2A membership purchase snapshot CHECK is missing, disabled, or untrusted.', 1;
    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment') AND fk.name = N'fk_payment_renewal_membership'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.membership')
          AND child.name = N'renewal_membership_id' AND parent.name = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    )
        THROW 50966, 'The Stage A renewal membership FK is missing, untrusted, disabled, or incompatible.', 1;
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.name = N'ux_payment_renewal_membership'
          AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 1
          AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'renewal_membership_id'
          AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N'')
              = N'RENEWAL_MEMBERSHIP_IDISNOTNULL'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.key_ordinal > 0) = 1
    )
        THROW 50967, 'The Stage A filtered unique renewal-payment index is missing or incompatible.', 1;

    IF @methodNullable = 0
    BEGIN
        IF @isExpectedOld <> 1
            THROW 50959, 'The existing ck_payment_method definition is not the known ONLINE/COUNTER rule.', 1;
        IF EXISTS (
            SELECT 1 FROM sys.check_constraints cc
            WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment')
              AND cc.name <> N'ck_payment_method'
              AND CHARINDEX(N'METHOD', UPPER(cc.definition)) > 0
        )
            THROW 50960, 'An additional payment CHECK constraint may constrain renewal methods; inspect before proceeding.', 1;
        IF EXISTS (
            SELECT 1 FROM sys.index_columns ic
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE ic.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'method'
        )
            THROW 50961, 'An index references payment.method; inspect its compatibility before changing nullability.', 1;

        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_method;';
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ALTER COLUMN method VARCHAR(20) NULL;';
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_method CHECK (
            (method IS NOT NULL AND method IN (''ONLINE'', ''COUNTER''))
            OR (method IS NULL AND purpose = ''MEMBERSHIP'' AND status IN (''PENDING'', ''CANCELLED'')
                AND renewal_membership_id IS NOT NULL AND purchased_months IS NOT NULL AND purchased_months = 1
                AND monthly_price_snapshot IS NOT NULL AND monthly_price_snapshot > 0 AND amount = monthly_price_snapshot)
        );';
    END
    ELSE IF @isExpectedNew <> 1
    BEGIN
        IF @isExpectedLegacyNew <> 1
            THROW 50962, 'Nullable dbo.payment.method exists with an unexpected ck_payment_method definition.', 1;

        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_method;';
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_method CHECK (
            (method IS NOT NULL AND method IN (''ONLINE'', ''COUNTER''))
            OR (method IS NULL AND purpose = ''MEMBERSHIP'' AND status IN (''PENDING'', ''CANCELLED'')
                AND renewal_membership_id IS NOT NULL AND purchased_months IS NOT NULL AND purchased_months = 1
                AND monthly_price_snapshot IS NOT NULL AND monthly_price_snapshot > 0 AND amount = monthly_price_snapshot)
        );';
    END

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'method'
          AND t.name = N'varchar' AND c.max_length = 20 AND c.is_nullable = 1
          AND c.is_identity = 0 AND c.is_computed = 0 AND c.default_object_id = 0
    )
        THROW 50963, 'dbo.payment.method is not the expected nullable VARCHAR(20).', 1;

    SELECT @checkDefinition = cc.definition
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'dbo.payment') AND cc.name = N'ck_payment_method'
      AND cc.is_disabled = 0 AND cc.is_not_trusted = 0;
    SET @normalized = UPPER(COALESCE(@checkDefinition, N''));
    SET @normalized = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@normalized,
        N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N'');
    WHILE LEN(@normalized) >= 2 AND LEFT(@normalized, 1) = N'(' AND RIGHT(@normalized, 1) = N')'
        SET @normalized = SUBSTRING(@normalized, 2, LEN(@normalized) - 2);
    SET @normalizedNew = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@normalized,
        N'(STATUS=''PENDING''ORSTATUS=''CANCELLED'')', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=''CANCELLED''ORSTATUS=''PENDING'')', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=(''PENDING'')ORSTATUS=(''CANCELLED''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'(STATUS=(''CANCELLED'')ORSTATUS=(''PENDING''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=''PENDING'')OR(STATUS=''CANCELLED''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=''CANCELLED'')OR(STATUS=''PENDING''))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=(''PENDING''))OR(STATUS=(''CANCELLED'')))', N'STATUSIN(''PENDING'',''CANCELLED'')'),
        N'((STATUS=(''CANCELLED''))OR(STATUS=(''PENDING'')))', N'STATUSIN(''PENDING'',''CANCELLED'')');
    IF @checkDefinition IS NULL OR @normalizedNew NOT IN (@newCanonical, @newExpandedA, @newExpandedB,
            @newExpandedScalarA, @newExpandedScalarB, @newSerializedA, @newSerializedB,
            @newSerializedNoScalarA, @newSerializedNoScalarB, @newCanonicalNoScalar, @newCanonicalScalar,
            @newExpandedNoGroupScalarA, @newExpandedNoGroupScalarB, @newExpandedNoGroupA, @newExpandedNoGroupB)
        THROW 50964, 'The renewal-aware ck_payment_method is missing, untrusted, disabled, or incompatible.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
