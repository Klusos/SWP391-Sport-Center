/*
  Fictional SportCenter development/demo seed data. SQL Server 2016+.

  SAFETY: use only on a dedicated local development/demo database. Connect to
  the intended local SQL Server instance, select SportCenterDB, review this
  script, then deliberately change @ConfirmLocalDemoDatabase to 1 below.
  The database-name guard alone cannot prove which server you connected to.

  This is an additive, transactional, repeatable seed. It never updates or
  deletes existing rows, resets passwords, or specifies identity values.
  Existing deterministic records with incompatible values/relationships cause
  a clear error and rollback. Run only after the approved core schema and the
  existing schema migrations have been applied.

  Login endpoint: POST /api/auth/login with { "email": "...", "password": "..." }
  All seeded accounts use password: SportCenterDemo!2026
  Passwords are stored only as BCrypt hashes; all sample email addresses use
  the reserved example.test domain. Keep this seed and its shared demo password
  out of production environments.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ConfirmLocalDemoDatabase BIT = 0; -- Change to 1 only after checking the connected server/database.
IF @ConfirmLocalDemoDatabase <> 1
    THROW 50401, 'Demo seed is disabled. Verify this is a dedicated local database, then set @ConfirmLocalDemoDatabase = 1.', 1;
IF DB_NAME() <> N'SportCenterDB'
    THROW 50402, 'Demo seed may run only in SportCenterDB.', 1;
IF @@TRANCOUNT <> 0
    THROW 50403, 'Run demo-seed.sql without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.DemoSeed',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50404, 'Could not acquire the demo seed lock; retry after other seed work finishes.', 1;

    DECLARE @requiredTables TABLE (table_name SYSNAME PRIMARY KEY);
    INSERT @requiredTables VALUES
        (N'users'), (N'coach'), (N'coach_sport'), (N'sport'),
        (N'member_profile'), (N'membership_plan'), (N'membership');
    IF EXISTS (
        SELECT 1
        FROM @requiredTables AS required_table
        WHERE OBJECT_ID(N'dbo.' + QUOTENAME(required_table.table_name), N'U') IS NULL
    )
        THROW 50405, 'Required core tables are missing. Apply/review the approved core schema before seeding.', 1;

    IF COL_LENGTH(N'dbo.users', N'id') IS NULL OR COL_LENGTH(N'dbo.users', N'name') IS NULL
       OR COL_LENGTH(N'dbo.users', N'email') IS NULL OR COL_LENGTH(N'dbo.users', N'date_of_birth') IS NULL
       OR COL_LENGTH(N'dbo.users', N'gender') IS NULL OR COL_LENGTH(N'dbo.users', N'phone') IS NULL
       OR COL_LENGTH(N'dbo.users', N'password_hash') IS NULL OR COL_LENGTH(N'dbo.users', N'role') IS NULL
       OR COL_LENGTH(N'dbo.users', N'status') IS NULL OR COL_LENGTH(N'dbo.users', N'created_at') IS NULL
       OR COL_LENGTH(N'dbo.coach', N'id') IS NULL OR COL_LENGTH(N'dbo.coach', N'user_id') IS NULL
       OR COL_LENGTH(N'dbo.coach', N'status') IS NULL
       OR COL_LENGTH(N'dbo.coach_sport', N'id') IS NULL OR COL_LENGTH(N'dbo.coach_sport', N'coach_id') IS NULL
       OR COL_LENGTH(N'dbo.coach_sport', N'sport_id') IS NULL
       OR COL_LENGTH(N'dbo.coach_sport', N'experience_years') IS NULL
       OR COL_LENGTH(N'dbo.coach_sport', N'certification') IS NULL
       OR COL_LENGTH(N'dbo.sport', N'id') IS NULL OR COL_LENGTH(N'dbo.sport', N'name') IS NULL
       OR COL_LENGTH(N'dbo.sport', N'description') IS NULL OR COL_LENGTH(N'dbo.sport', N'status') IS NULL
       OR COL_LENGTH(N'dbo.member_profile', N'id') IS NULL OR COL_LENGTH(N'dbo.member_profile', N'member_id') IS NULL
       OR COL_LENGTH(N'dbo.member_profile', N'height') IS NULL OR COL_LENGTH(N'dbo.member_profile', N'weight') IS NULL
       OR COL_LENGTH(N'dbo.member_profile', N'goal') IS NULL OR COL_LENGTH(N'dbo.member_profile', N'updated_at') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'id') IS NULL OR COL_LENGTH(N'dbo.membership_plan', N'name') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'description') IS NULL OR COL_LENGTH(N'dbo.membership_plan', N'price') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'duration_months') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'course_discount_percent') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'personal_training_discount_percent') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'allow_personal_training_plan') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'allow_one_on_one_coaching') IS NULL
       OR COL_LENGTH(N'dbo.membership_plan', N'status') IS NULL
       OR COL_LENGTH(N'dbo.membership', N'id') IS NULL OR COL_LENGTH(N'dbo.membership', N'member_id') IS NULL
       OR COL_LENGTH(N'dbo.membership', N'plan_id') IS NULL OR COL_LENGTH(N'dbo.membership', N'auto_renew') IS NULL
       OR COL_LENGTH(N'dbo.membership', N'start_date') IS NULL OR COL_LENGTH(N'dbo.membership', N'end_date') IS NULL
       OR COL_LENGTH(N'dbo.membership', N'status') IS NULL
        THROW 50406, 'A required mapped column is missing. Compare the core schema with JPA before seeding.', 1;

    DECLARE @passwordHash VARCHAR(60) = '$2a$10$aN0IlhveNVZGaN7l7wDAIeL/NA390ykFJ37KbAKvUDVSNV0plw10u';
    DECLARE @users TABLE (
        full_name VARCHAR(100) NOT NULL,
        email VARCHAR(150) NOT NULL PRIMARY KEY,
        birth_date DATE NOT NULL,
        gender VARCHAR(1) NOT NULL,
        phone VARCHAR(20) NOT NULL,
        role VARCHAR(30) NOT NULL
    );
    INSERT @users (full_name, email, birth_date, gender, phone, role) VALUES
        ('Demo Administrator', 'demo.admin@sportcenter.example.test', '1985-03-14', 'F', '+1-202-555-0101', 'ADMIN'),
        ('Demo Receptionist One', 'demo.reception1@sportcenter.example.test', '1990-07-22', 'F', '+1-202-555-0102', 'RECEPTIONIST'),
        ('Demo Receptionist Two', 'demo.reception2@sportcenter.example.test', '1992-11-08', 'M', '+1-202-555-0103', 'RECEPTIONIST'),
        ('Demo Coach Avery', 'demo.coach1@sportcenter.example.test', '1987-01-19', 'F', '+1-202-555-0104', 'COACH'),
        ('Demo Coach Kai', 'demo.coach2@sportcenter.example.test', '1984-09-05', 'M', '+1-202-555-0105', 'COACH'),
        ('Demo Coach Morgan', 'demo.coach3@sportcenter.example.test', '1989-12-12', 'X', '+1-202-555-0106', 'COACH'),
        ('Demo Member Riley Chen', 'demo.member01@sportcenter.example.test', '1998-04-17', 'F', '+1-202-555-0107', 'MEMBER'),
        ('Demo Member Jordan Patel', 'demo.member02@sportcenter.example.test', '1995-08-03', 'M', '+1-202-555-0108', 'MEMBER'),
        ('Demo Member Sam Rivera', 'demo.member03@sportcenter.example.test', '2001-02-25', 'X', '+1-202-555-0109', 'MEMBER'),
        ('Demo Member Taylor Nguyen', 'demo.member04@sportcenter.example.test', '1997-06-11', 'F', '+1-202-555-0110', 'MEMBER'),
        ('Demo Member Alex Kim', 'demo.member05@sportcenter.example.test', '1993-10-29', 'M', '+1-202-555-0111', 'MEMBER'),
        ('Demo Member Casey Morgan', 'demo.member06@sportcenter.example.test', '2000-01-07', 'F', '+1-202-555-0112', 'MEMBER'),
        ('Demo Member Jamie Brooks', 'demo.member07@sportcenter.example.test', '1996-05-21', 'M', '+1-202-555-0113', 'MEMBER'),
        ('Demo Member Devon Ali', 'demo.member08@sportcenter.example.test', '1999-09-16', 'X', '+1-202-555-0114', 'MEMBER'),
        ('Demo Member Quinn Park', 'demo.member09@sportcenter.example.test', '2002-03-02', 'F', '+1-202-555-0115', 'MEMBER'),
        ('Demo Member Robin Diaz', 'demo.member10@sportcenter.example.test', '1994-12-30', 'M', '+1-202-555-0116', 'MEMBER');

    -- Refuse case-insensitive email conflicts too, even on a case-sensitive DB.
    IF EXISTS (
        SELECT 1
        FROM @users AS expected
        JOIN dbo.users AS existing_user WITH (UPDLOCK, HOLDLOCK)
          ON LOWER(existing_user.email) = LOWER(expected.email)
        WHERE CONVERT(VARBINARY(300), existing_user.email) <> CONVERT(VARBINARY(300), expected.email)
           OR CONVERT(VARBINARY(200), existing_user.name) <> CONVERT(VARBINARY(200), expected.full_name)
           OR CONVERT(VARBINARY(60), existing_user.role) <> CONVERT(VARBINARY(60), expected.role)
           OR CONVERT(VARBINARY(60), existing_user.status) <> CONVERT(VARBINARY(60), 'ACTIVE')
           OR CONVERT(VARBINARY(60), existing_user.password_hash) <> CONVERT(VARBINARY(60), @passwordHash)
    )
        THROW 50407, 'A deterministic demo email exists with conflicting casing, identity, role, status, or password hash. No account was changed.', 1;

    INSERT dbo.users (name, email, date_of_birth, gender, phone, password_hash, role, status, created_at)
    SELECT expected.full_name, expected.email, expected.birth_date, expected.gender, expected.phone,
           @passwordHash, expected.role, 'ACTIVE', SYSDATETIME()
    FROM @users AS expected
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.users AS existing_user WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_user.email = expected.email
    );

    DECLARE @sports TABLE (name VARCHAR(100) NOT NULL PRIMARY KEY, description VARCHAR(500) NOT NULL);
    INSERT @sports (name, description) VALUES
        ('Demo Badminton', 'Fictional demo catalog sport for racket-sport coaching.'),
        ('Demo Basketball', 'Fictional demo catalog sport for team-sport coaching.'),
        ('Demo Swimming', 'Fictional demo catalog sport for aquatic coaching.'),
        ('Demo Tennis', 'Fictional demo catalog sport for court-sport coaching.'),
        ('Demo Yoga', 'Fictional demo catalog sport for flexibility and movement classes.');

    IF EXISTS (
        SELECT 1 FROM @sports AS expected
        JOIN dbo.sport AS existing_sport WITH (UPDLOCK, HOLDLOCK) ON existing_sport.name = expected.name
        WHERE existing_sport.status <> 'ACTIVE'
    )
        THROW 50408, 'A deterministic demo sport exists but is not ACTIVE. Review it manually; no rows were changed.', 1;
    INSERT dbo.sport (name, description, status)
    SELECT expected.name, expected.description, 'ACTIVE'
    FROM @sports AS expected
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.sport AS existing_sport WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_sport.name = expected.name
    );

    -- Only demo COACH accounts receive Coach rows. Existing inactive/mismatched
    -- Coach rows fail instead of being changed.
    IF EXISTS (
        SELECT 1 FROM @users AS expected
        JOIN dbo.users AS account ON account.email = expected.email AND expected.role = 'COACH'
        JOIN dbo.coach AS existing_coach WITH (UPDLOCK, HOLDLOCK) ON existing_coach.user_id = account.id
        WHERE existing_coach.status <> 'ACTIVE'
    )
        THROW 50409, 'A demo COACH account has a non-ACTIVE Coach record. Review the relationship manually.', 1;
    INSERT dbo.coach (user_id, status)
    SELECT account.id, 'ACTIVE'
    FROM @users AS expected
    JOIN dbo.users AS account ON account.email = expected.email AND expected.role = 'COACH'
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.coach AS existing_coach WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_coach.user_id = account.id
    );

    DECLARE @specializations TABLE (
        coach_email VARCHAR(150) NOT NULL,
        sport_name VARCHAR(100) NOT NULL,
        experience_years INT NOT NULL,
        certification VARCHAR(255) NULL,
        PRIMARY KEY (coach_email, sport_name)
    );
    INSERT @specializations VALUES
        ('demo.coach1@sportcenter.example.test', 'Demo Badminton', 8, 'Fictional Demo Racket Coaching Certificate'),
        ('demo.coach1@sportcenter.example.test', 'Demo Tennis', 5, 'Fictional Demo Court Coaching Certificate'),
        ('demo.coach1@sportcenter.example.test', 'Demo Swimming', 3, NULL),
        ('demo.coach2@sportcenter.example.test', 'Demo Basketball', 9, 'Fictional Demo Team Coaching Certificate'),
        ('demo.coach2@sportcenter.example.test', 'Demo Yoga', 4, NULL),
        ('demo.coach3@sportcenter.example.test', 'Demo Swimming', 7, 'Fictional Demo Aquatics Coaching Certificate'),
        ('demo.coach3@sportcenter.example.test', 'Demo Badminton', 2, NULL);

    INSERT dbo.coach_sport (coach_id, sport_id, experience_years, certification)
    SELECT coach_record.id, sport_record.id, expected.experience_years, expected.certification
    FROM @specializations AS expected
    JOIN dbo.users AS coach_user ON coach_user.email = expected.coach_email AND coach_user.role = 'COACH'
    JOIN dbo.coach AS coach_record ON coach_record.user_id = coach_user.id AND coach_record.status = 'ACTIVE'
    JOIN dbo.sport AS sport_record ON sport_record.name = expected.sport_name AND sport_record.status = 'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.coach_sport AS existing_assignment WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_assignment.coach_id = coach_record.id AND existing_assignment.sport_id = sport_record.id
    );

    -- Plans use dedicated names. Existing matching plans are reused unchanged;
    -- conflicting price/duration/discount/access/status values abort the seed.
    DECLARE @plans TABLE (
        name VARCHAR(100) NOT NULL PRIMARY KEY,
        description VARCHAR(500) NOT NULL,
        price DECIMAL(12,2) NOT NULL,
        duration_months INT NOT NULL,
        course_discount DECIMAL(5,2) NOT NULL,
        personal_discount DECIMAL(5,2) NOT NULL,
        allow_personal BIT NOT NULL,
        allow_one_on_one BIT NOT NULL
    );
    INSERT @plans VALUES
        ('DEMO - Monthly Access', 'Fictional one-month demo membership plan.', 29.99, 1, 0.00, 0.00, 0, 0),
        ('DEMO - Quarterly Active', 'Fictional three-month demo membership plan.', 79.99, 3, 5.00, 5.00, 1, 0),
        ('DEMO - Annual Complete', 'Fictional twelve-month demo membership plan.', 249.99, 12, 15.00, 20.00, 1, 1);

    IF EXISTS (
        SELECT expected.name
        FROM @plans AS expected
        JOIN dbo.membership_plan AS existing_plan ON existing_plan.name = expected.name
        GROUP BY expected.name
        HAVING COUNT_BIG(*) > 1
    )
        THROW 50412, 'Duplicate rows exist for a deterministic demo plan name. Resolve the duplicate manually.', 1;

    IF EXISTS (
        SELECT 1 FROM @plans AS expected
        JOIN dbo.membership_plan AS existing_plan WITH (UPDLOCK, HOLDLOCK) ON existing_plan.name = expected.name
        WHERE existing_plan.price <> expected.price
           OR existing_plan.duration_months <> expected.duration_months
           OR existing_plan.course_discount_percent <> expected.course_discount
           OR existing_plan.personal_training_discount_percent <> expected.personal_discount
           OR existing_plan.allow_personal_training_plan <> expected.allow_personal
           OR existing_plan.allow_one_on_one_coaching <> expected.allow_one_on_one
           OR existing_plan.status <> 'ACTIVE'
    )
        THROW 50410, 'A deterministic demo membership plan exists with conflicting business values. Review it manually.', 1;
    INSERT dbo.membership_plan (name, description, price, duration_months, course_discount_percent,
        personal_training_discount_percent, allow_personal_training_plan, allow_one_on_one_coaching, status)
    SELECT expected.name, expected.description, expected.price, expected.duration_months,
           expected.course_discount, expected.personal_discount, expected.allow_personal,
           expected.allow_one_on_one, 'ACTIVE'
    FROM @plans AS expected
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.membership_plan AS existing_plan WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_plan.name = expected.name
    );

    DECLARE @profiles TABLE (
        member_email VARCHAR(150) NOT NULL PRIMARY KEY,
        height DECIMAL(5,2) NOT NULL,
        weight DECIMAL(5,2) NOT NULL,
        goal VARCHAR(500) NOT NULL
    );
    INSERT @profiles VALUES
        ('demo.member01@sportcenter.example.test', 162.00, 58.00, 'Build general strength and improve badminton footwork.'),
        ('demo.member02@sportcenter.example.test', 178.00, 76.00, 'Improve cardiovascular fitness and basketball conditioning.'),
        ('demo.member03@sportcenter.example.test', 170.00, 64.00, 'Develop a consistent swimming routine.'),
        ('demo.member04@sportcenter.example.test', 165.00, 61.00, 'Improve flexibility and maintain an active routine.'),
        ('demo.member05@sportcenter.example.test', 181.00, 82.00, 'Build endurance and improve tennis movement.'),
        ('demo.member06@sportcenter.example.test', 168.00, 59.00, 'Increase strength and practice mindful movement.'),
        ('demo.member07@sportcenter.example.test', 176.00, 73.00, 'Improve swimming technique and stamina.'),
        ('demo.member08@sportcenter.example.test', 173.00, 69.00, 'Build coordination through recreational team sports.'),
        ('demo.member09@sportcenter.example.test', 160.00, 54.00, 'Develop strength, balance, and mobility.'),
        ('demo.member10@sportcenter.example.test', 184.00, 86.00, 'Maintain fitness and improve court-sport agility.');

    INSERT dbo.member_profile (member_id, height, weight, goal, updated_at)
    SELECT member_user.id, expected.height, expected.weight, expected.goal, SYSDATETIME()
    FROM @profiles AS expected
    JOIN dbo.users AS member_user
      ON member_user.email = expected.member_email AND member_user.role = 'MEMBER' AND member_user.status = 'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.member_profile AS existing_profile WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_profile.member_id = member_user.id
    );

    -- Eight members each receive one active demo membership. Reuse an existing
    -- matching active membership on rerun; conflicting/expired matching rows or
    -- another active plan fail rather than being altered or duplicated.
    DECLARE @memberPlans TABLE (member_email VARCHAR(150) NOT NULL PRIMARY KEY, plan_name VARCHAR(100) NOT NULL);
    INSERT @memberPlans VALUES
        ('demo.member01@sportcenter.example.test', 'DEMO - Monthly Access'),
        ('demo.member02@sportcenter.example.test', 'DEMO - Quarterly Active'),
        ('demo.member03@sportcenter.example.test', 'DEMO - Annual Complete'),
        ('demo.member04@sportcenter.example.test', 'DEMO - Monthly Access'),
        ('demo.member05@sportcenter.example.test', 'DEMO - Quarterly Active'),
        ('demo.member06@sportcenter.example.test', 'DEMO - Annual Complete'),
        ('demo.member07@sportcenter.example.test', 'DEMO - Monthly Access'),
        ('demo.member08@sportcenter.example.test', 'DEMO - Quarterly Active');

    DECLARE @today DATE = CONVERT(date, SYSDATETIME());
    IF EXISTS (
        SELECT 1
        FROM @memberPlans AS expected
        JOIN dbo.users AS member_user ON member_user.email = expected.member_email
        JOIN dbo.membership_plan AS membership_plan ON membership_plan.name = expected.plan_name
        JOIN dbo.membership AS existing_membership WITH (UPDLOCK, HOLDLOCK)
          ON existing_membership.member_id = member_user.id
        WHERE (existing_membership.plan_id = membership_plan.id
               AND (existing_membership.status <> 'ACTIVE'
                    OR existing_membership.auto_renew <> 0
                    OR existing_membership.start_date > @today
                    OR existing_membership.end_date <= @today))
           OR (existing_membership.status = 'ACTIVE' AND existing_membership.end_date > @today
               AND existing_membership.plan_id <> membership_plan.id)
    )
        THROW 50411, 'A demo member has an expired/conflicting seed membership or another active membership. Review manually.', 1;

    IF EXISTS (
        SELECT member_user.id, membership_plan.id
        FROM @memberPlans AS expected
        JOIN dbo.users AS member_user ON member_user.email = expected.member_email
        JOIN dbo.membership_plan AS membership_plan ON membership_plan.name = expected.plan_name
        JOIN dbo.membership AS existing_membership
          ON existing_membership.member_id = member_user.id AND existing_membership.plan_id = membership_plan.id
        GROUP BY member_user.id, membership_plan.id
        HAVING COUNT_BIG(*) > 1
    )
        THROW 50413, 'Duplicate memberships exist for a seeded member and plan. Resolve them manually.', 1;

    INSERT dbo.membership (member_id, plan_id, auto_renew, start_date, end_date, status)
    SELECT member_user.id, membership_plan.id, 0, @today,
           DATEADD(month, membership_plan.duration_months, @today), 'ACTIVE'
    FROM @memberPlans AS expected
    JOIN dbo.users AS member_user
      ON member_user.email = expected.member_email AND member_user.role = 'MEMBER' AND member_user.status = 'ACTIVE'
    JOIN dbo.membership_plan AS membership_plan
      ON membership_plan.name = expected.plan_name AND membership_plan.status = 'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.membership AS existing_membership WITH (UPDLOCK, HOLDLOCK)
        WHERE existing_membership.member_id = member_user.id AND existing_membership.plan_id = membership_plan.id
    )
      AND NOT EXISTS (
        SELECT 1 FROM dbo.membership AS active_membership WITH (UPDLOCK, HOLDLOCK)
        WHERE active_membership.member_id = member_user.id
          AND active_membership.status = 'ACTIVE' AND active_membership.end_date > @today
    );

    COMMIT TRANSACTION;

    -- Summary verification; no password hashes or sensitive values are returned.
    SELECT role, COUNT_BIG(*) AS seeded_account_count
    FROM dbo.users
    WHERE email IN (SELECT email FROM @users)
    GROUP BY role
    ORDER BY role;
    SELECT N'demo.sports' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.sport WHERE name IN (SELECT name FROM @sports);
    SELECT N'demo.coach_sport' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.coach_sport AS assignment
    JOIN dbo.sport AS sport_record ON sport_record.id = assignment.sport_id
    JOIN @sports AS expected_sport ON expected_sport.name = sport_record.name
    JOIN dbo.coach AS coach_record ON coach_record.id = assignment.coach_id
    JOIN dbo.users AS coach_user ON coach_user.id = coach_record.user_id
    JOIN @specializations AS expected_assignment
      ON expected_assignment.sport_name = sport_record.name
     AND expected_assignment.coach_email = coach_user.email;
    SELECT N'demo.coach' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.coach AS coach_record
    JOIN dbo.users AS coach_user ON coach_user.id = coach_record.user_id
    WHERE coach_user.email IN (SELECT email FROM @users WHERE role = 'COACH');
    SELECT N'demo.member_profile' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.member_profile AS profile
    JOIN dbo.users AS member_user ON member_user.id = profile.member_id
    WHERE member_user.email IN (SELECT email FROM @users WHERE role = 'MEMBER');
    SELECT N'demo.membership_plan' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.membership_plan WHERE name IN (SELECT name FROM @plans);
    SELECT N'demo.membership' AS seed_group, COUNT_BIG(*) AS record_count
    FROM dbo.membership AS membership
    JOIN dbo.users AS member_user ON member_user.id = membership.member_id
    JOIN dbo.membership_plan AS membership_plan ON membership_plan.id = membership.plan_id
    WHERE member_user.email IN (SELECT member_email FROM @memberPlans)
      AND membership_plan.name IN (SELECT name FROM @plans);
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
