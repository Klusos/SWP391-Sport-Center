-- Phone format constraints for the original demo database.
-- Owner-approved DB5 one-time repair: replace only still-invalid phones on the
-- listed demonstration rows with shared demo-only placeholder 0987654321.
-- Already-valid phone values, all other fields, and NULL/required semantics are preserved.
-- Preserves dbo.users.phone NULL semantics and consultation_request.phone NOT NULL.
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'SportCenterDemo' OR DB_ID() <> 5
    THROW 51971, 'Select the verified SportCenterDemo DB5 target before applying this migration.', 1;
IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> N'LAPTOP-416VBDLK'
    THROW 51981, 'Unexpected SQL Server identity; verify the DB5 target before applying this migration.', 1;
IF (SELECT state_desc FROM sys.databases WHERE database_id = DB_ID()) <> 'ONLINE'
    THROW 51972, 'SportCenterDemo is not ONLINE.', 1;
IF @@TRANCOUNT <> 0
    THROW 51973, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.users', N'U') IS NULL OR OBJECT_ID(N'dbo.consultation_request', N'U') IS NULL
    THROW 51974, 'Expected dbo.users and dbo.consultation_request.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users')
               AND name = N'phone' AND system_type_id = TYPE_ID(N'varchar') AND max_length = 20 AND is_nullable = 1)
    THROW 51975, 'dbo.users.phone differs from reviewed varchar(20) NULL schema.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.consultation_request')
               AND name = N'phone' AND system_type_id = TYPE_ID(N'nvarchar') AND max_length = 40 AND is_nullable = 0)
    THROW 51976, 'dbo.consultation_request.phone differs from reviewed nvarchar(20) NOT NULL schema.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    UPDATE dbo.users SET phone = '0987654321'
    WHERE id IN (9, 1009, 1010, 1011, 1013, 1014, 1015, 1016, 1017)
      AND phone IS NOT NULL AND NOT (DATALENGTH(phone) = 10
          AND phone COLLATE Latin1_General_100_BIN2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]');

    UPDATE dbo.consultation_request SET phone = N'0987654321'
    WHERE id IN (1002, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2010, 2011, 2015, 2016, 2017, 2019)
      AND NOT (DATALENGTH(phone) = 20
          AND phone COLLATE Latin1_General_100_BIN2 LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]');

    IF EXISTS (SELECT 1 FROM dbo.users WHERE phone IS NOT NULL AND NOT (
           DATALENGTH(phone) = 10 AND phone COLLATE Latin1_General_100_BIN2
           LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'))
        THROW 51977, 'Invalid dbo.users.phone values remain outside approved records.', 1;
    IF EXISTS (SELECT 1 FROM dbo.consultation_request WHERE NOT (
           DATALENGTH(phone) = 20 AND phone COLLATE Latin1_General_100_BIN2
           LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'))
        THROW 51978, 'Invalid consultation_request.phone values remain outside approved records.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.users')
               AND name = N'ck_users_phone_format')
        THROW 51979, 'Constraint ck_users_phone_format already exists; inspect its definition before proceeding.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request')
               AND name = N'ck_consultation_request_phone'
               AND definition NOT LIKE N'%len(ltrim(rtrim%')
               AND definition NOT LIKE N'%LEN(LTRIM(RTRIM%')
        THROW 51982, 'Existing consultation phone constraint is not the reviewed legacy nonblank constraint; inspect manually.', 1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request')
               AND name = N'ck_consultation_request_phone')
        ALTER TABLE dbo.consultation_request DROP CONSTRAINT ck_consultation_request_phone;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.consultation_request')
                   AND name = N'ck_consultation_request_phone')
        ALTER TABLE dbo.consultation_request WITH CHECK ADD CONSTRAINT ck_consultation_request_phone CHECK
          (DATALENGTH(phone) = 20 AND phone COLLATE Latin1_General_100_BIN2
           LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]');

    ALTER TABLE dbo.users WITH CHECK ADD CONSTRAINT ck_users_phone_format CHECK
      (phone IS NULL OR (DATALENGTH(phone) = 10 AND phone COLLATE Latin1_General_100_BIN2
       LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'));

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name IN (N'ck_users_phone_format', N'ck_consultation_request_phone')
           AND (is_disabled = 1 OR is_not_trusted = 1))
    THROW 51980, 'Phone format constraints are disabled or untrusted.', 1;
