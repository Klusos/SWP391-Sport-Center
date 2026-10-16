-- SQL Server 2016+ manual migration for Flow 2 PT bookings.
-- Run after the core users, coach, sport, class_session, Membership, and MembershipPlan tables exist.
-- Back up the target DB and stop application writers. Do not run inside an outer transaction.
-- This script is transactional and rerunnable. It never drops tables or indexes; it replaces only
-- the recognized legacy status CHECK. Existing pending-row deadlines are backfilled only after opt-in.
-- JPA LocalDateTime normally maps to SQL Server datetime2(7), but this entity explicitly declares
-- columnDefinition="datetime" for its temporal columns; this migration follows that explicit mapping.
-- DECIMAL(12,2) is expected as sys.columns precision=12, scale=2, max_length=9 bytes.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

-- Change to 1 only after reviewing legacy PENDING_PAYMENT timestamps in the read-only preflight.
DECLARE @ConfirmLegacyPendingDeadlineBackfill BIT = 0;

IF DB_NAME() <> N'SportCenterDB'
    THROW 50401, 'Select SportCenterDB before running the PT booking migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50402, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase2.PersonalTrainingBooking.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50403, 'Could not acquire the PT booking migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL OR OBJECT_ID(N'dbo.coach', N'U') IS NULL
       OR OBJECT_ID(N'dbo.sport', N'U') IS NULL OR OBJECT_ID(N'dbo.class_session', N'U') IS NULL
        THROW 50404, 'Expected dbo.users, dbo.coach, dbo.sport, and dbo.class_session core tables.', 1;
    IF OBJECT_ID(N'dbo.personal_training_booking') IS NOT NULL
       AND OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NULL
        THROW 50405, 'dbo.personal_training_booking is occupied by a non-table object.', 1;

    -- Unknown CHECKs mentioning status may still reject EXPIRED even when the managed
    -- status constraint is correct. Fail closed instead of guessing their semantics.
    IF OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NOT NULL
       AND EXISTS (
            SELECT 1 FROM sys.check_constraints cc
            WHERE cc.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
              AND LOWER(cc.definition) LIKE N'%status%'
              AND cc.name <> N'ck_personal_training_booking_status'
       )
        THROW 50414, 'An unexpected CHECK constraint references status; inspect it before migration.', 1;

    IF OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.personal_training_booking (
            id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_personal_training_booking PRIMARY KEY,
            member_id INT NOT NULL,
            coach_id INT NOT NULL,
            sport_id INT NOT NULL,
            start_time DATETIME NOT NULL,
            end_time DATETIME NOT NULL,
            original_price DECIMAL(12,2) NOT NULL,
            discount DECIMAL(12,2) NOT NULL,
            final_price DECIMAL(12,2) NOT NULL,
            status VARCHAR(30) NOT NULL,
            created_at DATETIME NOT NULL CONSTRAINT df_personal_training_booking_created_at DEFAULT (GETDATE()),
            payment_expires_at DATETIME NULL,
            CONSTRAINT ck_personal_training_booking_status
                CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
            CONSTRAINT ck_personal_training_booking_payment_expiry
                CHECK (status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL),
            CONSTRAINT ck_personal_training_booking_times CHECK (end_time > start_time),
            CONSTRAINT ck_personal_training_booking_prices
                CHECK (original_price >= 0 AND discount >= 0 AND final_price >= 0),
            CONSTRAINT fk_pt_booking_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_pt_booking_coach FOREIGN KEY (coach_id) REFERENCES dbo.coach(id),
            CONSTRAINT fk_pt_booking_sport FOREIGN KEY (sport_id) REFERENCES dbo.sport(id)
        );
    END;

    -- Upgrade a previously-created PT table without rewriting terminal history.
    IF COL_LENGTH(N'dbo.personal_training_booking', N'payment_expires_at') IS NULL
        ALTER TABLE dbo.personal_training_booking ADD payment_expires_at DATETIME NULL;

    -- Existing pending rows receive the original 15-minute deadline derived from creation time.
    -- created_at is a timezone-less DATETIME; verify legacy writers used the business timezone first.
    IF @ConfirmLegacyPendingDeadlineBackfill = 0
       AND EXISTS (SELECT 1 FROM dbo.personal_training_booking
                   WHERE status = 'PENDING_PAYMENT' AND payment_expires_at IS NULL)
        THROW 50416, 'Legacy pending rows need deadline backfill; inspect the preflight and explicitly confirm the timestamp basis.', 1;

    IF @ConfirmLegacyPendingDeadlineBackfill = 1
       AND EXISTS (SELECT 1 FROM dbo.personal_training_booking
                   WHERE status = 'PENDING_PAYMENT' AND payment_expires_at IS NULL
                     AND (created_at IS NULL OR created_at > CONVERT(DATETIME, '9999-12-31T23:44:59.997', 126)))
        THROW 50417, 'A legacy pending row has a null or out-of-range created_at; deadline backfill was stopped.', 1;

    UPDATE dbo.personal_training_booking
       SET payment_expires_at = DATEADD(MINUTE, 15, created_at)
     WHERE @ConfirmLegacyPendingDeadlineBackfill = 1
       AND status = 'PENDING_PAYMENT' AND payment_expires_at IS NULL;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
               AND name = N'ck_personal_training_booking_status'
               AND definition NOT LIKE N'%EXPIRED%')
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                       AND name = N'ck_personal_training_booking_status'
                       AND definition LIKE N'%PENDING_PAYMENT%' AND definition LIKE N'%CONFIRMED%'
                       AND definition LIKE N'%CANCELLED%' AND LOWER(definition) NOT LIKE N'% and %'
                       AND LOWER(definition) NOT LIKE N'%not %'
                       AND definition NOT LIKE N'%<>%' AND definition NOT LIKE N'%!=%')
            THROW 50413, 'Existing PT booking status constraint is incompatible; review it before migration.', 1;
        ALTER TABLE dbo.personal_training_booking DROP CONSTRAINT ck_personal_training_booking_status;
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_status')
        ALTER TABLE dbo.personal_training_booking WITH CHECK ADD CONSTRAINT ck_personal_training_booking_status
            CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_payment_expiry')
        ALTER TABLE dbo.personal_training_booking WITH CHECK ADD CONSTRAINT ck_personal_training_booking_payment_expiry
            CHECK (status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL);

    -- Validate existing table shape instead of silently accepting an incompatible schema.
    IF EXISTS (SELECT required.column_name FROM (VALUES
        (N'id'), (N'member_id'), (N'coach_id'), (N'sport_id'), (N'start_time'), (N'end_time'),
        (N'original_price'), (N'discount'), (N'final_price'), (N'status'), (N'created_at')
    ) required(column_name) WHERE COL_LENGTH(N'dbo.personal_training_booking', required.column_name) IS NULL)
        THROW 50406, 'Existing dbo.personal_training_booking is incomplete; inspect it before migration.', 1;

    DECLARE @expectedColumns TABLE (
        column_name SYSNAME PRIMARY KEY, type_name SYSNAME, max_length SMALLINT,
        precision_value TINYINT, scale_value TINYINT, nullable BIT, identity_column BIT
    );
    INSERT @expectedColumns VALUES
        (N'id', N'int', 4, 10, 0, 0, 1),
        (N'member_id', N'int', 4, 10, 0, 0, 0),
        (N'coach_id', N'int', 4, 10, 0, 0, 0),
        (N'sport_id', N'int', 4, 10, 0, 0, 0),
        (N'start_time', N'datetime', 8, 23, 3, 0, 0),
        (N'end_time', N'datetime', 8, 23, 3, 0, 0),
        (N'original_price', N'decimal', 9, 12, 2, 0, 0),
        (N'discount', N'decimal', 9, 12, 2, 0, 0),
        (N'final_price', N'decimal', 9, 12, 2, 0, 0),
        (N'status', N'varchar', 30, 0, 0, 0, 0),
        (N'created_at', N'datetime', 8, 23, 3, 0, 0),
        (N'payment_expires_at', N'datetime', 8, 23, 3, 1, 0);
    IF EXISTS (
        SELECT 1 FROM @expectedColumns e
        LEFT JOIN sys.columns c ON c.object_id = OBJECT_ID(N'dbo.personal_training_booking') AND c.name = e.column_name
        LEFT JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL OR t.name <> e.type_name OR c.max_length <> e.max_length
           OR c.precision <> e.precision_value OR c.scale <> e.scale_value
           OR c.is_nullable <> e.nullable OR c.is_identity <> e.identity_column
           OR c.is_computed <> 0 OR c.generated_always_type <> 0
    ) THROW 50409, 'Existing PT booking column type, size, nullability, or identity mapping is incompatible.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.personal_training_booking') AND i.is_primary_key = 1
          AND i.is_unique = 1 AND i.is_disabled = 0 AND ic.key_ordinal = 1 AND c.name = N'id'
          AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
               AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
    ) THROW 50407, 'PT booking id must be the single-column primary key.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns src ON src.object_id = fkc.parent_object_id AND src.column_id = fkc.parent_column_id
        JOIN sys.columns dst ON dst.object_id = fkc.referenced_object_id AND dst.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking') AND fk.name = N'fk_pt_booking_member'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.users') AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND src.name = N'member_id' AND dst.name = N'id'
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns src ON src.object_id = fkc.parent_object_id AND src.column_id = fkc.parent_column_id
        JOIN sys.columns dst ON dst.object_id = fkc.referenced_object_id AND dst.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking') AND fk.name = N'fk_pt_booking_coach'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.coach') AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND src.name = N'coach_id' AND dst.name = N'id'
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns src ON src.object_id = fkc.parent_object_id AND src.column_id = fkc.parent_column_id
        JOIN sys.columns dst ON dst.object_id = fkc.referenced_object_id AND dst.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking') AND fk.name = N'fk_pt_booking_sport'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.sport') AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND src.name = N'sport_id' AND dst.name = N'id'
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
    )
        THROW 50408, 'PT booking foreign keys are missing, disabled, or reference the wrong tables.', 1;

    IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')) <> 3
        THROW 50415, 'Unexpected additional PT booking foreign keys exist; review their behavior before migration.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_status' AND is_disabled = 0 AND is_not_trusted = 0
                   AND definition LIKE N'%PENDING_PAYMENT%' AND definition LIKE N'%CONFIRMED%'
                   AND definition LIKE N'%CANCELLED%' AND definition LIKE N'%EXPIRED%'
                   AND LOWER(definition) NOT LIKE N'% and %' AND LOWER(definition) NOT LIKE N'%not %'
                   AND definition NOT LIKE N'%<>%' AND definition NOT LIKE N'%!=%')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_payment_expiry' AND is_disabled = 0 AND is_not_trusted = 0
                   AND definition LIKE N'%PENDING_PAYMENT%' AND definition LIKE N'%payment_expires_at%')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_times' AND is_disabled = 0 AND is_not_trusted = 0
                   AND definition LIKE N'%start_time%' AND definition LIKE N'%end_time%' AND definition LIKE N'%>%')
       OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ck_personal_training_booking_prices' AND is_disabled = 0 AND is_not_trusted = 0
                   AND definition LIKE N'%original_price%' AND definition LIKE N'%discount%' AND definition LIKE N'%final_price%'
                   AND definition LIKE N'%>=%')
        THROW 50412, 'PT booking integrity CHECK constraints are missing, disabled, untrusted, or incompatible.', 1;

    IF EXISTS (
        SELECT 1 FROM sys.indexes i WHERE i.object_id = OBJECT_ID(N'dbo.personal_training_booking')
          AND i.name = N'ix_pt_booking_coach_time'
          AND (i.is_disabled = 1 OR i.is_unique = 1 OR i.has_filter = 1 OR i.type <> 2 OR i.is_hypothetical = 1
            OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 4
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 1 AND c.name = N'coach_id')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 2 AND c.name = N'start_time')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 3 AND c.name = N'end_time')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 4 AND c.name = N'status')
            OR EXISTS (SELECT 1 FROM sys.index_columns k WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                       AND k.key_ordinal > 0 AND k.is_descending_key = 1))
        THROW 50410, 'Existing PT coach-time index has the wrong definition or is disabled.', 1;
    IF EXISTS (
        SELECT 1 FROM sys.indexes i WHERE i.object_id = OBJECT_ID(N'dbo.personal_training_booking')
          AND i.name = N'ix_pt_booking_member_time'
          AND (i.is_disabled = 1 OR i.is_unique = 1 OR i.has_filter = 1 OR i.type <> 2 OR i.is_hypothetical = 1
            OR (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 4
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 1 AND c.name = N'member_id')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 2 AND c.name = N'start_time')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 3 AND c.name = N'end_time')
            OR NOT EXISTS (SELECT 1 FROM sys.index_columns k JOIN sys.columns c ON c.object_id = k.object_id AND c.column_id = k.column_id
                           WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal = 4 AND c.name = N'status')
            OR EXISTS (SELECT 1 FROM sys.index_columns k WHERE k.object_id = i.object_id AND k.index_id = i.index_id
                       AND k.key_ordinal > 0 AND k.is_descending_key = 1))
        THROW 50411, 'Existing PT member-time index has the wrong definition or is disabled.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ix_pt_booking_coach_time' AND is_disabled = 0)
        CREATE INDEX ix_pt_booking_coach_time ON dbo.personal_training_booking(coach_id, start_time, end_time, status);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.personal_training_booking')
                   AND name = N'ix_pt_booking_member_time' AND is_disabled = 0)
        CREATE INDEX ix_pt_booking_member_time ON dbo.personal_training_booking(member_id, start_time, end_time, status);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Configuration required before enabling booking creation:
-- app.pt.session-base-price defaults to 500000; override it with APP_PT_SESSION_BASE_PRICE when required.
-- Do not use MembershipPlan.price. The application does not run this migration automatically.
-- Flow 3 may transition PENDING_PAYMENT to CONFIRMED only after it verifies the matching payment.
-- Flow 3 must lock the booking row and reject confirmation at/after payment_expires_at.
-- A late successful payment remains a payment-side exception; Flow 3 must not confirm the EXPIRED booking.
