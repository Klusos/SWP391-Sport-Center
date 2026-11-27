package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MarkAttendanceRequest;
import group5.example.sportCenter.dto.AttendanceSubmissionMemberRequest;
import group5.example.sportCenter.dto.AttendanceSubmissionRequest;
import group5.example.sportCenter.dto.RecordTrainingResultRequest;
import group5.example.sportCenter.entity.*;
import group5.example.sportCenter.repository.*;
import group5.example.sportCenter.security.JwtService;
import group5.example.sportCenter.service.AttendanceService;
import group5.example.sportCenter.service.TrainingService;

/** Synthetic-only SQL Server proof that the shared session lock serializes attendance/result writes. */
@SpringBootTest(properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}", "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false", "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false", "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock", "app.payment.mock-gateway.enabled=true", "debug=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
class AttendanceTrainingConsistencySqlServerIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CoachRepository coaches;
    @Autowired SportRepository sports;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceHistoryRepository histories;
    @Autowired TrainingResultRepository results;
    @Autowired AttendanceService attendanceService;
    @Autowired TrainingService trainingService;
    @Autowired JwtService jwtService;

    private final String marker = "ATRC-" + UUID.randomUUID().toString().substring(0, 10);
    private User member, coachUser, admin;
    private Coach coach;
    private Sport sport;
    private Course course;
    private CourseOffering offering;
    private CourseClass courseClass;
    private ClassSession session;
    private CourseRegistration registration;
    private Attendance record;
    private final Map<String, Integer> baselineCounts = new LinkedHashMap<>();

    @BeforeEach
    void createSyntheticFixture() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys.dm_exec_requests r "
                + "JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id "
                + "WHERE r.database_id=DB_ID() AND r.session_id<>@@SPID AND s.is_user_process=1 "
                + "AND UPPER(r.command) IN ('INSERT','UPDATE','DELETE','MERGE','ALTER','CREATE','DROP','TRUNCATE')",
                Integer.class), "Competing DB12 writer detected before fixture setup");
        for (String table : new String[] { "users", "coach", "sport", "course", "course_offering", "class",
                "class_session", "course_registration", "attendance", "attendance_history", "training_result", "audit_log" }) {
            baselineCounts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[" + table + "]", Integer.class));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, marker + "%"));
        member = user("MEMBER");
        coachUser = user("COACH");
        admin = user("ADMIN");
        coach = new Coach(); coach.setUser(coachUser); coach.setStatus("ACTIVE"); coach = coaches.saveAndFlush(coach);
        sport = new Sport(); sport.setName(marker + " sport"); sport.setStatus("ACTIVE"); sport = sports.saveAndFlush(sport);
        course = new Course(); course.setSport(sport); course.setName(marker + " course"); course.setLevel("BEGINNER");
        course.setAgePolicy(CourseAgePolicy.RECOMMENDED); course.setStatus("ACTIVE"); course = courses.saveAndFlush(course);
        LocalDate today = LocalDate.now(VN);
        LocalDateTime now = LocalDateTime.now(VN);
        offering = new CourseOffering(); offering.setCourse(course); offering.setName(marker + " offering");
        offering.setPrice(new BigDecimal("100.00")); offering.setSessionDuration(60); offering.setPlannedSessions(1);
        offering.setStartDate(today.minusDays(1)); offering.setEndDate(today.plusDays(3));
        offering.setRegistrationDeadline(now.minusDays(1)); offering.setCancellationDeadline(now.minusHours(1));
        offering.setStatus("OPEN"); offering = offerings.saveAndFlush(offering);
        courseClass = new CourseClass(); courseClass.setCourseOffering(offering); courseClass.setCoach(coach);
        courseClass.setName(marker + " class"); courseClass.setCapacity(10); courseClass.setStatus("ACTIVE");
        courseClass = classes.saveAndFlush(courseClass);
        session = new ClassSession(); session.setCourseClass(courseClass); session.setStartTime(now.minusHours(2));
        session.setEndTime(now.minusHours(1)); session.setStatus("COMPLETED"); session = sessions.saveAndFlush(session);
        registration = new CourseRegistration(); registration.setMember(member); registration.setCourseClass(courseClass);
        registration.setStatus(CourseRegistrationStatus.CONFIRMED); registration.setOriginalPrice(new BigDecimal("100.00"));
        registration.setRegisteredAt(now.minusHours(3));
        registration.setMembershipDiscount(BigDecimal.ZERO); registration.setFinalPrice(new BigDecimal("100.00"));
        registration = registrations.saveAndFlush(registration);
        attendanceService.submitInitial(session.getId(), new AttendanceSubmissionRequest(
                java.util.List.of(new AttendanceSubmissionMemberRequest(member.getId(), AttendanceStatus.PRESENT, null))),
                jwt(coachUser));
        record = attendance.findByClassSessionIdAndMemberId(session.getId(), member.getId()).orElseThrow();
    }

    @Test
    void concurrentResultCreationAndAttendanceCorrectionCommitOnlyConsistentState() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> correction = pool.submit(() -> {
                ready.countDown(); await(start);
                try {
                    attendanceService.mark(session.getId(), member.getId(),
                            new MarkAttendanceRequest(AttendanceStatus.ABSENT, null, "Concurrent QA correction"), jwt(coachUser));
                    return 200;
                } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            });
            Future<Integer> result = pool.submit(() -> {
                ready.countDown(); await(start);
                try {
                    trainingService.recordResult(session.getId(), member.getId(),
                            new RecordTrainingResultRequest(7, "Synthetic SQL Server race"), jwt(coachUser));
                    return 200;
                } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int correctionStatus = correction.get(20, TimeUnit.SECONDS);
            int resultStatus = result.get(20, TimeUnit.SECONDS);
            assertTrue((correctionStatus == 200 && resultStatus == 409)
                    || (correctionStatus == 409 && resultStatus == 200),
                    "One write must win under the shared ClassSession lock");
            Attendance persisted = attendance.findById(record.getId()).orElseThrow();
            boolean hasResult = results.existsByClassSessionIdAndMemberId(session.getId(), member.getId());
            assertEquals(persisted.getStatus() == AttendanceStatus.PRESENT, hasResult);

            if (hasResult) {
                int historyCount = histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size();
                int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='ATTENDANCE' AND entity_id=?",
                        Integer.class, record.getId().toString());
                ResponseStatusException rejected = assertThrows(ResponseStatusException.class, () ->
                        attendanceService.mark(session.getId(), member.getId(),
                                new MarkAttendanceRequest(AttendanceStatus.EXCUSED, null, "Admin test correction"), jwt(admin)));
                assertEquals(409, rejected.getStatusCode().value());
                assertTrue(rejected.getReason().contains("Không thể chuyển trạng thái điểm danh"));
                assertEquals(AttendanceStatus.PRESENT, attendance.findById(record.getId()).orElseThrow().getStatus());
                assertEquals(historyCount, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size());
                assertEquals(auditCount, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='ATTENDANCE' AND entity_id=?",
                        Integer.class, record.getId().toString()));
            }
            System.out.println("ATTENDANCE_TRAINING_SQLSERVER_RACE correction=" + correctionStatus
                    + " result=" + resultStatus + " final=" + attendance.findById(record.getId()).orElseThrow().getStatus()
                    + " hasResult=" + results.existsByClassSessionIdAndMemberId(session.getId(), member.getId()));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void existingResultRejectsAdminCorrectionWithoutHistoryOrAuditTransition() {
        trainingService.recordResult(session.getId(), member.getId(),
                new RecordTrainingResultRequest(8, "Synthetic SQL Server result-first case"), jwt(coachUser));
        int historyCount = histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size();
        int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='ATTENDANCE' AND entity_id=?",
                Integer.class, record.getId().toString());
        ResponseStatusException rejected = assertThrows(ResponseStatusException.class, () ->
                attendanceService.mark(session.getId(), member.getId(),
                        new MarkAttendanceRequest(AttendanceStatus.EXCUSED, null, "Synthetic Admin correction"), jwt(admin)));
        assertEquals(409, rejected.getStatusCode().value());
        assertTrue(rejected.getReason().contains("Không thể chuyển trạng thái điểm danh"));
        assertEquals(AttendanceStatus.PRESENT, attendance.findById(record.getId()).orElseThrow().getStatus());
        assertTrue(results.existsByClassSessionIdAndMemberId(session.getId(), member.getId()));
        assertEquals(historyCount, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size());
        assertEquals(auditCount, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='ATTENDANCE' AND entity_id=?",
                Integer.class, record.getId().toString()));
    }

    @AfterEach
    void cleanExactFixture() {
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id IN (SELECT id FROM dbo.users WHERE name LIKE ?)", marker + "%");
        if (session != null) {
            Integer sessionId = session.getId(); Integer attendanceId = record == null ? null : record.getId();
            if (attendanceId != null) {
                jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='ATTENDANCE' AND entity_id=?", attendanceId.toString());
                jdbc.update("DELETE FROM dbo.attendance_history WHERE attendance_id=?", attendanceId);
            }
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='TRAINING_RESULT' AND entity_id IN "
                    + "(SELECT CAST(id AS varchar(30)) FROM dbo.training_result WHERE class_session_id=? AND member_id=?)",
                    sessionId, member.getId());
            jdbc.update("DELETE FROM dbo.training_result WHERE class_session_id=? AND member_id=?", sessionId, member.getId());
            jdbc.update("DELETE FROM dbo.attendance WHERE class_session_id=? AND member_id=?", sessionId, member.getId());
            if (registration != null) jdbc.update("DELETE FROM dbo.course_registration WHERE id=?", registration.getId());
            jdbc.update("DELETE FROM dbo.class_session WHERE id=?", sessionId);
        }
        if (courseClass != null) jdbc.update("DELETE FROM dbo.class WHERE id=?", courseClass.getId());
        if (offering != null) jdbc.update("DELETE FROM dbo.course_offering WHERE id=?", offering.getId());
        if (course != null) jdbc.update("DELETE FROM dbo.course WHERE id=?", course.getId());
        if (sport != null) jdbc.update("DELETE FROM dbo.sport WHERE id=?", sport.getId());
        if (coach != null) jdbc.update("DELETE FROM dbo.coach WHERE id=?", coach.getId());
        jdbc.update("DELETE FROM dbo.users WHERE name LIKE ? AND email LIKE ?", marker + "%", "%@attendance-qa.test");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, marker + "%"));
        baselineCounts.forEach((table, count) -> assertEquals(count,
                jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[" + table + "]", Integer.class), table + " row count after cleanup"));
        System.out.println("ATTENDANCE_TRAINING_SQLSERVER_FIXTURE_CLEANED=true");
    }

    private User user(String role) {
        User user = new User(); user.setName(marker + " " + role);
        user.setEmail(marker.toLowerCase() + "-" + role.toLowerCase() + "@attendance-qa.test");
        user.setPasswordHash("synthetic-unused-hash"); user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private Jwt jwt(User user) { return jwtService.validateToken(jwtService.generateToken(user)); }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("SQL Server race start gate timed out");
    }
}
