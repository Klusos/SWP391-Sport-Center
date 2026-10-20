/*
  SportCenter local E2E fixture cleanup preparation.

  Scope: one verified harness run only (e36a959e07084b56af70f7d3ad9356b3).
  Default behavior is DRY RUN: this script performs scoped deletes inside a
  transaction, verifies expected row counts and preserved records, then rolls
  the transaction back. A prior dry-run attempt failed and rolled back; no
  persistent cleanup has been performed.

  A future approved commit requires deliberate edits to BOTH @CommitCleanup
  and @CommitConfirmation. Before that, create and verify a current backup
  under separate authorization and re-run the preflight inventory.

  A fresh COPY_ONLY backup was verified before the 2026-10-20 dry-run retry:
  SportCenterDB-Phase89G-20261020-105041.bak. A verified backup does not
  authorize a committed cleanup.
*/
USE [SportCenterDB];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

IF SESSIONPROPERTY('ANSI_NULLS') <> 1
    OR SESSIONPROPERTY('ANSI_PADDING') <> 1
    OR SESSIONPROPERTY('ANSI_WARNINGS') <> 1
    OR SESSIONPROPERTY('ARITHABORT') <> 1
    OR SESSIONPROPERTY('CONCAT_NULL_YIELDS_NULL') <> 1
    OR SESSIONPROPERTY('QUOTED_IDENTIFIER') <> 1
    OR SESSIONPROPERTY('NUMERIC_ROUNDABORT') <> 0
    THROW 51057, 'Cleanup stopped: required indexed-structure SET options are not active.', 1;
PRINT 'Required indexed-structure SET options verified in the cleanup session.';

DECLARE @ExpectedServer sysname = N'DESKTOP-EMK77PD';
DECLARE @ExpectedDatabase sysname = N'SportCenterDB';
DECLARE @RunMarker nvarchar(40) = N'E2E-e36a959e07084b56af70f7d3ad9356b3';
DECLARE @CommitCleanup bit = 0;
DECLARE @CommitConfirmation nvarchar(100) = N'';
DECLARE @RequiredConfirmation nvarchar(100) =
    N'DELETE E2E-e36a959e07084b56af70f7d3ad9356b3';

BEGIN TRY
    IF CONVERT(sysname, @@SERVERNAME) <> @ExpectedServer
        THROW 51000, 'Cleanup stopped: @@SERVERNAME is not the approved server.', 1;
    IF CONVERT(sysname, DB_NAME()) <> @ExpectedDatabase
        THROW 51001, 'Cleanup stopped: current database is not SportCenterDB.', 1;

    IF @CommitCleanup = 1 AND @CommitConfirmation <> @RequiredConfirmation
        THROW 51002, 'Cleanup stopped: explicit commit confirmation does not match this run marker.', 1;

    BEGIN TRANSACTION;

    DECLARE @Users TABLE (
        id int PRIMARY KEY,
        name nvarchar(200) NOT NULL,
        email varchar(150) NOT NULL,
        role varchar(30) NOT NULL,
        status varchar(30) NOT NULL
    );
    INSERT INTO @Users VALUES
        (24, N'E2E-e36a959e07084b56af70f7d3ad9356b3 member', 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.member-a@example.invalid', 'MEMBER', 'ACTIVE'),
        (25, N'E2E-e36a959e07084b56af70f7d3ad9356b3 member', 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.member-b@example.invalid', 'MEMBER', 'ACTIVE'),
        (26, N'E2E-e36a959e07084b56af70f7d3ad9356b3 member', 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.price-member@example.invalid', 'MEMBER', 'ACTIVE'),
        (27, N'E2E-e36a959e07084b56af70f7d3ad9356b3 coach', 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.coach-a@example.invalid', 'COACH', 'ACTIVE'),
        (28, N'E2E-e36a959e07084b56af70f7d3ad9356b3 coach', 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.coach-b@example.invalid', 'COACH', 'ACTIVE');

    DECLARE @Sports TABLE (id int PRIMARY KEY, name nvarchar(200) NOT NULL);
    INSERT INTO @Sports VALUES (7, N'Sport E2E-e36a959e07084b56af70f7d3ad9356b3');

    DECLARE @Courses TABLE (
        id int PRIMARY KEY, sport_id int NOT NULL, name nvarchar(510) NOT NULL, status varchar(50) NOT NULL
    );
    INSERT INTO @Courses VALUES
        (2, 7, N'E2E-e36a959e07084b56af70f7d3ad9356b3 boundary-course', 'ACTIVE'),
        (3, 7, N'E2E-e36a959e07084b56af70f7d3ad9356b3 closed-window-course', 'ACTIVE'),
        (4, 7, N'E2E-e36a959e07084b56af70f7d3ad9356b3 schedule-course', 'ACTIVE'),
        (5, 7, N'E2E-e36a959e07084b56af70f7d3ad9356b3 price-course', 'ACTIVE');

    DECLARE @Offerings TABLE (id int PRIMARY KEY, course_id int NOT NULL, name nvarchar(300) NOT NULL);
    INSERT INTO @Offerings VALUES
        (4, 2, N'E2E-e36a959e07084b56af70f7d3ad9356b3 boundary-offering'),
        (5, 3, N'E2E-e36a959e07084b56af70f7d3ad9356b3 closed-window-offering'),
        (6, 4, N'E2E-e36a959e07084b56af70f7d3ad9356b3 schedule-offering'),
        (7, 5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 price-offering');

    DECLARE @Coaches TABLE (id int PRIMARY KEY, user_id int NOT NULL);
    INSERT INTO @Coaches VALUES (5, 27), (6, 28);

    DECLARE @Classes TABLE (
        id int PRIMARY KEY, offering_id int NOT NULL, coach_id int NOT NULL,
        name nvarchar(200) NOT NULL, capacity int NOT NULL, status varchar(30) NOT NULL
    );
    INSERT INTO @Classes VALUES
        (3, 4, 5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 capacity-class', 1, 'ACTIVE'),
        (4, 5, 5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 closed-window-class', 2, 'ACTIVE'),
        (5, 4, 6, N'E2E-e36a959e07084b56af70f7d3ad9356b3 same-offering-sibling', 3, 'ACTIVE'),
        (6, 6, 6, N'E2E-e36a959e07084b56af70f7d3ad9356b3 overlap-class', 5, 'ACTIVE'),
        (7, 6, 5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 adjacent-class', 5, 'ACTIVE'),
        (8, 7, 5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 price-class', 4, 'ACTIVE');

    DECLARE @Sessions TABLE (id int PRIMARY KEY, class_id int NOT NULL, status varchar(30) NOT NULL);
    INSERT INTO @Sessions VALUES (5, 3, 'SCHEDULED'), (6, 6, 'SCHEDULED'), (7, 7, 'SCHEDULED'), (8, 8, 'SCHEDULED');

    DECLARE @Registrations TABLE (
        id int PRIMARY KEY, member_id int NOT NULL, class_id int NOT NULL,
        status varchar(30) NOT NULL, final_price decimal(12,2) NOT NULL
    );
    INSERT INTO @Registrations VALUES
        (2, 24, 3, 'CONFIRMED', 100.00),
        (3, 24, 7, 'CONFIRMED', 150.00),
        (4, 26, 8, 'PENDING_PAYMENT', 744.99);

    DECLARE @Payments TABLE (
        id int PRIMARY KEY, member_id int NOT NULL, processed_by int NOT NULL,
        method varchar(20) NOT NULL, purpose varchar(30) NOT NULL,
        reference_id int NOT NULL, status varchar(20) NOT NULL, amount decimal(12,2) NOT NULL
    );
    INSERT INTO @Payments VALUES
        (4, 24, 23, 'COUNTER', 'COURSE_REGISTRATION', 2, 'SUCCESS', 100.00),
        (5, 24, 23, 'COUNTER', 'COURSE_REGISTRATION', 3, 'SUCCESS', 150.00);

    DECLARE @MembershipPlans TABLE (id int PRIMARY KEY, name nvarchar(200) NOT NULL);
    INSERT INTO @MembershipPlans VALUES (5, N'E2E-e36a959e07084b56af70f7d3ad9356b3 discount plan');
    DECLARE @Memberships TABLE (id int PRIMARY KEY, member_id int NOT NULL, plan_id int NOT NULL, status varchar(30) NOT NULL);
    INSERT INTO @Memberships VALUES (10, 26, 5, 'ACTIVE');

    DECLARE @CoachSports TABLE (coach_id int NOT NULL, sport_id int NOT NULL, PRIMARY KEY (coach_id, sport_id));
    INSERT INTO @CoachSports VALUES (5, 7), (6, 7);

    DECLARE @Notifications TABLE (id int PRIMARY KEY, user_id int NOT NULL, title varchar(150) NOT NULL, message nvarchar(max) NOT NULL);
    INSERT INTO @Notifications VALUES
        (23, 27, 'Class assignment', N'You have been assigned to class #3.'),
        (24, 27, 'Class assignment', N'You have been assigned to class #4.'),
        (25, 28, 'Class assignment', N'You have been assigned to class #5.'),
        (26, 28, 'Class assignment', N'You have been assigned to class #6.'),
        (27, 27, 'Class assignment', N'You have been assigned to class #7.'),
        (28, 27, 'Class assignment', N'You have been assigned to class #8.');

    DECLARE @ExpectedForeignKeys TABLE (
        child_table sysname NOT NULL, child_column sysname NOT NULL,
        parent_table sysname NOT NULL, parent_column sysname NOT NULL,
        PRIMARY KEY (child_table, child_column)
    );
    INSERT INTO @ExpectedForeignKeys VALUES
        ('course', 'sport_id', 'sport', 'id'),
        ('course_offering', 'course_id', 'course', 'id'),
        ('class', 'course_offering_id', 'course_offering', 'id'),
        ('class', 'coach_id', 'coach', 'id'),
        ('class_session', 'class_id', 'class', 'id'),
        ('course_registration', 'member_id', 'users', 'id'),
        ('course_registration', 'class_id', 'class', 'id'),
        ('payment', 'member_id', 'users', 'id'),
        ('payment', 'processed_by', 'users', 'id'),
        ('membership', 'member_id', 'users', 'id'),
        ('membership', 'plan_id', 'membership_plan', 'id'),
        ('coach', 'user_id', 'users', 'id'),
        ('coach_sport', 'coach_id', 'coach', 'id'),
        ('coach_sport', 'sport_id', 'sport', 'id'),
        ('notification', 'user_id', 'users', 'id'),
        ('facility', 'sport_id', 'sport', 'id'),
        ('facility_sport', 'facility_id', 'facility', 'id'),
        ('facility_sport', 'sport_id', 'sport', 'id'),
        ('attendance', 'class_session_id', 'class_session', 'id'),
        ('attendance_history', 'attendance_id', 'attendance', 'id');

    IF EXISTS (
        SELECT 1
        FROM @ExpectedForeignKeys e
        LEFT JOIN sys.tables ct ON ct.name = e.child_table AND SCHEMA_NAME(ct.schema_id) = N'dbo'
        LEFT JOIN sys.columns cc ON cc.object_id = ct.object_id AND cc.name = e.child_column
        LEFT JOIN sys.tables pt ON pt.name = e.parent_table AND SCHEMA_NAME(pt.schema_id) = N'dbo'
        LEFT JOIN sys.columns pc ON pc.object_id = pt.object_id AND pc.name = e.parent_column
        LEFT JOIN sys.foreign_key_columns fkc
            ON fkc.parent_object_id = ct.object_id AND fkc.parent_column_id = cc.column_id
            AND fkc.referenced_object_id = pt.object_id AND fkc.referenced_column_id = pc.column_id
        LEFT JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
        WHERE fk.object_id IS NULL OR fk.is_disabled = 1 OR fk.is_not_trusted = 1
            OR fk.delete_referential_action_desc <> N'NO_ACTION'
    )
        THROW 51003, 'Cleanup stopped: a required FK is missing, untrusted, disabled, or cascading.', 1;

    /* Exact ownership and state checks: IDs alone never authorize deletion. */
    IF EXISTS (SELECT id, name FROM @Sports EXCEPT SELECT id, name FROM dbo.sport WHERE id = 7)
        OR EXISTS (SELECT id, name FROM dbo.sport WHERE id = 7 EXCEPT SELECT id, name FROM @Sports)
        OR (SELECT COUNT(*) FROM dbo.sport WHERE name LIKE N'Sport ' + @RunMarker + N'%') <> 1
        THROW 51004, 'Cleanup stopped: Sport 7 does not exactly match the target run marker.', 1;

    IF EXISTS (SELECT id, sport_id, name, status FROM @Courses EXCEPT SELECT id, sport_id, name, status FROM dbo.course WHERE id IN (2,3,4,5))
        OR EXISTS (SELECT id, sport_id, name, status FROM dbo.course WHERE id IN (2,3,4,5) EXCEPT SELECT id, sport_id, name, status FROM @Courses)
        OR (SELECT COUNT(*) FROM dbo.course WHERE name LIKE @RunMarker + N' %') <> 4
        THROW 51005, 'Cleanup stopped: course IDs, names, Sport associations, or count changed.', 1;

    IF EXISTS (SELECT id, course_id, name FROM @Offerings EXCEPT SELECT id, course_id, name FROM dbo.course_offering WHERE id IN (4,5,6,7))
        OR EXISTS (SELECT id, course_id, name FROM dbo.course_offering WHERE id IN (4,5,6,7) EXCEPT SELECT id, course_id, name FROM @Offerings)
        OR (SELECT COUNT(*) FROM dbo.course_offering WHERE name LIKE @RunMarker + N' %') <> 4
        THROW 51006, 'Cleanup stopped: offering IDs, parents, names, or count changed.', 1;

    IF EXISTS (SELECT id, offering_id, coach_id, name, capacity, status FROM @Classes EXCEPT
               SELECT id, course_offering_id, coach_id, name, capacity, status FROM dbo.[class] WHERE id IN (3,4,5,6,7,8))
        OR EXISTS (SELECT id, course_offering_id, coach_id, name, capacity, status FROM dbo.[class] WHERE id IN (3,4,5,6,7,8) EXCEPT
                   SELECT id, offering_id, coach_id, name, capacity, status FROM @Classes)
        OR (SELECT COUNT(*) FROM dbo.[class] WHERE name LIKE @RunMarker + N' %') <> 6
        THROW 51007, 'Cleanup stopped: class IDs, relationships, names, capacities, or count changed.', 1;

    IF EXISTS (SELECT id, class_id, status FROM @Sessions EXCEPT SELECT id, class_id, status FROM dbo.class_session WHERE id IN (5,6,7,8))
        OR EXISTS (SELECT id, class_id, status FROM dbo.class_session WHERE id IN (5,6,7,8) EXCEPT SELECT id, class_id, status FROM @Sessions)
        OR (SELECT COUNT(*) FROM dbo.class_session ss JOIN dbo.[class] cl ON cl.id = ss.class_id WHERE cl.name LIKE @RunMarker + N' %') <> 4
        THROW 51008, 'Cleanup stopped: session IDs, classes, statuses, or count changed.', 1;

    IF EXISTS (SELECT id, name FROM @MembershipPlans EXCEPT SELECT id, name FROM dbo.membership_plan WHERE id = 5)
        OR EXISTS (SELECT id, name FROM dbo.membership_plan WHERE id = 5 EXCEPT SELECT id, name FROM @MembershipPlans)
        OR (SELECT COUNT(*) FROM dbo.membership_plan WHERE name LIKE @RunMarker + N' %') <> 1
        THROW 51009, 'Cleanup stopped: membership plan 5 does not match the target run marker.', 1;

    IF EXISTS (SELECT id, name, email, role, status FROM @Users EXCEPT SELECT id, name, email, role, status FROM dbo.users WHERE id IN (24,25,26,27,28))
        OR EXISTS (SELECT id, name, email, role, status FROM dbo.users WHERE id IN (24,25,26,27,28) EXCEPT SELECT id, name, email, role, status FROM @Users)
        OR (SELECT COUNT(*) FROM dbo.users WHERE name LIKE @RunMarker + N' %') <> 6
        OR (SELECT COUNT(*) FROM dbo.users WHERE LOWER(email) LIKE LOWER(N'%'+@RunMarker+N'%')) <> 6
        THROW 51010, 'Cleanup stopped: candidate user IDs, identities, roles, statuses, or run count changed.', 1;

    IF EXISTS (
        SELECT id, user_id FROM @Coaches EXCEPT SELECT id, user_id FROM dbo.coach WHERE id IN (5,6)
    ) OR EXISTS (
        SELECT id, user_id FROM dbo.coach WHERE id IN (5,6) EXCEPT SELECT id, user_id FROM @Coaches
    ) OR (SELECT COUNT(*) FROM dbo.coach c JOIN dbo.users u ON u.id = c.user_id WHERE u.name LIKE @RunMarker + N' %' AND u.role = 'COACH') <> 2
        THROW 51011, 'Cleanup stopped: coach IDs or user relationships changed.', 1;

    IF EXISTS (SELECT coach_id, sport_id FROM @CoachSports EXCEPT SELECT coach_id, sport_id FROM dbo.coach_sport WHERE coach_id IN (5,6) OR sport_id = 7)
        OR EXISTS (SELECT coach_id, sport_id FROM dbo.coach_sport WHERE coach_id IN (5,6) OR sport_id = 7 EXCEPT SELECT coach_id, sport_id FROM @CoachSports)
        THROW 51012, 'Cleanup stopped: unexpected coach-sport association exists.', 1;

    IF EXISTS (SELECT id, member_id, plan_id, status FROM @Memberships EXCEPT SELECT id, member_id, plan_id, status FROM dbo.membership WHERE id = 10)
        OR EXISTS (SELECT id, member_id, plan_id, status FROM dbo.membership WHERE id = 10 EXCEPT SELECT id, member_id, plan_id, status FROM @Memberships)
        OR (SELECT COUNT(*) FROM dbo.membership WHERE plan_id = 5 OR member_id IN (24,25,26)) <> 1
        THROW 51013, 'Cleanup stopped: membership 10 or its ownership/plan relationship changed.', 1;

    IF EXISTS (SELECT id, member_id, class_id, status, final_price FROM @Registrations EXCEPT
               SELECT id, member_id, class_id, status, final_price FROM dbo.course_registration WHERE id IN (2,3,4))
        OR EXISTS (SELECT id, member_id, class_id, status, final_price FROM dbo.course_registration WHERE id IN (2,3,4) EXCEPT
                   SELECT id, member_id, class_id, status, final_price FROM @Registrations)
        OR (SELECT COUNT(*) FROM dbo.course_registration WHERE class_id IN (3,4,5,6,7,8) OR member_id IN (24,25,26)) <> 3
        THROW 51014, 'Cleanup stopped: registration ownership, status, price, or dependency set changed.', 1;

    IF EXISTS (SELECT id, member_id, processed_by, method, purpose, reference_id, status, amount FROM @Payments EXCEPT
               SELECT id, member_id, processed_by, method, purpose, reference_id, status, amount FROM dbo.payment WHERE id IN (4,5))
        OR EXISTS (SELECT id, member_id, processed_by, method, purpose, reference_id, status, amount FROM dbo.payment WHERE id IN (4,5) EXCEPT
                   SELECT id, member_id, processed_by, method, purpose, reference_id, status, amount FROM @Payments)
        THROW 51015, 'Cleanup stopped: payment IDs or settlement facts changed.', 1;

    /* Payments have polymorphic references; verify both member ownership and every course registration reference. */
    IF (SELECT COUNT(*) FROM dbo.payment
        WHERE member_id IN (24,25,26,27,28) OR processed_by IN (24,25,26,27,28)
           OR (purpose = 'COURSE_REGISTRATION' AND reference_id IN (2,3,4))
           OR (purpose = 'MEMBERSHIP' AND reference_id = 5)) <> 2
        THROW 51016, 'Cleanup stopped: an unexpected payment depends on a candidate user, registration, or plan.', 1;
    IF EXISTS (
        SELECT 1 FROM dbo.payment p
        WHERE p.purpose = 'COURSE_REGISTRATION' AND p.reference_id IN (2,3,4)
          AND NOT EXISTS (SELECT 1 FROM @Payments e WHERE e.id = p.id AND e.reference_id = p.reference_id)
    )
        THROW 51017, 'Cleanup stopped: a course payment references a candidate registration unexpectedly.', 1;

    IF EXISTS (SELECT id, user_id, title, message FROM @Notifications EXCEPT SELECT id, user_id, title, message FROM dbo.notification WHERE id IN (23,24,25,26,27,28))
        OR EXISTS (SELECT id, user_id, title, message FROM dbo.notification WHERE id IN (23,24,25,26,27,28) EXCEPT SELECT id, user_id, title, message FROM @Notifications)
        OR (SELECT COUNT(*) FROM dbo.notification WHERE user_id IN (24,25,26,27,28)) <> 6
        THROW 51018, 'Cleanup stopped: class-assignment notifications or candidate-user notification set changed.', 1;
    IF EXISTS (
        SELECT 1 FROM dbo.notification n
        WHERE n.title = 'Class assignment'
          AND (n.message LIKE N'%class #3.%' OR n.message LIKE N'%class #4.%'
            OR n.message LIKE N'%class #5.%' OR n.message LIKE N'%class #6.%'
            OR n.message LIKE N'%class #7.%' OR n.message LIKE N'%class #8.%')
          AND n.id NOT IN (23,24,25,26,27,28)
    )
        THROW 51055, 'Cleanup stopped: another notification references a candidate class.', 1;

    /* Broader child-table and polymorphic-reference guards; expected counts are zero. */
    IF (SELECT COUNT(*) FROM dbo.membership WHERE member_id IN (24,25,26,27,28) OR plan_id = 5) <> 1
        THROW 51019, 'Cleanup stopped: unexpected membership dependency exists.', 1;
    IF (SELECT COUNT(*) FROM dbo.member_profile WHERE member_id IN (24,25,26,27,28)) <> 0
        THROW 51020, 'Cleanup stopped: candidate users have member profiles not in the reviewed scope.', 1;
    IF (SELECT COUNT(*) FROM dbo.[class] WHERE coach_id IN (5,6)) <> 6
        THROW 51021, 'Cleanup stopped: candidate coaches are assigned to unexpected classes.', 1;
    IF (SELECT COUNT(*) FROM dbo.course WHERE sport_id = 7) <> 4
        THROW 51022, 'Cleanup stopped: Sport 7 has unexpected course dependencies.', 1;
    IF (SELECT COUNT(*) FROM dbo.course_offering WHERE course_id IN (2,3,4,5)) <> 4
        THROW 51023, 'Cleanup stopped: candidate courses have unexpected offering dependencies.', 1;
    IF (SELECT COUNT(*) FROM dbo.[class] WHERE course_offering_id IN (4,5,6,7)) <> 6
        THROW 51024, 'Cleanup stopped: candidate offerings have unexpected class dependencies.', 1;
    IF (SELECT COUNT(*) FROM dbo.class_session WHERE class_id IN (3,4,5,6,7,8)) <> 4
        THROW 51025, 'Cleanup stopped: candidate classes have unexpected session dependencies.', 1;
    IF EXISTS (SELECT 1 FROM dbo.facility_sport WHERE sport_id = 7)
        OR EXISTS (SELECT 1 FROM dbo.facility WHERE sport_id = 7)
        THROW 51026, 'Cleanup stopped: Sport 7 is associated with a facility.', 1;
    IF EXISTS (SELECT 1 FROM dbo.attendance a
               WHERE a.member_id IN (24,25,26,27,28)
                  OR a.marked_by IN (24,25,26,27,28)
                  OR a.class_session_id IN (SELECT id FROM @Sessions))
        OR EXISTS (SELECT 1 FROM dbo.attendance_history ah
                   WHERE ah.changed_by IN (24,25,26,27,28)
                      OR ah.attendance_id IN (
                          SELECT a.id FROM dbo.attendance a
                          WHERE a.class_session_id IN (SELECT id FROM @Sessions)
                      ))
        THROW 51027, 'Cleanup stopped: attendance or attendance-history dependency exists.', 1;
    IF EXISTS (SELECT 1 FROM dbo.consultation_request
               WHERE member_id IN (24,25,26,27,28) OR handled_by IN (24,25,26,27,28) OR sport_id = 7)
        THROW 51028, 'Cleanup stopped: consultation dependency exists.', 1;
    IF EXISTS (SELECT 1 FROM dbo.personal_training_booking
               WHERE member_id IN (24,25,26,27,28) OR coach_id IN (5,6) OR sport_id = 7)
        THROW 51029, 'Cleanup stopped: PT booking dependency exists.', 1;
    IF EXISTS (SELECT 1 FROM dbo.member_sport_level
               WHERE member_id IN (24,25,26,27,28) OR verified_by_coach_id IN (5,6) OR sport_id = 7)
        THROW 51030, 'Cleanup stopped: member sport-level dependency exists.', 1;
    IF EXISTS (SELECT 1 FROM dbo.skill_assessment
               WHERE member_id IN (24,25,26,27,28) OR requested_by_user_id IN (24,25,26,27,28)
                  OR assigned_coach_id IN (5,6) OR sport_id = 7)
        THROW 51031, 'Cleanup stopped: skill-assessment dependency exists.', 1;

    /* AuditLog is polymorphic: compare entity_type AND entity_id. Preserve the known plan audit row. */
    IF EXISTS (SELECT 1 FROM dbo.audit_log WHERE actor_user_id IN (24,25,26,27,28))
        THROW 51032, 'Cleanup stopped: a candidate user is an audit actor.', 1;
    IF EXISTS (
        SELECT 1 FROM dbo.audit_log a
        WHERE (
             (a.entity_type = 'SPORT' AND a.entity_id = '7')
          OR (a.entity_type = 'COURSE' AND a.entity_id IN ('2','3','4','5'))
          OR (a.entity_type = 'COURSE_OFFERING' AND a.entity_id IN ('4','5','6','7'))
          OR (a.entity_type = 'CLASS' AND a.entity_id IN ('3','4','5','6','7','8'))
          OR (a.entity_type = 'CLASS_SESSION' AND a.entity_id IN ('5','6','7','8'))
          OR (a.entity_type = 'COACH' AND a.entity_id IN ('5','6'))
          OR (a.entity_type = 'COACH_SPORT' AND a.entity_id IN ('5','6'))
          OR (a.entity_type = 'MEMBERSHIP' AND a.entity_id = '10')
          OR (a.entity_type = 'COURSE_REGISTRATION' AND a.entity_id IN ('2','3','4'))
          OR (a.entity_type = 'PAYMENT' AND a.entity_id IN ('4','5'))
          OR (a.entity_type = 'USER' AND a.entity_id IN ('24','25','26','27','28'))
          OR (a.entity_type = 'MEMBERSHIP_PLAN' AND a.entity_id = '5' AND a.id <> 5)
        )
    )
        THROW 51033, 'Cleanup stopped: an unexpected audit-log reference to a candidate exists.', 1;
    IF (SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type = 'MEMBERSHIP_PLAN' AND entity_id = '5') <> 1
        THROW 51054, 'Cleanup stopped: the reviewed membership-plan audit relationship changed.', 1;

    /* Assert explicitly protected/shared records before deletion. */
    IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 21 AND name = N'Dedicated E2E Administrator' AND role = 'ADMIN' AND status = 'ACTIVE')
        OR NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 22 AND name = N'Dedicated SQL Server E2E Administrator' AND role = 'ADMIN' AND status = 'ACTIVE')
        OR NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 23 AND name = @RunMarker + N' RECEPTIONIST'
                       AND email = 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.receptionist@example.invalid'
                       AND role = 'RECEPTIONIST' AND status = 'ACTIVE')
        THROW 51034, 'Cleanup stopped: a protected user is missing or no longer matches the inventory.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.notification WHERE id = 32 AND user_id = 23 AND title = 'New consultation request' AND message = N'A new consultation request #4 is waiting for review.')
        THROW 51035, 'Cleanup stopped: protected notification 32 is missing or changed.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.consultation_request WHERE id = 4 AND member_id = 20 AND sport_id = 1 AND status = 'COMPLETED' AND handled_by = 1)
        THROW 51036, 'Cleanup stopped: protected consultation request 4 is missing or changed.', 1;
    IF (SELECT COUNT(*) FROM dbo.audit_log WHERE id IN (4,5,6,7)) <> 4
        THROW 51037, 'Cleanup stopped: one or more protected audit rows 4-7 are missing.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 4 AND actor_user_id = 22 AND entity_type = 'USER' AND entity_id = '23')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 5 AND actor_user_id = 22 AND entity_type = 'MEMBERSHIP_PLAN' AND entity_id = '5')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 6 AND action = 'TAKE' AND entity_type = 'CONSULTATION_REQUEST' AND entity_id = '4')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 7 AND action = 'COMPLETE' AND entity_type = 'CONSULTATION_REQUEST' AND entity_id = '4')
        THROW 51038, 'Cleanup stopped: protected audit rows do not match their typed references.', 1;

    /* Dependency-aware deletes. Every statement has an exact expected row count. */
    DECLARE @ActualDeleteCount int;

    DELETE FROM dbo.payment WHERE id IN (4,5);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.payment expected=2 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 2 THROW 51039, 'Cleanup stopped: expected to delete exactly two reviewed payments.', 1;

    DELETE FROM dbo.course_registration WHERE id IN (2,3,4);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.course_registration expected=3 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 3 THROW 51040, 'Cleanup stopped: expected to delete exactly three reviewed registrations.', 1;

    DELETE FROM dbo.class_session WHERE id IN (5,6,7,8);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.class_session expected=4 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 4 THROW 51041, 'Cleanup stopped: expected to delete exactly four reviewed sessions.', 1;

    DELETE FROM dbo.notification WHERE id IN (23,24,25,26,27,28);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.notification expected=6 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 6 THROW 51042, 'Cleanup stopped: expected to delete exactly six class-assignment notifications.', 1;

    DELETE FROM dbo.[class] WHERE id IN (3,4,5,6,7,8);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.class expected=6 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 6 THROW 51043, 'Cleanup stopped: expected to delete exactly six reviewed classes.', 1;

    DELETE FROM dbo.course_offering WHERE id IN (4,5,6,7);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.course_offering expected=4 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 4 THROW 51044, 'Cleanup stopped: expected to delete exactly four reviewed offerings.', 1;

    DELETE FROM dbo.course WHERE id IN (2,3,4,5);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.course expected=4 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 4 THROW 51045, 'Cleanup stopped: expected to delete exactly four reviewed courses.', 1;

    DELETE FROM dbo.membership WHERE id = 10;
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.membership expected=1 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 1 THROW 51046, 'Cleanup stopped: expected to delete exactly membership 10.', 1;

    DELETE FROM dbo.coach_sport WHERE (coach_id = 5 AND sport_id = 7) OR (coach_id = 6 AND sport_id = 7);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.coach_sport expected=2 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 2 THROW 51047, 'Cleanup stopped: expected to delete exactly two reviewed coach-sport links.', 1;

    DELETE FROM dbo.coach WHERE id IN (5,6);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.coach expected=2 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 2 THROW 51048, 'Cleanup stopped: expected to delete exactly coaches 5 and 6.', 1;

    DELETE FROM dbo.membership_plan WHERE id = 5;
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.membership_plan expected=1 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 1 THROW 51049, 'Cleanup stopped: expected to delete exactly membership plan 5.', 1;

    DELETE FROM dbo.users WHERE id IN (24,25,26,27,28);
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.users expected=5 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 5 THROW 51050, 'Cleanup stopped: expected to delete exactly five reviewed fixture users.', 1;

    DELETE FROM dbo.sport WHERE id = 7;
    SET @ActualDeleteCount = @@ROWCOUNT;
    PRINT 'DELETE dbo.sport expected=1 actual=' + CONVERT(varchar(10), @ActualDeleteCount);
    IF @ActualDeleteCount <> 1 THROW 51051, 'Cleanup stopped: expected to delete exactly Sport 7.', 1;

    /* Post-delete checks run before either COMMIT or the default dry-run ROLLBACK. */
    IF EXISTS (SELECT 1 FROM dbo.sport WHERE id = 7)
        OR EXISTS (SELECT 1 FROM dbo.course WHERE id IN (2,3,4,5))
        OR EXISTS (SELECT 1 FROM dbo.course_offering WHERE id IN (4,5,6,7))
        OR EXISTS (SELECT 1 FROM dbo.[class] WHERE id IN (3,4,5,6,7,8))
        OR EXISTS (SELECT 1 FROM dbo.class_session WHERE id IN (5,6,7,8))
        OR EXISTS (SELECT 1 FROM dbo.membership_plan WHERE id = 5)
        OR EXISTS (SELECT 1 FROM dbo.membership WHERE id = 10)
        OR EXISTS (SELECT 1 FROM dbo.course_registration WHERE id IN (2,3,4))
        OR EXISTS (SELECT 1 FROM dbo.payment WHERE id IN (4,5))
        OR EXISTS (SELECT 1 FROM dbo.coach WHERE id IN (5,6))
        OR EXISTS (SELECT 1 FROM dbo.coach_sport WHERE (coach_id = 5 AND sport_id = 7) OR (coach_id = 6 AND sport_id = 7))
        OR EXISTS (SELECT 1 FROM dbo.notification WHERE id IN (23,24,25,26,27,28))
        OR EXISTS (SELECT 1 FROM dbo.users WHERE id IN (24,25,26,27,28))
        THROW 51052, 'Cleanup stopped: one or more candidate rows remain after the scoped deletes.', 1;
    IF EXISTS (SELECT 1 FROM dbo.payment WHERE purpose = 'COURSE_REGISTRATION' AND reference_id IN (2,3,4))
        OR EXISTS (SELECT 1 FROM dbo.payment WHERE purpose = 'MEMBERSHIP' AND reference_id = 5)
        THROW 51056, 'Cleanup stopped: a polymorphic payment reference remains after the deletes.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 21 AND name = N'Dedicated E2E Administrator' AND role = 'ADMIN' AND status = 'ACTIVE')
        OR NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 22 AND name = N'Dedicated SQL Server E2E Administrator' AND role = 'ADMIN' AND status = 'ACTIVE')
        OR NOT EXISTS (SELECT 1 FROM dbo.users WHERE id = 23 AND name = @RunMarker + N' RECEPTIONIST'
                       AND email = 'e2e+e2e-e36a959e07084b56af70f7d3ad9356b3.receptionist@example.invalid'
                       AND role = 'RECEPTIONIST' AND status = 'ACTIVE')
        OR NOT EXISTS (SELECT 1 FROM dbo.notification WHERE id = 32 AND user_id = 23)
        OR NOT EXISTS (SELECT 1 FROM dbo.consultation_request WHERE id = 4 AND member_id = 20 AND sport_id = 1)
        OR (SELECT COUNT(*) FROM dbo.audit_log WHERE id IN (4,5,6,7)) <> 4
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 4 AND actor_user_id = 22 AND entity_type = 'USER' AND entity_id = '23')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 5 AND actor_user_id = 22 AND entity_type = 'MEMBERSHIP_PLAN' AND entity_id = '5')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 6 AND action = 'TAKE' AND entity_type = 'CONSULTATION_REQUEST' AND entity_id = '4')
        OR NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE id = 7 AND action = 'COMPLETE' AND entity_type = 'CONSULTATION_REQUEST' AND entity_id = '4')
        THROW 51053, 'Cleanup stopped: a protected record changed during cleanup.', 1;

    IF @CommitCleanup = 1
    BEGIN
        COMMIT TRANSACTION;
        PRINT 'COMMITTED: only the reviewed E2E marker group was deleted.';
    END
    ELSE
    BEGIN
        ROLLBACK TRANSACTION;
        PRINT 'DRY RUN ONLY: all scoped deletes and checks succeeded; transaction rolled back.';
        PRINT 'Expected delete counts: payment 2; course_registration 3; class_session 4; notification 6; class 6; course_offering 4; course 4; membership 1; coach_sport 2; coach 2; membership_plan 1; users 5; sport 1.';
    END;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
