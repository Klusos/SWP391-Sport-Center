-- PROPOSAL ONLY. READ-ONLY and not executed by Phase 8C-D.
-- Run as one SQLCMD-mode batch only after separate authorization.
-- Required SQLCMD variables: ApprovedServerIdentity, ApprovedDatabaseName,
-- ApprovedFourStateCheckSha256. There are deliberately no target defaults.
-- Obtain and review the exact CHECK definition/hash from an authorized read-only target baseline.
SET NOCOUNT ON;

DECLARE @ApprovedServerIdentity nvarchar(128) = N'$(ApprovedServerIdentity)';
DECLARE @ApprovedDatabaseName sysname = N'$(ApprovedDatabaseName)';
DECLARE @ApprovedFourStateCheckSha256 char(64) = UPPER('$(ApprovedFourStateCheckSha256)');

IF NULLIF(LTRIM(RTRIM(@ApprovedServerIdentity)), N'') IS NULL
   OR NULLIF(LTRIM(RTRIM(@ApprovedDatabaseName)), N'') IS NULL
   OR @ApprovedFourStateCheckSha256 IS NULL
   OR LEN(@ApprovedFourStateCheckSha256) <> 64
   OR @ApprovedFourStateCheckSha256 LIKE '%[^0-9A-F]%'
    THROW 50831, 'Approved target identity and reviewed four-state CHECK SHA-256 are required.', 1;

IF CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2
       <> @ApprovedServerIdentity COLLATE Latin1_General_100_BIN2
   OR DB_NAME() COLLATE Latin1_General_100_BIN2
       <> @ApprovedDatabaseName COLLATE Latin1_General_100_BIN2
    THROW 50832, 'Current SQL Server instance/database does not exactly match the approved target.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE database_id = DB_ID() AND state = 0)
    THROW 50833, 'Current database is not ONLINE.', 1;
IF OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NULL
   OR OBJECT_ID(N'dbo.audit_log', N'U') IS NULL
   OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50834, 'A required dbo table is missing.', 1;

SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS server_identity,
       @@SERVERNAME AS configured_server_name, DB_NAME() AS database_name,
       CONVERT(nvarchar(128), SERVERPROPERTY('ProductVersion')) AS product_version,
       CONVERT(nvarchar(128), SERVERPROPERTY('ProductMajorVersion')) AS product_major_version,
       N'ONLINE' AS database_state;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    WHERE c.object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
      AND c.name = N'status' AND c.system_type_id = TYPE_ID(N'varchar')
      AND c.user_type_id = c.system_type_id AND c.max_length = 30
      AND c.is_nullable = 0 AND c.is_computed = 0 AND c.generated_always_type = 0
      AND c.default_object_id = 0)
    THROW 50835, 'PT status must be the expected non-null varchar(30) column without computed/default generation.', 1;

IF (SELECT COUNT(*) FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'dbo.personal_training_booking',N'U')
      AND CHARINDEX(N'status',LOWER(definition))>0) <> 2
    THROW 50890, 'Expected the exact status CHECK and reviewed payment-expiry CHECK only among status-related constraints.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'dbo.personal_training_booking',N'U')
      AND CHARINDEX(N'status',LOWER(definition))>0
      AND name NOT IN (N'ck_personal_training_booking_status',N'ck_personal_training_booking_payment_expiry'))
    THROW 50891, 'Unknown status-related CHECK constraint found.', 1;
-- Exact companion-CHECK definitions are pinned to the reviewed Phase 8C-E baseline.
DECLARE @ExpectedPreservedChecks table (name sysname NOT NULL PRIMARY KEY, definition nvarchar(300) NOT NULL);
INSERT @ExpectedPreservedChecks VALUES
    (N'ck_personal_training_booking_payment_expiry',N'([status]<>''PENDING_PAYMENT'' OR [payment_expires_at] IS NOT NULL)'),
    (N'ck_personal_training_booking_prices',N'([original_price]>=(0) AND [discount]>=(0) AND [final_price]>=(0))'),
    (N'ck_personal_training_booking_times',N'([end_time]>[start_time])');
IF EXISTS (
    SELECT 1 FROM @ExpectedPreservedChecks e
    LEFT JOIN sys.check_constraints cc
      ON cc.parent_object_id=OBJECT_ID(N'dbo.personal_training_booking',N'U') AND cc.name=e.name
    WHERE cc.object_id IS NULL OR cc.is_disabled<>0 OR cc.is_not_trusted<>0
       OR CONVERT(varbinary(max),cc.definition)<>CONVERT(varbinary(max),e.definition))
    THROW 50892, 'A required payment-expiry, prices, or times CHECK differs from the reviewed trusted baseline.', 1;

DECLARE @ActualFourStateDefinition nvarchar(max);
SELECT @ActualFourStateDefinition = cc.definition
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
  AND cc.name = N'ck_personal_training_booking_status'
  AND cc.is_disabled = 0 AND cc.is_not_trusted = 0;

IF @ActualFourStateDefinition IS NULL
    THROW 50837, 'Named four-state status CHECK is absent, disabled, or untrusted.', 1;
IF CONVERT(char(64), HASHBYTES('SHA2_256', CONVERT(varbinary(max), @ActualFourStateDefinition)), 2)
       COLLATE Latin1_General_100_BIN2 <> @ApprovedFourStateCheckSha256 COLLATE Latin1_General_100_BIN2
    THROW 50838, 'Status CHECK definition is not byte-identical to the separately reviewed baseline definition.', 1;

IF EXISTS (SELECT 1 FROM dbo.personal_training_booking WHERE status IS NULL)
   OR EXISTS (SELECT 1 FROM dbo.personal_training_booking
              WHERE status NOT IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
    THROW 50839, 'NULL or unexpected historical PT status found; stop for manual review.', 1;

IF (SELECT COUNT(*) FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.audit_log', N'U')) <> 8
    THROW 50840, 'dbo.audit_log column set differs from the reviewed eight-column contract.', 1;

DECLARE @ExpectedAuditColumns table (
    name sysname NOT NULL PRIMARY KEY, type_name sysname NOT NULL, max_length smallint NOT NULL,
    is_nullable bit NOT NULL, is_identity bit NOT NULL, precision_value tinyint NULL, scale tinyint NULL);
INSERT @ExpectedAuditColumns VALUES
    (N'id', N'bigint', 8, 0, 1, 19, 0),
    (N'actor_user_id', N'int', 4, 1, 0, 10, 0),
    (N'actor_role', N'varchar', 30, 0, 0, NULL, NULL),
    (N'action', N'varchar', 50, 0, 0, NULL, NULL),
    (N'entity_type', N'varchar', 60, 0, 0, NULL, NULL),
    (N'entity_id', N'varchar', 64, 1, 0, NULL, NULL),
    (N'description', N'nvarchar', 1000, 0, 0, NULL, NULL),
    (N'created_at', N'datetime2', 8, 0, 0, 26, 6);

IF EXISTS (
    SELECT 1
    FROM @ExpectedAuditColumns e
    LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.audit_log', N'U') AND c.name = e.name
    LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
    WHERE c.column_id IS NULL OR t.name <> e.type_name OR t.is_user_defined <> 0
       OR c.user_type_id <> c.system_type_id OR c.max_length <> e.max_length
       OR c.is_nullable <> e.is_nullable OR c.is_identity <> e.is_identity
       OR (e.precision_value IS NOT NULL AND c.precision <> e.precision_value)
       OR c.is_computed <> 0 OR c.generated_always_type <> 0 OR c.default_object_id <> 0
       OR (e.scale IS NOT NULL AND c.scale <> e.scale))
    THROW 50841, 'dbo.audit_log column type/length/nullability/identity/default contract is incompatible.', 1;
IF EXISTS (SELECT 1 FROM sys.columns c
           WHERE c.object_id = OBJECT_ID(N'dbo.audit_log', N'U')
             AND c.name NOT IN (SELECT name FROM @ExpectedAuditColumns))
    THROW 50842, 'dbo.audit_log contains an unreviewed extra column.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID(N'dbo.audit_log', N'U') AND i.is_primary_key = 1
      AND i.is_unique = 1 AND i.is_disabled = 0 AND c.name = N'id'
      AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
           AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1)
    THROW 50843, 'dbo.audit_log requires an enabled single-column id primary key.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
    JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
    JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.audit_log', N'U')
      AND fk.referenced_object_id = OBJECT_ID(N'dbo.users', N'U')
      AND pc.name = N'actor_user_id' AND rc.name = N'id'
      AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
      AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
      AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1)
    THROW 50844, 'dbo.audit_log requires its trusted, non-cascading actor_user_id to dbo.users.id foreign key.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users', N'U')
              AND name = N'id' AND system_type_id = TYPE_ID(N'int')
              AND user_type_id = system_type_id AND is_nullable = 0)
    THROW 50845, 'dbo.users.id must be a non-null SQL INT for the audit actor relationship.', 1;

SELECT cc.name, cc.definition,
       CONVERT(char(64), HASHBYTES('SHA2_256', CONVERT(varbinary(max), cc.definition)), 2) AS definition_sha256,
       cc.is_disabled, cc.is_not_trusted
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
ORDER BY cc.name;

SELECT status, COUNT_BIG(*) AS row_count
FROM dbo.personal_training_booking GROUP BY status ORDER BY status;
SELECT c.name, t.name AS type_name, c.max_length, c.precision, c.scale,
       c.is_nullable, c.is_identity, c.default_object_id
FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.audit_log', N'U') ORDER BY c.column_id;

-- A PASS requires the supplied hash to have been copied only after an authorized reviewer
-- inspected the exact target definition. This script is inventory plus fail-closed preflight;
-- it does not authorize or perform migration.







