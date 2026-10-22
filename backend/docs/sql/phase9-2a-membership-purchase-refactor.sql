-- Phase 9.2A additive membership pricing, upgrade relationship, and purchase snapshots.
-- Back up the database, stop application writers, and run as one standalone batch.
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
    THROW 50921, 'Select SportCenterDB before applying the membership purchase migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50922, 'Run the membership purchase migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.membership_plan', N'U') IS NULL OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 50923, 'Expected dbo.membership_plan, dbo.membership, and dbo.payment before this migration.', 1;

-- Populate only after reviewing actual plan rows. Required only when a legacy plan has
-- duration_months <> 1; the old bundle price is never divided or assumed to be monthly.
CREATE TABLE #PlanMonthlyPriceOverrides (plan_id INT NOT NULL PRIMARY KEY, monthly_price DECIMAL(12,2) NOT NULL);
-- Example after operator review: INSERT INTO #PlanMonthlyPriceOverrides VALUES (42, 123456.00);

BEGIN TRY
    BEGIN TRANSACTION;

    IF EXISTS (SELECT 1 FROM #PlanMonthlyPriceOverrides o JOIN dbo.membership_plan p ON p.id = o.plan_id WHERE p.duration_months = 1)
        THROW 50928, 'One-month legacy plans must use their existing price as the monthly price; do not override them.', 1;

    IF COL_LENGTH(N'dbo.membership_plan', N'monthly_price') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership_plan ADD monthly_price DECIMAL(12,2) NULL;';
    IF COL_LENGTH(N'dbo.payment', N'purchased_months') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD purchased_months INT NULL;';
    IF COL_LENGTH(N'dbo.payment', N'monthly_price_snapshot') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD monthly_price_snapshot DECIMAL(12,2) NULL;';
    IF COL_LENGTH(N'dbo.membership', N'purchased_months') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD purchased_months INT NULL;';
    IF COL_LENGTH(N'dbo.membership', N'paid_amount') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD paid_amount DECIMAL(12,2) NULL;';
    IF COL_LENGTH(N'dbo.membership', N'source_payment_id') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership ADD source_payment_id INT NULL;';

    IF OBJECT_ID(N'dbo.membership_plan_upgrade', N'U') IS NULL
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.membership_plan_upgrade (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_membership_plan_upgrade PRIMARY KEY,
                from_plan_id INT NOT NULL,
                to_plan_id INT NOT NULL,
                CONSTRAINT fk_membership_plan_upgrade_from FOREIGN KEY (from_plan_id) REFERENCES dbo.membership_plan(id),
                CONSTRAINT fk_membership_plan_upgrade_to FOREIGN KEY (to_plan_id) REFERENCES dbo.membership_plan(id),
                CONSTRAINT ck_membership_plan_upgrade_not_self CHECK (from_plan_id <> to_plan_id),
                CONSTRAINT uk_membership_plan_upgrade_direction UNIQUE (from_plan_id, to_plan_id)
            );';

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM #PlanMonthlyPriceOverrides o JOIN dbo.membership_plan p ON p.id = o.plan_id
                   WHERE p.monthly_price IS NOT NULL AND p.monthly_price <> o.monthly_price)
            THROW 50924, ''A supplied monthly-price override conflicts with an already populated monthly_price; inspect manually.'', 1;';
    IF EXISTS (SELECT 1 FROM #PlanMonthlyPriceOverrides o LEFT JOIN dbo.membership_plan p ON p.id = o.plan_id WHERE p.id IS NULL)
        THROW 50925, 'A monthly-price override references an unknown plan ID.', 1;

    EXEC sys.sp_executesql N'
        UPDATE dbo.membership_plan SET monthly_price = price
        WHERE monthly_price IS NULL AND duration_months = 1;';
    EXEC sys.sp_executesql N'
        UPDATE p SET monthly_price = o.monthly_price
        FROM dbo.membership_plan p JOIN #PlanMonthlyPriceOverrides o ON o.plan_id = p.id;';

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.membership_plan WHERE monthly_price IS NULL OR monthly_price <= 0)
            THROW 50926, ''Every plan needs a reviewed positive monthly price. Add explicit overrides for legacy multi-month plans; do not infer from bundle price.'', 1;';
    EXEC sys.sp_executesql N'ALTER TABLE dbo.membership_plan ALTER COLUMN monthly_price DECIMAL(12,2) NOT NULL;';

    IF EXISTS (
        SELECT e.table_name, e.column_name
        FROM (VALUES
            (N'dbo.membership_plan', N'monthly_price', N'decimal', 12, 2, CONVERT(bit, 0)),
            (N'dbo.payment', N'purchased_months', N'int', 10, 0, CONVERT(bit, 1)),
            (N'dbo.payment', N'monthly_price_snapshot', N'decimal', 12, 2, CONVERT(bit, 1)),
            (N'dbo.membership', N'purchased_months', N'int', 10, 0, CONVERT(bit, 1)),
            (N'dbo.membership', N'paid_amount', N'decimal', 12, 2, CONVERT(bit, 1)),
            (N'dbo.membership', N'source_payment_id', N'int', 10, 0, CONVERT(bit, 1)),
            (N'dbo.membership_plan_upgrade', N'from_plan_id', N'int', 10, 0, CONVERT(bit, 0)),
            (N'dbo.membership_plan_upgrade', N'to_plan_id', N'int', 10, 0, CONVERT(bit, 0))
        ) e(table_name, column_name, type_name, precision_value, scale_value, nullable_value)
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(e.table_name) AND c.name = e.column_name
        LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL OR t.name <> e.type_name OR c.precision <> e.precision_value
           OR c.scale <> e.scale_value OR c.is_nullable <> e.nullable_value OR c.is_identity <> 0
    ) THROW 50929, 'A membership purchase snapshot column has an unexpected type, precision, scale, nullability, or identity definition.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND c.name = N'id'
          AND c.user_type_id = TYPE_ID(N'int') AND c.is_nullable = 0 AND c.is_identity = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.key_constraints kc
        JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE kc.parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade')
          AND kc.name = N'pk_membership_plan_upgrade' AND kc.type = N'PK'
          AND ic.key_ordinal = 1 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = ic.object_id
               AND x.index_id = ic.index_id AND x.key_ordinal > 0) = 1
    ) THROW 50930, 'The membership plan upgrade identity or primary key definition is incompatible.', 1;
    -- Create missing objects before validating their metadata. On reruns, the
    -- name guards leave existing objects untouched and the checks below fail closed.
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_membership_purchase_snapshot')
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_membership_purchase_snapshot CHECK ((purchased_months IS NULL AND monthly_price_snapshot IS NULL) OR (purchased_months IS NOT NULL AND purchased_months IN (1,3,6,12) AND monthly_price_snapshot IS NOT NULL AND monthly_price_snapshot > 0 AND amount = monthly_price_snapshot * purchased_months));';
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.membership') AND name = N'ck_membership_purchase_snapshot')
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership WITH CHECK ADD CONSTRAINT ck_membership_purchase_snapshot CHECK ((purchased_months IS NULL AND paid_amount IS NULL AND source_payment_id IS NULL) OR (purchased_months IS NULL AND paid_amount IS NOT NULL AND paid_amount >= 0 AND source_payment_id IS NOT NULL) OR (purchased_months IS NOT NULL AND purchased_months IN (1,3,6,12) AND paid_amount IS NOT NULL AND paid_amount >= 0 AND source_payment_id IS NOT NULL));';
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.membership') AND name = N'fk_membership_source_payment')
        EXEC sys.sp_executesql N'ALTER TABLE dbo.membership WITH CHECK ADD CONSTRAINT fk_membership_source_payment FOREIGN KEY (source_payment_id) REFERENCES dbo.payment(id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.membership') AND name = N'ux_membership_source_payment')
        EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_membership_source_payment ON dbo.membership(source_payment_id) WHERE source_payment_id IS NOT NULL;';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'ix_membership_plan_upgrade_to')
        EXEC sys.sp_executesql N'CREATE INDEX ix_membership_plan_upgrade_to ON dbo.membership_plan_upgrade(to_plan_id, from_plan_id);';

    IF EXISTS (
        SELECT expected.constraint_name
        FROM (VALUES
            (N'fk_membership_source_payment', N'dbo.membership', N'source_payment_id', N'dbo.payment', N'id'),
            (N'fk_membership_plan_upgrade_from', N'dbo.membership_plan_upgrade', N'from_plan_id', N'dbo.membership_plan', N'id'),
            (N'fk_membership_plan_upgrade_to', N'dbo.membership_plan_upgrade', N'to_plan_id', N'dbo.membership_plan', N'id')
        ) expected(constraint_name, child_table, child_column, parent_table, parent_column)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
            JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
            WHERE fk.name = expected.constraint_name AND fk.parent_object_id = OBJECT_ID(expected.child_table)
              AND fk.referenced_object_id = OBJECT_ID(expected.parent_table)
              AND child.name = expected.child_column AND parent.name = expected.parent_column
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns fkcx WHERE fkcx.constraint_object_id = fk.object_id) = 1
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
        )
    ) THROW 50931, 'A membership purchase or plan relationship foreign key has an unexpected target or action.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic1 ON ic1.object_id = i.object_id AND ic1.index_id = i.index_id AND ic1.key_ordinal = 1
        JOIN sys.columns c1 ON c1.object_id = ic1.object_id AND c1.column_id = ic1.column_id
        JOIN sys.index_columns ic2 ON ic2.object_id = i.object_id AND ic2.index_id = i.index_id AND ic2.key_ordinal = 2
        JOIN sys.columns c2 ON c2.object_id = ic2.object_id AND c2.column_id = ic2.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND i.name = N'ix_membership_plan_upgrade_to'
          AND i.is_disabled = 0 AND c1.name = N'to_plan_id' AND c2.name = N'from_plan_id'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.key_ordinal > 0) = 2
    ) THROW 50932, 'The membership plan upgrade lookup index has unexpected key columns or ordering.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.membership') AND i.name = N'ux_membership_source_payment'
          AND i.is_unique = 1 AND i.has_filter = 1 AND i.is_disabled = 0 AND c.name = N'source_payment_id'
          AND UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.filter_definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N'')) = N'SOURCE_PAYMENT_IDISNOTNULL'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.key_ordinal > 0) = 1
          AND NOT EXISTS (SELECT 1 FROM sys.index_columns x WHERE x.object_id = i.object_id
                          AND x.index_id = i.index_id AND x.is_included_column = 1)
    ) THROW 50933, 'The membership source-payment index is not the expected unique filtered single-column index.', 1;

    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.membership_plan WHERE monthly_price IS NOT NULL AND duration_months = 1 AND monthly_price <> price)
           AND EXISTS (SELECT 1 FROM #PlanMonthlyPriceOverrides)
            PRINT ''Reviewed monthly-price values were supplied; legacy price and duration_months remain unchanged.'';';

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_membership_purchase_snapshot' AND (is_disabled = 1 OR is_not_trusted = 1))
       OR EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.membership') AND name = N'ck_membership_purchase_snapshot' AND (is_disabled = 1 OR is_not_trusted = 1))
       OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.membership') AND name = N'fk_membership_source_payment' AND (is_disabled = 1 OR is_not_trusted = 1))
       OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.membership') AND name = N'ux_membership_source_payment' AND is_unique = 1 AND has_filter = 1 AND is_disabled = 0)
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'pk_membership_plan_upgrade' AND type = N'PK')
       OR NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'uk_membership_plan_upgrade_direction' AND type = N'UQ')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'fk_membership_plan_upgrade_from' AND is_disabled = 0 AND is_not_trusted = 0)
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'fk_membership_plan_upgrade_to' AND is_disabled = 0 AND is_not_trusted = 0)
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'ck_membership_plan_upgrade_not_self' AND is_disabled = 0 AND is_not_trusted = 0)
        THROW 50927, 'Membership purchase or upgrade relationship metadata is incomplete, disabled, or untrusted.', 1;

    DECLARE @expectedCheckDefinitions TABLE (
        table_name sysname NOT NULL,
        constraint_name sysname NOT NULL,
        expected_definition nvarchar(1000) NOT NULL,
        known_sql_server_serialization nvarchar(2000) NULL
    );
    INSERT INTO @expectedCheckDefinitions (table_name, constraint_name, expected_definition, known_sql_server_serialization)
    VALUES
        (N'dbo.payment', N'ck_payment_membership_purchase_snapshot',
         N'((purchased_months IS NULL AND monthly_price_snapshot IS NULL) OR (purchased_months IS NOT NULL AND purchased_months IN (1,3,6,12) AND monthly_price_snapshot IS NOT NULL AND monthly_price_snapshot > 0 AND amount = monthly_price_snapshot * purchased_months))',
         N'(PURCHASED_MONTHSISNULLANDMONTHLY_PRICE_SNAPSHOTISNULLORPURCHASED_MONTHSISNOTNULLAND(PURCHASED_MONTHS=(12)ORPURCHASED_MONTHS=(6)ORPURCHASED_MONTHS=(3)ORPURCHASED_MONTHS=(1))ANDMONTHLY_PRICE_SNAPSHOTISNOTNULLANDMONTHLY_PRICE_SNAPSHOT>0ANDAMOUNT=MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS)'),
        (N'dbo.membership', N'ck_membership_purchase_snapshot',
         N'((purchased_months IS NULL AND paid_amount IS NULL AND source_payment_id IS NULL) OR (purchased_months IS NULL AND paid_amount IS NOT NULL AND paid_amount >= 0 AND source_payment_id IS NOT NULL) OR (purchased_months IS NOT NULL AND purchased_months IN (1,3,6,12) AND paid_amount IS NOT NULL AND paid_amount >= 0 AND source_payment_id IS NOT NULL))',
         N'(PURCHASED_MONTHSISNULLANDPAID_AMOUNTISNULLANDSOURCE_PAYMENT_IDISNULLORPURCHASED_MONTHSISNULLANDPAID_AMOUNTISNOTNULLANDPAID_AMOUNT>=0ANDSOURCE_PAYMENT_IDISNOTNULLORPURCHASED_MONTHSISNOTNULLAND(PURCHASED_MONTHS=(12)ORPURCHASED_MONTHS=(6)ORPURCHASED_MONTHS=(3)ORPURCHASED_MONTHS=(1))ANDPAID_AMOUNTISNOTNULLANDPAID_AMOUNT>=0ANDSOURCE_PAYMENT_IDISNOTNULL)');
    DECLARE @atomicPredicates TABLE (predicate nvarchar(200) NOT NULL PRIMARY KEY);
    INSERT INTO @atomicPredicates (predicate) VALUES
        (N'PURCHASED_MONTHSISNULL'), (N'PURCHASED_MONTHSISNOTNULL'),
        (N'MONTHLY_PRICE_SNAPSHOTISNULL'), (N'MONTHLY_PRICE_SNAPSHOTISNOTNULL'),
        (N'MONTHLY_PRICE_SNAPSHOT>0'), (N'AMOUNT=MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS'),
        (N'PAID_AMOUNTISNULL'), (N'PAID_AMOUNTISNOTNULL'), (N'PAID_AMOUNT>=0'),
        (N'SOURCE_PAYMENT_IDISNULL'), (N'SOURCE_PAYMENT_IDISNOTNULL'),
        (N'PURCHASED_MONTHSIN(1,3,6,12)');
    DECLARE @checkTable sysname, @checkName sysname, @expectedDefinition nvarchar(1000),
            @knownSqlServerSerialization nvarchar(2000), @actualDefinition nvarchar(4000);
    DECLARE check_definition_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name, constraint_name, expected_definition, known_sql_server_serialization FROM @expectedCheckDefinitions;
    OPEN check_definition_cursor;
    FETCH NEXT FROM check_definition_cursor INTO @checkTable, @checkName, @expectedDefinition, @knownSqlServerSerialization;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @actualDefinition = NULL;
        SELECT @actualDefinition = definition FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(@checkTable) AND name = @checkName
          AND is_disabled = 0 AND is_not_trusted = 0;
        DECLARE @normalizedExpected nvarchar(1000) = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(@expectedDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''));
        SET @normalizedExpected = REPLACE(@normalizedExpected, N']', N'');
        DECLARE @normalizedActual nvarchar(4000) = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(@actualDefinition, N''), N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''));
        SET @normalizedActual = REPLACE(@normalizedActual, N']', N'');
        -- SQL Server may serialize scalar IN-list values with an extra pair of
        -- parentheses. Normalize this known form only; do not remove arbitrary
        -- inner parentheses because they carry predicate grouping semantics.
        SET @normalizedExpected = REPLACE(@normalizedExpected, N'IN((1),(3),(6),(12))', N'IN(1,3,6,12)');
        SET @normalizedActual = REPLACE(@normalizedActual, N'IN((1),(3),(6),(12))', N'IN(1,3,6,12)');
        -- Parentheses around a comparison's scalar arithmetic/literal operands
        -- are also semantically neutral and may appear in serialized definitions.
        SET @normalizedExpected = REPLACE(@normalizedExpected, N'AMOUNT=(MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS)', N'AMOUNT=MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS');
        SET @normalizedActual = REPLACE(@normalizedActual, N'AMOUNT=(MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS)', N'AMOUNT=MONTHLY_PRICE_SNAPSHOT*PURCHASED_MONTHS');
        SET @normalizedExpected = REPLACE(@normalizedExpected, N'MONTHLY_PRICE_SNAPSHOT>(0)', N'MONTHLY_PRICE_SNAPSHOT>0');
        SET @normalizedActual = REPLACE(@normalizedActual, N'MONTHLY_PRICE_SNAPSHOT>(0)', N'MONTHLY_PRICE_SNAPSHOT>0');
        SET @normalizedExpected = REPLACE(@normalizedExpected, N'PAID_AMOUNT>=(0)', N'PAID_AMOUNT>=0');
        SET @normalizedActual = REPLACE(@normalizedActual, N'PAID_AMOUNT>=(0)', N'PAID_AMOUNT>=0');
        DECLARE @normalizedKnownSerialization nvarchar(4000) = UPPER(COALESCE(@knownSqlServerSerialization, N''));
        -- Some SQL Server versions/catalog paths also add parentheses around
        -- individual Boolean atoms. Remove wrappers only for this allowlisted set
        -- of exact atoms; retain parentheses that group compound AND/OR expressions.
        DECLARE @atomicPredicate nvarchar(200), @foundAtomicWrapper bit;
        DECLARE atomic_predicate_cursor CURSOR LOCAL FAST_FORWARD FOR
            SELECT predicate FROM @atomicPredicates;
        OPEN atomic_predicate_cursor;
        FETCH NEXT FROM atomic_predicate_cursor INTO @atomicPredicate;
        WHILE @@FETCH_STATUS = 0
        BEGIN
            SET @foundAtomicWrapper = 1;
            WHILE @foundAtomicWrapper = 1
            BEGIN
                SET @foundAtomicWrapper = 0;
                IF CHARINDEX(N'(' + @atomicPredicate + N')', @normalizedExpected) > 0
                BEGIN
                    SET @normalizedExpected = REPLACE(@normalizedExpected, N'(' + @atomicPredicate + N')', @atomicPredicate);
                    SET @foundAtomicWrapper = 1;
                END;
                IF CHARINDEX(N'(' + @atomicPredicate + N')', @normalizedActual) > 0
                BEGIN
                    SET @normalizedActual = REPLACE(@normalizedActual, N'(' + @atomicPredicate + N')', @atomicPredicate);
                    SET @foundAtomicWrapper = 1;
                END;
            END;
            FETCH NEXT FROM atomic_predicate_cursor INTO @atomicPredicate;
        END;
        CLOSE atomic_predicate_cursor;
        DEALLOCATE atomic_predicate_cursor;
        IF @normalizedActual NOT IN (@normalizedExpected, N'(' + @normalizedExpected + N')', N'((' + @normalizedExpected + N'))')
           AND (@normalizedKnownSerialization = N'' OR @normalizedActual NOT IN (
               @normalizedKnownSerialization,
               N'(' + @normalizedKnownSerialization + N')',
               N'((' + @normalizedKnownSerialization + N'))'))
        BEGIN
            PRINT N'CHECK validation mismatch for ' + @checkTable + N'.' + @checkName + N'.';
            PRINT N'Expected definition: ' + @expectedDefinition;
            PRINT N'Actual definition: ' + COALESCE(@actualDefinition, N'<missing, disabled, or untrusted>');
            PRINT N'Normalized expected: ' + @normalizedExpected;
            IF @normalizedKnownSerialization <> N''
                PRINT N'Allowed known SQL Server serialization: ' + @normalizedKnownSerialization;
            PRINT N'Normalized actual: ' + @normalizedActual;
            PRINT N'Mismatch reason: definition matches neither the authored predicate nor its exact allowlisted SQL Server IN-to-OR serialization.';
            THROW 50934, 'A membership purchase CHECK is missing, untrusted, disabled, or has an unexpected logical definition.', 1;
        END;
        FETCH NEXT FROM check_definition_cursor INTO @checkTable, @checkName, @expectedDefinition, @knownSqlServerSerialization;
    END;
    CLOSE check_definition_cursor;
    DEALLOCATE check_definition_cursor;

    IF NOT EXISTS (
        SELECT 1 FROM sys.key_constraints kc
        JOIN sys.index_columns ic1 ON ic1.object_id = kc.parent_object_id AND ic1.index_id = kc.unique_index_id AND ic1.key_ordinal = 1
        JOIN sys.columns c1 ON c1.object_id = ic1.object_id AND c1.column_id = ic1.column_id
        JOIN sys.index_columns ic2 ON ic2.object_id = kc.parent_object_id AND ic2.index_id = kc.unique_index_id AND ic2.key_ordinal = 2
        JOIN sys.columns c2 ON c2.object_id = ic2.object_id AND c2.column_id = ic2.column_id
        WHERE kc.parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade')
          AND kc.name = N'uk_membership_plan_upgrade_direction' AND kc.type = N'UQ'
          AND c1.name = N'from_plan_id' AND c2.name = N'to_plan_id'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = kc.parent_object_id
               AND x.index_id = kc.unique_index_id AND x.key_ordinal > 0) = 2
    ) THROW 50935, 'The membership plan upgrade uniqueness constraint has unexpected columns or ordering.', 1;

    DECLARE @notSelfDefinition nvarchar(4000);
    SELECT @notSelfDefinition = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(13) + NCHAR(10), N''))
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.membership_plan_upgrade') AND name = N'ck_membership_plan_upgrade_not_self'
      AND is_disabled = 0 AND is_not_trusted = 0;
    IF @notSelfDefinition <> N'FROM_PLAN_ID<>TO_PLAN_ID'
        THROW 50936, 'The membership plan upgrade self-link CHECK has an unexpected predicate.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
