/* DRAFT ONLY. Clone-only rollback proposal; never runs automatically.
   SQLCMD variables required: -v ExpectedServer="..." ExpectedDatabase="...".
   This removes only the six columns, two CHECK constraints, and one index from the matching migration.
   It never deletes Payment rows, restores/overwrites a database, or drops a database. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedServer sysname=N'$(ExpectedServer)';
DECLARE @expectedDatabase sysname=N'$(ExpectedDatabase)';
DECLARE @actualServer sysname=CONVERT(sysname,SERVERPROPERTY('ServerName'));
IF NULLIF(LTRIM(RTRIM(@expectedServer)),N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@expectedDatabase)),N'') IS NULL
   OR @expectedServer=N'$'+N'(ExpectedServer)'
   OR @expectedDatabase=N'$'+N'(ExpectedDatabase)'
   OR UPPER(LTRIM(RTRIM(@expectedDatabase)))=N'SPORTCENTERDEMO'
   OR @actualServer IS NULL OR CONVERT(varbinary(256),@actualServer)<>CONVERT(varbinary(256),@expectedServer)
   OR CONVERT(varbinary(256),DB_NAME())<>CONVERT(varbinary(256),@expectedDatabase)
   OR DATABASEPROPERTYEX(DB_NAME(),'Status')<>N'ONLINE'
    THROW 50850, 'Explicit isolated-clone identity is missing or mismatched. Rollback stopped.', 1;

IF OBJECT_ID(N'dbo.payment',N'U') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_provider_transaction_reference') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_verified_collection_amount') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_verified_collection_currency') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_paid_time_evidence_type') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_collection_verified_at') IS NULL
   OR COL_LENGTH(N'dbo.payment',N'renewal_collection_conflict_detected_at') IS NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_collection_evidence',N'C') IS NULL
   OR OBJECT_ID(N'dbo.ck_payment_renewal_settlement_has_collection',N'C') IS NULL
   OR NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.payment')
                 AND name=N'ux_payment_renewal_provider_transaction_reference')
    THROW 50851, 'Expected complete collection migration objects are not present; inspect manually.', 1;

IF EXISTS (SELECT 1 FROM dbo.payment
    WHERE renewal_provider_transaction_reference IS NOT NULL
       OR renewal_verified_collection_amount IS NOT NULL
       OR renewal_verified_collection_currency IS NOT NULL
       OR renewal_paid_time_evidence_type IS NOT NULL
       OR renewal_collection_verified_at IS NOT NULL
       OR renewal_collection_conflict_detected_at IS NOT NULL)
    THROW 50852, 'Collection evidence or conflict markers exist; rollback would destroy evidence and is refused.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DROP INDEX ux_payment_renewal_provider_transaction_reference ON dbo.payment;
    ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_renewal_settlement_has_collection;
    ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_renewal_collection_evidence;
    ALTER TABLE dbo.payment DROP COLUMN
        renewal_provider_transaction_reference,
        renewal_verified_collection_amount,
        renewal_verified_collection_currency,
        renewal_paid_time_evidence_type,
        renewal_collection_verified_at,
        renewal_collection_conflict_detected_at;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
