package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.security.JwtService;

/** Clone-pinned, synthetic-only HTTP check for PT outcome state and financial invariants. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "debug=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase8CJPtOutcomeSqlServerIT {
    private static final String RUN_ID = System.getProperty("phase8cj.runId", "");
    private static final String MARK = "PHASE8CJ-PT-" + RUN_ID + "-";
    private static final String TX_MARK = "P8J" + (RUN_ID.length() > 10 ? RUN_ID.substring(0, 10) : RUN_ID) + "-";
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    private final List<Integer> userIds = new ArrayList<>();
    private final List<Integer> coachIds = new ArrayList<>();
    private final List<Integer> bookingIds = new ArrayList<>();
    private final List<Integer> paymentIds = new ArrayList<>();
    private Integer sportId;
    private List<Long> baselineCounts;
    private String historicalBookingDigest;
    private User member, assignedCoach, unassignedCoach, inactiveCoach, admin, receptionist;
    private Integer assignedCoachId, inactiveCoachId;

    @BeforeAll
    void verifyTargetAndCaptureBaseline() throws Exception {
        assertFalse(RUN_ID.isBlank(), "Explicit phase8cj.runId is required");
        CloneGuard.verifyRuntime(dataSource, environment, context);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, MARK + "%"));
        baselineCounts = List.of(
                count("users"), count("coach"), count("sport"), count("personal_training_booking"),
                count("payment"), count("audit_log"));
        historicalBookingDigest = bookingDigest();
    }

    @Test
    void coachAndAdminOutcomeHttpPreservesPaymentAndRejectsUnauthorizedOrEarlyChanges() throws Exception {
        createUsersAndCoaches();
        int completedBooking = createBooking(assignedCoachId, LocalDateTime.now().minusHours(2));
        int noShowBooking = createBooking(assignedCoachId, LocalDateTime.now().minusHours(2));
        int earlyBooking = createBooking(assignedCoachId, LocalDateTime.now().plusMinutes(2));
        int inactiveBooking = createBooking(inactiveCoachId, LocalDateTime.now().minusHours(2));
        int concurrentBooking = createBooking(assignedCoachId, LocalDateTime.now().minusHours(2));
        int conflictingBooking = createBooking(assignedCoachId, LocalDateTime.now().minusHours(2));
        int paymentId = createSuccessfulPayment(completedBooking);
        int noShowPaymentId = createSuccessfulPayment(noShowBooking);

        assertHttp("PATCH", "/api/pt-bookings/" + completedBooking + "/outcome", assignedCoach,
                "{\"outcome\":\"COMPLETED\"}", 200);
        assertEquals("COMPLETED", bookingStatus(completedBooking));
        assertEquals(1, auditCount(completedBooking));

        assertHttp("PATCH", "/api/pt-bookings/" + noShowBooking + "/outcome", assignedCoach,
                "{\"outcome\":\"NO_SHOW\"}", 200);
        assertEquals("NO_SHOW", bookingStatus(noShowBooking));
        assertEquals(1, auditCount(noShowBooking));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, noShowPaymentId));
        assertNull(jdbc.queryForObject("SELECT refunded_at FROM dbo.payment WHERE id=?", java.time.LocalDateTime.class, noShowPaymentId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=?", Integer.class, member.getId()));
        assertHttp("PATCH", "/api/pt-bookings/" + completedBooking + "/outcome", assignedCoach,
                "{\"outcome\":\"COMPLETED\"}", 200);
        assertHttp("PATCH", "/api/pt-bookings/" + completedBooking + "/outcome", assignedCoach,
                "{\"outcome\":\"NO_SHOW\"}", 409);
        assertEquals(1, auditCount(completedBooking));

        assertHttp("PATCH", "/api/pt-bookings/" + earlyBooking + "/outcome", assignedCoach,
                "{\"outcome\":\"NO_SHOW\"}", 409);
        assertEquals("CONFIRMED", bookingStatus(earlyBooking));
        assertHttp("PATCH", "/api/pt-bookings/" + completedBooking + "/outcome", unassignedCoach,
                "{\"outcome\":\"NO_SHOW\"}", 403);
        assertHttp("PATCH", "/api/pt-bookings/" + completedBooking + "/outcome", member,
                "{\"outcome\":\"NO_SHOW\"}", 403);
        assertHttp("PATCH", "/api/pt-bookings/" + inactiveBooking + "/outcome", inactiveCoach,
                "{\"outcome\":\"NO_SHOW\"}", 403);
        assertEquals("CONFIRMED", bookingStatus(inactiveBooking));

        String adminPath = "/api/admin/pt-bookings/" + completedBooking + "/outcome";
        assertHttp("PATCH", adminPath, receptionist,
                "{\"outcome\":\"NO_SHOW\",\"reason\":\"unauthorized\"}", 403);
        assertHttp("PATCH", adminPath, admin,
                "{\"outcome\":\"NO_SHOW\",\"reason\":\"  \"}", 400);
        assertHttp("PATCH", adminPath, admin,
                "{\"outcome\":\"NO_SHOW\",\"reason\":\"Synthetic attendance correction\"}", 200);
        assertEquals("NO_SHOW", bookingStatus(completedBooking));
        assertHttp("PATCH", adminPath, admin,
                "{\"outcome\":\"NO_SHOW\",\"reason\":\"duplicate\"}", 200);
        assertEquals(2, auditCount(completedBooking));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PT_BOOKING_OUTCOME' AND entity_id=? AND action='RECORD'", Integer.class, Integer.toString(completedBooking)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PT_BOOKING_OUTCOME' AND entity_id=? AND action='CORRECT' AND actor_role='ADMIN'", Integer.class, Integer.toString(completedBooking)));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, paymentId));
        assertNull(jdbc.queryForObject("SELECT refunded_at FROM dbo.payment WHERE id=?", java.time.LocalDateTime.class, paymentId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=?", Integer.class, member.getId()));
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            String coachToken = jwtService.generateToken(assignedCoach);
            Future<Integer> first = workers.submit(() -> concurrentOutcomeAfter(ready, start, concurrentBooking, coachToken));
            Future<Integer> second = workers.submit(() -> concurrentOutcomeAfter(ready, start, concurrentBooking, coachToken));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(200, first.get(15, TimeUnit.SECONDS)); assertEquals(200, second.get(15, TimeUnit.SECONDS));
            assertEquals("COMPLETED", bookingStatus(concurrentBooking)); assertEquals(1, auditCount(concurrentBooking));

        CountDownLatch conflictingReady = new CountDownLatch(2); CountDownLatch conflictingStart = new CountDownLatch(1);
        ExecutorService conflictingWorkers = Executors.newFixedThreadPool(2);
        try {
            String conflictingCoachToken = jwtService.generateToken(assignedCoach);
            Future<Integer> completed = conflictingWorkers.submit(() -> concurrentOutcomeAfter(conflictingReady, conflictingStart,
                    conflictingBooking, conflictingCoachToken, "COMPLETED"));
            Future<Integer> noShow = conflictingWorkers.submit(() -> concurrentOutcomeAfter(conflictingReady, conflictingStart,
                    conflictingBooking, conflictingCoachToken, "NO_SHOW"));
            assertTrue(conflictingReady.await(5, TimeUnit.SECONDS)); conflictingStart.countDown();
            int completedStatus = completed.get(15, TimeUnit.SECONDS);
            int noShowStatus = noShow.get(15, TimeUnit.SECONDS);
            assertTrue((completedStatus == 200 && noShowStatus == 409) || (completedStatus == 409 && noShowStatus == 200),
                    "Exactly one conflicting Coach outcome may win; the other must conflict");
            assertEquals(completedStatus == 200 ? "COMPLETED" : "NO_SHOW", bookingStatus(conflictingBooking));
            assertEquals(1, auditCount(conflictingBooking));
        } finally { conflictingWorkers.shutdownNow(); }
        } finally { workers.shutdownNow(); }
        System.out.println("PHASE8CJ_PT_HTTP completed=PASS idempotency=PASS conflict=PASS early=PASS authorization=PASS adminCorrection=PASS paymentInvariant=PASS concurrentSameOutcome=PASS");
    }

    @AfterAll
    void cleanupOnlyRunOwnedRows() throws Exception {
        if (userIds.isEmpty()) return;
        CloneGuard.verifyRuntime(dataSource, environment, context);
        for (Integer bookingId : bookingIds) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='PT_BOOKING_OUTCOME' AND entity_id=?", Integer.toString(bookingId));
        }
        if (!paymentIds.isEmpty()) jdbc.update("DELETE FROM dbo.payment WHERE id IN (" + marks(paymentIds.size()) + ") AND transaction_code LIKE ?", withArgs(paymentIds, TX_MARK + "%"));
        if (!bookingIds.isEmpty()) jdbc.update("DELETE FROM dbo.personal_training_booking WHERE id IN (" + marks(bookingIds.size()) + ")", bookingIds.toArray());
        if (!coachIds.isEmpty()) jdbc.update("DELETE FROM dbo.coach WHERE id IN (" + marks(coachIds.size()) + ") AND user_id IN (" + marks(userIds.size()) + ")", concat(coachIds, userIds));
        if (sportId != null) jdbc.update("DELETE FROM dbo.sport WHERE id=? AND name LIKE ?", sportId, MARK + "%");
        jdbc.update("DELETE FROM dbo.users WHERE id IN (" + marks(userIds.size()) + ") AND name LIKE ?", withArgs(userIds, MARK + "%"));
        List<Long> after = List.of(count("users"), count("coach"), count("sport"), count("personal_training_booking"), count("payment"), count("audit_log"));
        assertEquals(baselineCounts, after, "Clone aggregate counts must return to the pre-fixture baseline");
        assertEquals(historicalBookingDigest, bookingDigest(), "Historical PT booking rows must be byte-for-byte equivalent at the SQL value level");
    }

    private void createUsersAndCoaches() {
        member = createUser("MEMBER", "member"); assignedCoach = createUser("COACH", "coach-assigned");
        unassignedCoach = createUser("COACH", "coach-unassigned"); inactiveCoach = createUser("COACH", "coach-inactive");
        admin = createUser("ADMIN", "admin"); receptionist = createUser("RECEPTIONIST", "receptionist");
        sportId = jdbc.queryForObject("INSERT dbo.sport(name,status) OUTPUT INSERTED.id VALUES (?, 'ACTIVE')", Integer.class, MARK + "Sport");
        assignedCoachId = insertCoach(assignedCoach.getId(), "ACTIVE"); coachIds.add(assignedCoachId);
        coachIds.add(insertCoach(unassignedCoach.getId(), "ACTIVE"));
        inactiveCoachId = insertCoach(inactiveCoach.getId(), "INACTIVE"); coachIds.add(inactiveCoachId);
    }

    private User createUser(String role, String label) {
        String name = MARK + label; String email = "phase8cj." + RUN_ID + "." + label + "." + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        Integer id = jdbc.queryForObject("INSERT dbo.users(name,email,password_hash,role,status,created_at) OUTPUT INSERTED.id VALUES (?, ?, 'synthetic-not-used', ?, 'ACTIVE', SYSUTCDATETIME())", Integer.class, name, email, role);
        userIds.add(id); User user = new User(); user.setId(id); user.setName(name); user.setEmail(email); user.setRole(role); user.setStatus("ACTIVE"); return user;
    }

    private int insertCoach(int userId, String status) {
        Integer coachId = jdbc.queryForObject("INSERT dbo.coach(user_id,status) OUTPUT INSERTED.id VALUES (?,?)", Integer.class, userId, status);
        return coachId;
    }

    private int createBooking(int coachId, LocalDateTime end) {
        LocalDateTime start = end.minusMinutes(90);
        Integer id = jdbc.queryForObject("INSERT dbo.personal_training_booking(member_id,coach_id,sport_id,start_time,end_time,original_price,discount,final_price,status,created_at) OUTPUT INSERTED.id VALUES (?,?,?,?,?,500000,0,500000,'CONFIRMED',SYSUTCDATETIME())", Integer.class, member.getId(), coachId, sportId, start, end);
        bookingIds.add(id); return id;
    }

    private int createSuccessfulPayment(int bookingId) {
        String tx = TX_MARK + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Integer id = jdbc.queryForObject("INSERT dbo.payment(member_id,amount,method,purpose,reference_id,status,transaction_code,created_at,paid_at) OUTPUT INSERTED.id VALUES (?,500000,'COUNTER','PT_BOOKING',?,'SUCCESS',?,SYSUTCDATETIME(),SYSUTCDATETIME())", Integer.class, member.getId(), bookingId, tx);
        paymentIds.add(id); return id;
    }

    private void assertHttp(String method, String path, User actor, String body, int status) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + jwtService.generateToken(actor))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), "Sanitized API assertion for " + method + " " + path);
    }

    private int concurrentOutcomeAfter(CountDownLatch ready, CountDownLatch start, int bookingId, String token) throws Exception {
        return concurrentOutcomeAfter(ready, start, bookingId, token, "COMPLETED");
    }

    private int concurrentOutcomeAfter(CountDownLatch ready, CountDownLatch start, int bookingId, String token, String outcome) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start gate timed out");
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/pt-bookings/"+bookingId+"/outcome"))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .method("PATCH",HttpRequest.BodyPublishers.ofString("{\"outcome\":\""+outcome+"\"}")).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String bookingStatus(int id) { return jdbc.queryForObject("SELECT status FROM dbo.personal_training_booking WHERE id=?", String.class, id); }
    private int auditCount(int id) { return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PT_BOOKING_OUTCOME' AND entity_id=?", Integer.class, Integer.toString(id)); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.[" + table + "]", Long.class); }
    private String bookingDigest() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM dbo.personal_training_booking ORDER BY id")) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    Object value = rows.getObject(column);
                    byte[] bytes = (value == null ? "<NULL>" : value.toString()).getBytes(StandardCharsets.UTF_8);
                    digest.update((byte) (bytes.length >>> 24)); digest.update((byte) (bytes.length >>> 16));
                    digest.update((byte) (bytes.length >>> 8)); digest.update((byte) bytes.length); digest.update(bytes);
                }
            }
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }
    private static String marks(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private static Object[] withArgs(List<Integer> ids, Object tail) { List<Object> all=new ArrayList<>(ids); all.add(tail); return all.toArray(); }
    private static Object[] concat(List<Integer> a, List<Integer> b) { List<Object> all=new ArrayList<>(a); all.addAll(b); return all.toArray(); }

    public static final class CloneGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final String URL="jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo_PTOutcome_8CF1;encrypt=true;trustServerCertificate=true";
        @Override public void initialize(ConfigurableApplicationContext context) {
            var env=context.getEnvironment();
            if (!URL.equals(env.getProperty("spring.datasource.url")) || !"validate".equalsIgnoreCase(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"false".equalsIgnoreCase(env.getProperty("sportcenter.scheduling.enabled")) || !"false".equalsIgnoreCase(env.getProperty("sportcenter.renewal.scheduler.enabled"))
                    || !"false".equalsIgnoreCase(env.getProperty("sportcenter.pt.outcome-fallback.enabled")) || !"mock".equals(env.getProperty("app.payment.provider"))
                    || !"true".equalsIgnoreCase(env.getProperty("app.payment.mock-gateway.enabled"))) throw new IllegalStateException("Phase 8C-J PT test config refused");
            String username=env.getProperty("spring.datasource.username"), password=env.getProperty("spring.datasource.password");
            if (username==null || username.isBlank() || password==null || password.isBlank()) throw new IllegalStateException("Clone credentials unavailable");
            Properties props=new Properties(); props.setProperty("user",username); props.setProperty("password",password); props.setProperty("encrypt","true"); props.setProperty("trustServerCertificate","true");
            try(Connection c=DriverManager.getConnection(URL,props)){ verifyIdentity(c); } catch(Exception e){ throw new IllegalStateException("Clone identity refused before startup",e); }
        }
        static void verifyRuntime(DataSource ds, Environment env, ApplicationContext context) throws Exception {
            if (!URL.equals(env.getProperty("spring.datasource.url")) || !"validate".equalsIgnoreCase(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty()) throw new IllegalStateException("Effective runtime target/scheduler guard failed");
            try(Connection c=ds.getConnection()){ verifyIdentity(c); }
        }
        static void verifyIdentity(Connection c) throws Exception {
            try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT SERVERPROPERTY('ServerName'),SERVERPROPERTY('ProductVersion'),DB_NAME(),DB_ID(),CONVERT(nvarchar(30),DATABASEPROPERTYEX(DB_NAME(),'Status'))")){
                if(!r.next() || !"LAPTOP-416VBDLK".equals(r.getString(1)) || !"17.0.1155.3".equals(r.getString(2)) || !"SportCenterDemo_PTOutcome_8CF1".equals(r.getString(3)) || r.getInt(4)!=12 || !"ONLINE".equalsIgnoreCase(r.getString(5)) || r.next()) throw new IllegalStateException("SQL target is not approved clone ID 12");
            }
        }
    }
}
