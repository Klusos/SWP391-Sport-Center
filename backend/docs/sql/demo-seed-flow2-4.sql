/*
  Fictional demo data for Flow 2 (classes and schedules), Flow 3 (course payments)
  and Flow 4 (training plans, attendance, results, progress). SQL Server 2016+.

  SAFETY: use only on a dedicated local development/demo database, after
  demo-seed.sql and every schema migration up to phase10-1-flow4-training.sql.
  Review this script, then deliberately change @ConfirmLocalDemoDatabase to 1.

  Additive and transactional: it only inserts new rows (all names start with
  "Demo Seed") and never updates or deletes existing data. If the demo course
  already exists the script stops without changes. Dates are relative to the
  day the script runs and use the SQL Server clock, which must be on Vietnam
  time (Asia/Ho_Chi_Minh) like the backend.

  The file contains Vietnamese text. With sqlcmd pass -f 65001, e.g.
    sqlcmd -S localhost -d SportCenterDB -U <user> -C -b -I -f 65001 -i demo-seed-flow2-4.sql
  SSMS opens it as UTF-8 automatically.

  What it creates (logins use the demo-seed.sql password):
  - "Demo Seed Lớp tối T3-T5" (ongoing, Coach demo.coach1): demo.member02 and
    demo.member03 CONFIRMED and paid at the counter; three COMPLETED sessions
    with attendance, results and evaluations; one ended SCHEDULED session left
    for the live demo (take attendance -> complete -> record results); four
    upcoming sessions; a training plan with four exercises.
  - "Demo Seed Lớp sáng T7-CN" (opens in 10 days, registration open): no
    registrations, so demo.member01 can register and pay with VNPay.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ConfirmLocalDemoDatabase BIT = 0; -- Change to 1 only after checking the connected server/database.
IF @ConfirmLocalDemoDatabase <> 1
    THROW 51101, 'Flow 2-4 demo seed is disabled. Verify this is a dedicated local database, then set @ConfirmLocalDemoDatabase = 1.', 1;
IF DB_NAME() <> N'SportCenterDB'
    THROW 51102, 'Flow 2-4 demo seed may run only in SportCenterDB.', 1;
IF @@TRANCOUNT <> 0
    THROW 51103, 'Run demo-seed-flow2-4.sql without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.DemoSeed.Flow2to4',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 51104, 'Could not acquire the Flow 2-4 demo seed lock; retry after other seed work finishes.', 1;

    IF OBJECT_ID(N'dbo.training_plan', N'U') IS NULL OR OBJECT_ID(N'dbo.facility_sport', N'U') IS NULL
       OR COL_LENGTH(N'dbo.course_registration', N'payment_deadline') IS NULL
       OR COL_LENGTH(N'dbo.payment', N'monthly_price_snapshot') IS NULL
        THROW 51105, 'Apply every migration up to phase10-1-flow4-training.sql before this seed.', 1;

    IF EXISTS (SELECT 1 FROM dbo.course WHERE name = N'Demo Seed Cầu lông cơ bản')
        THROW 51106, 'Flow 2-4 demo data already exists (course "Demo Seed Cầu lông cơ bản"). No rows were changed.', 1;

    DECLARE @sportId INT = (SELECT id FROM dbo.sport WHERE name = 'Demo Badminton' AND status = 'ACTIVE');
    DECLARE @coachUserId INT = (SELECT id FROM dbo.users WHERE email = 'demo.coach1@sportcenter.example.test' AND role = 'COACH' AND status = 'ACTIVE');
    DECLARE @coachId INT = (SELECT id FROM dbo.coach WHERE user_id = @coachUserId AND status = 'ACTIVE');
    DECLARE @receptionId INT = (SELECT id FROM dbo.users WHERE email = 'demo.reception1@sportcenter.example.test' AND role = 'RECEPTIONIST');
    DECLARE @member2 INT = (SELECT id FROM dbo.users WHERE email = 'demo.member02@sportcenter.example.test' AND role = 'MEMBER' AND status = 'ACTIVE');
    DECLARE @member3 INT = (SELECT id FROM dbo.users WHERE email = 'demo.member03@sportcenter.example.test' AND role = 'MEMBER' AND status = 'ACTIVE');
    IF @sportId IS NULL OR @coachId IS NULL OR @receptionId IS NULL OR @member2 IS NULL OR @member3 IS NULL
        THROW 51107, 'Run demo-seed.sql first: Demo Badminton, demo.coach1, demo.reception1, demo.member02 and demo.member03 are required.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.coach_sport WHERE coach_id = @coachId AND sport_id = @sportId)
        THROW 51108, 'demo.coach1 must be qualified for Demo Badminton (coach_sport).', 1;

    DECLARE @now DATETIME2(7) = SYSDATETIME();
    DECLARE @today DATE = CONVERT(date, @now);
    DECLARE @todayAt DATETIME2(7) = CAST(@today AS DATETIME2(7));

    -- Facility usable for Demo Badminton.
    INSERT dbo.facility (name, status, type, sport_id) VALUES (N'Demo Seed Sân cầu lông A', 'ACTIVE', N'COURT', @sportId);
    DECLARE @facilityId INT = SCOPE_IDENTITY();
    INSERT dbo.facility_sport (facility_id, sport_id) VALUES (@facilityId, @sportId);

    INSERT dbo.course (description, learning_outcome, level, name, status, target_age_max, target_age_min, sport_id)
    VALUES (N'Khóa demo: làm quen cầu lông từ con số 0.', N'Cầm vợt đúng, di chuyển cơ bản, giao cầu và đánh cầu cao sâu.',
            'BEGINNER', N'Demo Seed Cầu lông cơ bản', 'ACTIVE', 60, 16, @sportId);
    DECLARE @courseId INT = SCOPE_IDENTITY();

    -- Ongoing offering: started 14 days ago, so registration closed before it began.
    DECLARE @ongoingStart DATE = DATEADD(day, -14, @today);
    INSERT dbo.course_offering (cancellation_deadline, end_date, name, planned_sessions, price, registration_deadline,
                                session_duration, start_date, status, course_id)
    VALUES (DATEADD(hour, -1, CAST(@ongoingStart AS DATETIME2(7))), DATEADD(day, 42, @today), N'Demo Seed Đợt đang học',
            8, 1000000, DATEADD(hour, -1, CAST(@ongoingStart AS DATETIME2(7))), 90, @ongoingStart, 'OPEN', @courseId);
    DECLARE @ongoingOfferingId INT = SCOPE_IDENTITY();
    INSERT dbo.class (capacity, name, status, coach_id, course_offering_id)
    VALUES (12, N'Demo Seed Lớp tối T3-T5', 'ACTIVE', @coachId, @ongoingOfferingId);
    DECLARE @ongoingClassId INT = SCOPE_IDENTITY();

    -- Upcoming offering: registration open for the Flow 2/3 demo.
    DECLARE @upcomingStart DATE = DATEADD(day, 10, @today);
    INSERT dbo.course_offering (cancellation_deadline, end_date, name, planned_sessions, price, registration_deadline,
                                session_duration, start_date, status, course_id)
    VALUES (DATEADD(hour, 23, CAST(DATEADD(day, 8, @today) AS DATETIME2(7))), DATEADD(day, 45, @today),
            N'Demo Seed Đợt sắp khai giảng', 8, 1200000,
            DATEADD(hour, 23, CAST(DATEADD(day, 7, @today) AS DATETIME2(7))), 90, @upcomingStart, 'OPEN', @courseId);
    DECLARE @upcomingOfferingId INT = SCOPE_IDENTITY();
    INSERT dbo.class (capacity, name, status, coach_id, course_offering_id)
    VALUES (10, N'Demo Seed Lớp sáng T7-CN', 'ACTIVE', @coachId, @upcomingOfferingId);
    DECLARE @upcomingClassId INT = SCOPE_IDENTITY();

    -- Sessions: offsets in days from today; 18:00-19:30 for the ongoing class, 08:00-09:30 for the upcoming one.
    DECLARE @sessionPlan TABLE (seq INT PRIMARY KEY, class_id INT NOT NULL, day_offset INT NOT NULL, start_hour INT NOT NULL, status VARCHAR(30) NOT NULL);
    INSERT @sessionPlan VALUES
        (1, @ongoingClassId, -14, 18, 'COMPLETED'), (2, @ongoingClassId, -10, 18, 'COMPLETED'),
        (3, @ongoingClassId, -7, 18, 'COMPLETED'), (4, @ongoingClassId, -3, 18, 'SCHEDULED'),
        (5, @ongoingClassId, 4, 18, 'SCHEDULED'), (6, @ongoingClassId, 7, 18, 'SCHEDULED'),
        (7, @ongoingClassId, 11, 18, 'SCHEDULED'), (8, @ongoingClassId, 14, 18, 'SCHEDULED'),
        (11, @upcomingClassId, 10, 8, 'SCHEDULED'), (12, @upcomingClassId, 11, 8, 'SCHEDULED'),
        (13, @upcomingClassId, 17, 8, 'SCHEDULED'), (14, @upcomingClassId, 18, 8, 'SCHEDULED'),
        (15, @upcomingClassId, 24, 8, 'SCHEDULED'), (16, @upcomingClassId, 25, 8, 'SCHEDULED'),
        (17, @upcomingClassId, 31, 8, 'SCHEDULED'), (18, @upcomingClassId, 32, 8, 'SCHEDULED');
    DECLARE @sessions TABLE (seq INT PRIMARY KEY, session_id INT NOT NULL);
    DECLARE @seq INT = (SELECT MIN(seq) FROM @sessionPlan);
    WHILE @seq IS NOT NULL
    BEGIN
        INSERT dbo.class_session (class_id, facility_id, start_time, end_time, status)
        SELECT class_id, @facilityId,
               DATEADD(hour, start_hour, CAST(DATEADD(day, day_offset, @today) AS DATETIME)),
               DATEADD(minute, 90, DATEADD(hour, start_hour, CAST(DATEADD(day, day_offset, @today) AS DATETIME))),
               status
        FROM @sessionPlan WHERE seq = @seq;
        INSERT @sessions VALUES (@seq, SCOPE_IDENTITY());
        SET @seq = (SELECT MIN(seq) FROM @sessionPlan WHERE seq > @seq);
    END;

    -- Confirmed, counter-paid registrations with the member's current membership discount snapshot.
    DECLARE @registered DATETIME2(7) = DATEADD(day, -18, @todayAt);
    DECLARE @enrolments TABLE (member_id INT PRIMARY KEY, txn VARCHAR(40) NOT NULL);
    INSERT @enrolments VALUES (@member2, 'DEMO-SEED-F4-' + CONVERT(VARCHAR(8), @today, 112) + '-M2'),
                              (@member3, 'DEMO-SEED-F4-' + CONVERT(VARCHAR(8), @today, 112) + '-M3');
    DECLARE @registrations TABLE (member_id INT PRIMARY KEY, registration_id INT NOT NULL, final_price DECIMAL(12,2) NOT NULL);
    DECLARE @memberId INT = (SELECT MIN(member_id) FROM @enrolments);
    WHILE @memberId IS NOT NULL
    BEGIN
        DECLARE @discountPercent DECIMAL(5,2) = COALESCE((
            SELECT TOP 1 p.course_discount_percent FROM dbo.membership m JOIN dbo.membership_plan p ON p.id = m.plan_id
            WHERE m.member_id = @memberId AND m.status = 'ACTIVE' AND m.start_date <= @today AND m.end_date >= @today
            ORDER BY p.course_discount_percent DESC), 0);
        DECLARE @discount DECIMAL(12,2) = ROUND(1000000 * @discountPercent / 100, 2);
        INSERT dbo.course_registration (member_id, class_id, status, registered_at, original_price, membership_discount, final_price)
        VALUES (@memberId, @ongoingClassId, 'CONFIRMED', @registered, 1000000, @discount, 1000000 - @discount);
        INSERT @registrations VALUES (@memberId, SCOPE_IDENTITY(), 1000000 - @discount);
        SET @memberId = (SELECT MIN(member_id) FROM @enrolments WHERE member_id > @memberId);
    END;
    INSERT dbo.payment (member_id, amount, method, purpose, reference_id, status, transaction_code, processed_by, note, created_at, paid_at)
    SELECT r.member_id, r.final_price, 'COUNTER', 'COURSE_REGISTRATION', r.registration_id, 'SUCCESS', e.txn, @receptionId,
           N'Demo seed: thanh toán tại quầy', @registered, DATEADD(minute, 20, @registered)
    FROM @registrations r JOIN @enrolments e ON e.member_id = r.member_id;

    -- Attendance for the three completed sessions (member03 missed the second one).
    DECLARE @attendancePlan TABLE (session_seq INT NOT NULL, member_id INT NOT NULL, status VARCHAR(30) NOT NULL);
    INSERT @attendancePlan VALUES
        (1, @member2, 'PRESENT'), (2, @member2, 'PRESENT'), (3, @member2, 'PRESENT'),
        (1, @member3, 'PRESENT'), (2, @member3, 'ABSENT'), (3, @member3, 'PRESENT');
    DECLARE @marked TABLE (attendance_id INT NOT NULL, status VARCHAR(30) NOT NULL, marked_at DATETIME2(7) NOT NULL);
    INSERT dbo.attendance (class_session_id, member_id, status, marked_by, marked_at, note)
    OUTPUT inserted.id, inserted.status, inserted.marked_at INTO @marked
    SELECT s.session_id, a.member_id, a.status, @coachUserId, DATEADD(minute, 95, CAST(cs.start_time AS DATETIME2(7))), NULL
    FROM @attendancePlan a JOIN @sessions s ON s.seq = a.session_seq JOIN dbo.class_session cs ON cs.id = s.session_id;
    INSERT dbo.attendance_history (attendance_id, old_status, new_status, changed_by, changed_at, reason)
    SELECT attendance_id, NULL, status, @coachUserId, marked_at, NULL FROM @marked;

    -- Training plan, goal and exercises.
    INSERT dbo.training_plan (class_id, goal, description, updated_by, created_at, updated_at)
    VALUES (@ongoingClassId, N'Đánh cầu qua lại liên tục 20 lần và giao cầu ngắn ổn định',
            N'Mỗi buổi: khởi động 10 phút, kỹ thuật 50 phút, đấu tập 20 phút, giãn cơ 10 phút.',
            @coachUserId, DATEADD(day, -15, @todayAt), DATEADD(day, -15, @todayAt));
    DECLARE @planId INT = SCOPE_IDENTITY();
    INSERT dbo.training_exercise (plan_id, name, sets, reps, duration_minutes, notes, sort_order) VALUES
        (@planId, N'Khởi động và bước chéo', NULL, NULL, 10, N'Chạy nhẹ, xoay khớp', 0),
        (@planId, N'Giao cầu ngắn', 3, 15, NULL, N'Cầu qua lưới thấp, rơi gần vạch giao cầu', 1),
        (@planId, N'Đánh cầu cao sâu (clear)', 4, 10, NULL, NULL, 2),
        (@planId, N'Đấu tập đơn', NULL, NULL, 20, N'Tính điểm theo luật rally', 3);

    -- Results for PRESENT members and one progress evaluation each.
    DECLARE @resultPlan TABLE (session_seq INT NOT NULL, member_id INT NOT NULL, score INT NOT NULL, notes NVARCHAR(1000) NULL);
    INSERT @resultPlan VALUES
        (1, @member2, 6, N'Cầm vợt đúng, bộ chân còn chậm'), (2, @member2, 7, N'Giao cầu ngắn ổn hơn'),
        (3, @member2, 8, N'Clear đủ sâu, cần giữ nhịp khi đấu tập'),
        (1, @member3, 5, N'Cần luyện thêm cách cầm vợt'), (3, @member3, 6, N'Tiến bộ ở giao cầu');
    INSERT dbo.training_result (class_session_id, member_id, score, notes, recorded_by, recorded_at)
    SELECT s.session_id, r.member_id, r.score, r.notes, @coachUserId, DATEADD(minute, 100, CAST(cs.start_time AS DATETIME2(7)))
    FROM @resultPlan r JOIN @sessions s ON s.seq = r.session_seq JOIN dbo.class_session cs ON cs.id = s.session_id;
    INSERT dbo.progress_evaluation (class_id, member_id, rating, comment, evaluated_by, evaluated_at) VALUES
        (@ongoingClassId, @member2, 4, N'Tiến bộ đều, đúng lộ trình mục tiêu.', @coachUserId, DATEADD(day, -6, @todayAt)),
        (@ongoingClassId, @member3, 3, N'Cần đi học đều hơn để bắt kịp lớp.', @coachUserId, DATEADD(day, -6, @todayAt));

    COMMIT TRANSACTION;

    SELECT N'Flow 2-4 demo data created' AS result, @ongoingClassId AS ongoing_class_id,
           @upcomingClassId AS upcoming_class_id, (SELECT session_id FROM @sessions WHERE seq = 4) AS live_demo_session_id;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
