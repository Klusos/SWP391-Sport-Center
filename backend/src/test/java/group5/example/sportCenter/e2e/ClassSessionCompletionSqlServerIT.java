package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseAgePolicy;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.ClassSessionCompletionService;

/** Clone-pinned synthetic proof of elapsed class completion, SQL locking, and audit idempotency. */
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
class ClassSessionCompletionSqlServerIT {
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
    @Autowired ClassSessionCompletionService completion;
    @MockitoSpyBean ClassSessionRepository sessions;

    private final String marker = "CSC-" + UUID.randomUUID().toString().substring(0, 10);
    private User member;
    private Coach coach;
    private Sport sport;
    private Course course;
    private CourseOffering offering;
    private CourseClass courseClass;
    private ClassSession elapsed;
    private final java.util.Map<String, Integer> baseline = new java.util.LinkedHashMap<>();

    @BeforeEach
    void createRunOwnedFixture() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys.dm_exec_requests r "
                + "JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id "
                + "WHERE r.database_id=DB_ID() AND r.session_id<>@@SPID AND s.is_user_process=1 "
                + "AND UPPER(r.command) IN ('INSERT','UPDATE','DELETE','MERGE','ALTER','CREATE','DROP','TRUNCATE')",
                Integer.class), "Competing DB12 writer detected before fixture setup");
        for (String table : new String[] { "users", "coach", "sport", "course", "course_offering", "class", "class_session", "audit_log" }) {
            baseline.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[" + table + "]", Integer.class));
        }
        member = user("MEMBER");
        User coachUser = user("COACH");
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
        elapsed = session(now.minusHours(2), now.minusHours(1), "SCHEDULED");
    }

    @Test
    void elapsedSessionCompletesOnceUnderConcurrentSqlServerInvocations() throws Exception {
        doReturn(List.of(elapsed.getId())).when(sessions).findElapsedScheduledIds(any(LocalDateTime.class));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> invokeTogether(ready, start));
            Future<Integer> second = pool.submit(() -> invokeTogether(ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int a = first.get(30, TimeUnit.SECONDS);
            int b = second.get(30, TimeUnit.SECONDS);
            assertEquals(1, a + b, "Only one invocation may commit the status transition");
            assertEquals("COMPLETED", sessions.findById(elapsed.getId()).orElseThrow().getStatus());
            assertEquals(1, auditCount(elapsed.getId()));
            assertEquals(0, completion.completeElapsedScheduledSessions(), "Repeated invocation must be idempotent");
            assertEquals(1, auditCount(elapsed.getId()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.attendance WHERE class_session_id=?", Integer.class, elapsed.getId()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_result WHERE class_session_id=?", Integer.class, elapsed.getId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void futureAndCancelledCandidatesRemainUnchanged() {
        LocalDateTime now = LocalDateTime.now(VN);
        ClassSession future = session(now.plusHours(1), now.plusHours(2), "SCHEDULED");
        ClassSession cancelled = session(now.minusHours(2), now.minusHours(1), "CANCELLED");
        doReturn(List.of(future.getId(), cancelled.getId())).when(sessions).findElapsedScheduledIds(any(LocalDateTime.class));
        assertEquals(0, completion.completeElapsedScheduledSessions());
        assertEquals("SCHEDULED", sessions.findById(future.getId()).orElseThrow().getStatus());
        assertEquals("CANCELLED", sessions.findById(cancelled.getId()).orElseThrow().getStatus());
        assertEquals(0, auditCount(future.getId()));
        assertEquals(0, auditCount(cancelled.getId()));
    }

    private int invokeTogether(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("SQL Server start gate timed out");
        return completion.completeElapsedScheduledSessions();
    }

    private ClassSession session(LocalDateTime start, LocalDateTime end, String status) {
        ClassSession row = new ClassSession(); row.setCourseClass(courseClass); row.setStartTime(start);
        row.setEndTime(end); row.setStatus(status); return sessions.saveAndFlush(row);
    }

    private int auditCount(Integer id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='CLASS_SESSION' AND entity_id=? AND action='STATUS_CHANGE'",
                Integer.class, id.toString());
    }

    private User user(String role) {
        User user = new User(); user.setName(marker + " " + role);
        user.setEmail(marker.toLowerCase() + "-" + role.toLowerCase() + "@completion-qa.test");
        user.setPasswordHash("synthetic-unused-hash"); user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    @AfterEach
    void cleanExactFixture() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        for (ClassSession row : List.of(elapsed)) deleteSession(row);
        for (Integer id : jdbc.queryForList("SELECT id FROM dbo.class_session WHERE class_id=?", Integer.class,
                courseClass == null ? -1 : courseClass.getId())) {
            deleteSession(sessions.findById(id).orElseThrow());
        }
        if (courseClass != null) jdbc.update("DELETE FROM dbo.[class] WHERE id=?", courseClass.getId());
        if (offering != null) jdbc.update("DELETE FROM dbo.course_offering WHERE id=?", offering.getId());
        if (course != null) jdbc.update("DELETE FROM dbo.course WHERE id=?", course.getId());
        if (sport != null) jdbc.update("DELETE FROM dbo.sport WHERE id=?", sport.getId());
        if (coach != null) jdbc.update("DELETE FROM dbo.coach WHERE id=?", coach.getId());
        jdbc.update("DELETE FROM dbo.users WHERE name LIKE ? AND email LIKE ?", marker + "%", "%@completion-qa.test");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, marker + "%"));
        baseline.forEach((table, count) -> assertEquals(count,
                jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[" + table + "]", Integer.class), table + " row count after cleanup"));
    }

    private void deleteSession(ClassSession row) {
        if (row == null) return;
        jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='CLASS_SESSION' AND entity_id=?", row.getId().toString());
        jdbc.update("DELETE FROM dbo.class_session WHERE id=?", row.getId());
    }
}
