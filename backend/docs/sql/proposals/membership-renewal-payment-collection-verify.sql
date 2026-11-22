/* READ-ONLY, fail-closed post-migration verification proposal.
   Requires the same SQL connection as preflight so #baseline_* remains available.
   SQLCMD variables: -v ExpectedServer="..." ExpectedDatabase="...".
   Outputs complete differing rows, then THROWs. No business data is changed. */
SET NOCOUNT ON;

DECLARE @expectedServer sysname = N'$(ExpectedServer)';
DECLARE @expectedDatabase sysname = N'$(ExpectedDatabase)';
DECLARE @actualServer sysname = CONVERT(sysname, SERVERPROPERTY('ServerName'));
IF NULLIF(LTRIM(RTRIM(@expectedServer)), N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabase)), N'') IS NULL
   OR @expectedServer = N'$' + N'(ExpectedServer)'
   OR @expectedDatabase = N'$' + N'(ExpectedDatabase)'
   OR UPPER(LTRIM(RTRIM(@expectedDatabase))) = N'SPORTCENTERDEMO'
   OR @actualServer IS NULL OR CONVERT(varbinary(256),@actualServer) <> CONVERT(varbinary(256),@expectedServer)
   OR CONVERT(varbinary(256),DB_NAME()) <> CONVERT(varbinary(256),@expectedDatabase)
   OR DATABASEPROPERTYEX(DB_NAME(), 'Status') <> N'ONLINE'
    THROW 50830, 'Explicit isolated-clone identity is missing or mismatched. Verification stopped.', 1;

IF OBJECT_ID(N'dbo.payment',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_renewal_intent',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_cancellation_request',N'U') IS NULL
    THROW 50831, 'A required business table is missing.', 1;

DECLARE @requiredPrerequisiteColumns TABLE (table_name sysname NOT NULL, column_name sysname NOT NULL,
                                            PRIMARY KEY(table_name,column_name));
INSERT INTO @requiredPrerequisiteColumns VALUES
 (N'payment',N'id'),(N'payment',N'member_id'),(N'payment',N'amount'),(N'payment',N'method'),
 (N'payment',N'purpose'),(N'payment',N'reference_id'),(N'payment',N'status'),(N'payment',N'transaction_code'),
 (N'payment',N'paid_at'),(N'payment',N'processed_by'),(N'payment',N'renewal_intent_id'),
 (N'payment',N'attempt_valid_until'),(N'payment',N'renewal_attempt_close_reason'),
 (N'payment',N'renewal_settlement_accepted_at'),
 (N'membership',N'id'),(N'membership',N'renewal_intent_id'),(N'membership',N'purchase_payment_id'),
 (N'membership_renewal_intent',N'id'),(N'membership_upgrade',N'id'),
 (N'membership_cancellation_request',N'id');
IF EXISTS (SELECT 1 FROM @requiredPrerequisiteColumns r
           LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+r.table_name,N'U') AND c.name=r.column_name
           WHERE c.column_id IS NULL)
    THROW 50841, 'A required Phase 2D prerequisite column is missing.', 1;

DECLARE @expectedColumns TABLE
 (column_name sysname PRIMARY KEY, type_name sysname, max_length smallint, precision_value tinyint,
  scale_value tinyint, is_nullable bit);
INSERT INTO @expectedColumns VALUES
 (N'renewal_provider_transaction_reference',N'varchar',64,0,0,1),
 (N'renewal_verified_collection_amount',N'decimal',9,12,2,1),
 (N'renewal_verified_collection_currency',N'varchar',3,0,0,1),
 (N'renewal_paid_time_evidence_type',N'varchar',24,0,0,1),
 (N'renewal_collection_verified_at',N'datetime2',8,0,6,1),
 (N'renewal_collection_conflict_detected_at',N'datetime2',8,0,6,1);
IF EXISTS (
    SELECT 1 FROM @expectedColumns e
    LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.payment',N'U') AND c.name=e.column_name
    LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
    WHERE c.column_id IS NULL OR t.name<>e.type_name OR c.max_length<>e.max_length
       OR c.is_nullable<>e.is_nullable
       OR (e.type_name=N'decimal' AND (c.precision<>e.precision_value OR c.scale<>e.scale_value))
       OR (e.type_name=N'datetime2' AND c.scale<>e.scale_value)
)
    THROW 50832, 'One or more collection columns are missing or have an incorrect SQL type/nullability.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
               AND name=N'ux_payment_renewal_provider_transaction_reference'
               AND is_unique=1 AND is_disabled=0
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_intent_id is not null%'
               AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_provider_transaction_reference is not null%')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
                  AND name=N'ux_payment_one_accepted_renewal_attempt' AND is_unique=1 AND is_disabled=0
                  AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_intent_id is not null%'
                  AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_settlement_accepted_at is not null%')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership')
                  AND name=N'ux_membership_one_renewal_successor' AND is_unique=1 AND is_disabled=0
                  AND REPLACE(REPLACE(LOWER(filter_definition),N'[',N''),N']',N'') LIKE N'%renewal_intent_id is not null%')
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
                  AND name=N'ix_payment_renewal_intent_status' AND is_disabled=0)
   OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.membership_renewal_intent')
                  AND name=N'ix_renewal_intent_due' AND is_disabled=0)
    THROW 50833, 'Required provider-reference or Phase 2D uniqueness indexes are missing, disabled, or misconfigured.', 1;

DECLARE @collectionCheck nvarchar(max), @settlementCheck nvarchar(max);
SELECT @collectionCheck=definition FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID(N'dbo.payment') AND name=N'ck_payment_renewal_collection_evidence'
  AND is_disabled=0 AND is_not_trusted=0;
SELECT @settlementCheck=definition FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID(N'dbo.payment') AND name=N'ck_payment_renewal_settlement_has_collection'
  AND is_disabled=0 AND is_not_trusted=0;
SET @collectionCheck=REPLACE(REPLACE(LOWER(@collectionCheck),N'[',N''),N']',N'');
SET @settlementCheck=REPLACE(REPLACE(LOWER(@settlementCheck),N'[',N''),N']',N'');
IF @collectionCheck IS NULL OR @settlementCheck IS NULL
   OR @collectionCheck NOT LIKE N'%renewal_intent_id is not null%'
   OR @collectionCheck NOT LIKE N'%renewal_collection_conflict_detected_at is not null%'
   OR @collectionCheck NOT LIKE N'%renewal_collection_verified_at is null%'
   OR @collectionCheck NOT LIKE N'%method is not null%'
   OR @collectionCheck NOT LIKE N'%malformed%'
   OR @collectionCheck NOT LIKE N'%server_confirmation%'
   OR @settlementCheck NOT LIKE N'%renewal_collection_conflict_detected_at is null%'
   OR @settlementCheck NOT LIKE N'%renewal_intent_id is null%'
   OR @settlementCheck NOT LIKE N'%method is not null%'
   OR @settlementCheck NOT LIKE N'%amount is not null%'
   OR @settlementCheck NOT LIKE N'%provider_verified%'
   OR @settlementCheck NOT LIKE N'%server_confirmation%'
   OR @settlementCheck NOT LIKE N'%method=''online''%'
   OR @settlementCheck NOT LIKE N'%method=''counter''%'
   OR @settlementCheck NOT LIKE N'%processed_by is not null%'
   OR @settlementCheck NOT LIKE N'%renewal_verified_collection_currency=''vnd''%'
   OR @settlementCheck NOT LIKE N'%renewal_verified_collection_amount=amount%'
   OR @settlementCheck NOT LIKE N'%renewal_provider_transaction_reference is null%'
    THROW 50834, 'Collection CHECK constraints are missing, untrusted, disabled, or lack required evidence rules.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.payment
    WHERE renewal_collection_verified_at IS NOT NULL
      AND (renewal_intent_id IS NULL
        OR renewal_paid_time_evidence_type IS NULL
        OR (method='ONLINE' AND renewal_paid_time_evidence_type NOT IN ('PROVIDER_VERIFIED','MISSING','MALFORMED'))
        OR (method='COUNTER' AND renewal_paid_time_evidence_type<>'SERVER_CONFIRMATION')
        OR (method='COUNTER' AND renewal_provider_transaction_reference IS NOT NULL)
        OR method NOT IN ('ONLINE','COUNTER'))
)
    THROW 50835, 'Recorded collection evidence has an invalid Renewal relationship or method classification.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.payment
    WHERE renewal_settlement_accepted_at IS NOT NULL
      AND renewal_intent_id IS NOT NULL
      AND (renewal_collection_conflict_detected_at IS NOT NULL
        OR renewal_collection_verified_at IS NULL
        OR amount IS NULL
        OR renewal_verified_collection_amount IS NULL
        OR renewal_verified_collection_amount<>amount
        OR renewal_verified_collection_currency IS NULL
        OR renewal_verified_collection_currency<>'VND'
        OR paid_at IS NULL
        OR renewal_paid_time_evidence_type IS NULL
        OR method IS NULL
        OR NOT ((method='ONLINE' AND renewal_paid_time_evidence_type='PROVIDER_VERIFIED'
                    AND renewal_provider_transaction_reference IS NOT NULL)
                OR (method='COUNTER' AND renewal_paid_time_evidence_type='SERVER_CONFIRMATION'
                    AND processed_by IS NOT NULL AND renewal_provider_transaction_reference IS NULL)))
)
    THROW 50836, 'An accepted Renewal settlement lacks complete method-appropriate collection evidence.', 1;

IF EXISTS (SELECT 1 FROM dbo.payment WHERE renewal_collection_conflict_detected_at IS NOT NULL
           AND renewal_intent_id IS NULL)
    THROW 50837, 'A non-Renewal Payment has a Renewal collection conflict marker.', 1;

IF OBJECT_ID(N'tempdb..#baseline_membership') IS NULL
   OR OBJECT_ID(N'tempdb..#baseline_payment') IS NULL
   OR OBJECT_ID(N'tempdb..#baseline_membership_upgrade') IS NULL
   OR OBJECT_ID(N'tempdb..#baseline_membership_cancellation_request') IS NULL
    THROW 50838, 'Same-session row-level baseline is absent; do not claim historical equality.', 1;

/* Two-way EXCEPT compares every pre-migration column. This deliberately excludes the six newly added Payment columns. */
DECLARE @compare TABLE (table_name sysname, temp_name sysname);
INSERT INTO @compare VALUES
 (N'membership',N'#baseline_membership'),(N'payment',N'#baseline_payment'),
 (N'membership_upgrade',N'#baseline_membership_upgrade'),
 (N'membership_cancellation_request',N'#baseline_membership_cancellation_request');
DECLARE @table sysname, @temp sysname, @columns nvarchar(max), @sql nvarchar(max), @hasDifference bit;
DECLARE compare_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT table_name,temp_name FROM @compare;
OPEN compare_cursor;
FETCH NEXT FROM compare_cursor INTO @table,@temp;
WHILE @@FETCH_STATUS=0
BEGIN
    SELECT @columns=STUFF((
        SELECT N','+QUOTENAME(c.name)
        FROM tempdb.sys.columns c WHERE c.object_id=OBJECT_ID(N'tempdb..'+@temp)
        ORDER BY c.column_id
        FOR XML PATH(N''),TYPE).value(N'.',N'nvarchar(max)'),1,1,N'');
    IF @columns IS NULL
        THROW 50839, 'A row-baseline temp table has no comparable columns.', 1;

    SET @hasDifference=0;
    SET @sql=N'IF EXISTS (SELECT '+@columns+N' FROM '+QUOTENAME(@temp)+N' EXCEPT SELECT '+@columns+N' FROM dbo.'+QUOTENAME(@table)+N')'
        +N' OR EXISTS (SELECT '+@columns+N' FROM dbo.'+QUOTENAME(@table)+N' EXCEPT SELECT '+@columns+N' FROM '+QUOTENAME(@temp)+N')'
        +N' SET @different=1;';
    EXEC sys.sp_executesql @sql,N'@different bit OUTPUT',@different=@hasDifference OUTPUT;
    IF @hasDifference=1
    BEGIN
        SET @sql=N'SELECT N''baseline_missing_or_changed'' AS difference, '+@columns+N' FROM '+QUOTENAME(@temp)
            +N' EXCEPT SELECT N''baseline_missing_or_changed'', '+@columns+N' FROM dbo.'+QUOTENAME(@table)+N';'
            +N'SELECT N''unexpected_current_row'' AS difference, '+@columns+N' FROM dbo.'+QUOTENAME(@table)
            +N' EXCEPT SELECT N''unexpected_current_row'', '+@columns+N' FROM '+QUOTENAME(@temp)+N';';
        EXEC sys.sp_executesql @sql;
        DECLARE @message nvarchar(2048)=N'Full-row baseline comparison failed for dbo.'+@table+N'.';
        THROW 50840, @message, 1;
    END;
    FETCH NEXT FROM compare_cursor INTO @table,@temp;
END;
CLOSE compare_cursor;
DEALLOCATE compare_cursor;

SELECT @actualServer AS server_name, DB_NAME() AS database_name,
       N'PASS: expected schema, trusted constraints/indexes, valid evidence, and exact protected-row equality.' AS verification_result;
