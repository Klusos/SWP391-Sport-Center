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
    ;THROW 50401, 'Select SportCenterDB before running the PT booking migration.', 1;
IF @@TRANCOUNT <> 0
    ;THROW 50402, 'Run this migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase2.PersonalTrainingBooking.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        ;THROW 50403, 'Could not acquire the PT booking migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL OR OBJECT_ID(N'dbo.coach', N'U') IS NULL
       OR OBJECT_ID(N'dbo.sport', N'U') IS NULL OR OBJECT_ID(N'dbo.class_session', N'U') IS NULL
        ;THROW 50404, 'Expected dbo.users, dbo.coach, dbo.sport, and dbo.class_session core tables.', 1;
    IF OBJECT_ID(N'dbo.personal_training_booking') IS NOT NULL
       AND OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NULL
        ;THROW 50405, 'dbo.personal_training_booking is occupied by a non-table object.', 1;

    -- Unknown CHECKs mentioning status may still reject EXPIRED even when the managed
    -- status constraint is correct. Fail closed instead of guessing their semantics.
    IF OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NOT NULL
       AND EXISTS (
            SELECT 1 FROM sys.check_constraints cc
            WHERE cc.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking')
              AND LOWER(cc.definition) LIKE N'%status%'
              AND cc.name <> N'ck_personal_training_booking_status'
       )
        ;THROW 50414, 'An unexpected CHECK constraint references status; inspect it before migration.', 1;

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
        ;THROW 50416, 'Legacy pending rows need deadline backfill; inspect the preflight and explicitly confirm the timestamp basis.', 1;

    IF @ConfirmLegacyPendingDeadlineBackfill = 1
       AND EXISTS (SELECT 1 FROM dbo.personal_training_booking
                   WHERE status = 'PENDING_PAYMENT' AND payment_expires_at IS NULL
                     AND (created_at IS NULL OR created_at > CONVERT(DATETIME, '9999-12-31T23:44:59.997', 126)))