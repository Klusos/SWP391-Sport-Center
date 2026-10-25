package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import group5.example.sportCenter.scheduler.MembershipScheduler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-sqlserver")
@ContextConfiguration(initializers = SqlServerE2ETargetGuard.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseRegistrationSqlServerE2EIT {

    private static final int E2E_ADMIN_ID = 11;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final SecureRandom RANDOM = new SecureRandom();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    // Replacing the scheduled bean makes its scheduled callback a no-op in this context.
    @MockitoBean
    MembershipScheduler membershipScheduler;

    private final String runId = "E2E-" + UUID.randomUUID().toString().replace("-", "");
    private final Map<String, String> passwords = new HashMap<>();
    private final Map<String, String> tokens = new HashMap<>();
    private final List<String> createdRecordIds = new ArrayList<>();
    private java.net.http.HttpClient http;
    private String adminEmail;
    private String adminPassword;

    @BeforeAll
    void resetOnlyAuthorizedAdminAndAuthenticate() throws Exception {
        // Repeat critical opt-in locally; the ApplicationContextInitializer has already
        // completed the full read-only target/account/schema preflight before server startup.
        if (!"DESKTOP-EMK77PD/SportCenterDB".equals(System.getenv("SPORTCENTER_E2E_SQLSERVER_CONFIRM"))) {
            throw new IllegalStateException("Missing exact E2E target opt-in");
        }
        adminEmail = requiredEnv("E2E_ADMIN_EMAIL");
        adminPassword = randomPassword();
        String hash = passwordEncoder.encode(adminPassword);

        int changed = jdbc.update(
                "UPDATE dbo.users SET password_hash = ? "
                        + "WHERE id = ? AND email = ? AND role = 'ADMIN' AND status = 'ACTIVE'",
                hash, E2E_ADMIN_ID, adminEmail);
        assertEquals(1, changed, "Password reset must affect only the authorized active E2E ADMIN id=11");

        http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        tokens.put("admin", login(adminEmail, adminPassword, "ADMIN"));
    }

    @Test
    void phase2AuthenticatedHttpScenariosAndSqlSnapshots() throws Exception {
        assertEquals(401, request("GET", "/api/course-registrations/me", null, null).statusCode());
        assertEquals(401, request("GET", "/api/course-registrations/me",
                "not-a-valid-jwt", null).statusCode());
        assertEquals(200, request("GET", "/api/course-registrations", tokens.get("admin"), null).statusCode());

        String receptionistPassword = createPassword("receptionist");
        String receptionistEmail = email("receptionist");
        JsonNode receptionist = createUser(receptionistEmail, receptionistPassword, "RECEPTIONIST");
        createdRecordIds.add("user:" + receptionist.path("id").asInt());
        tokens.put("receptionist", login(receptionistEmail, receptionistPassword, "RECEPTIONIST"));

        String memberAPassword = createPassword("member-a");
        String memberAEmail = email("member-a");
        JsonNode memberA = createMember(memberAEmail, memberAPassword);
        int memberAId = memberA.path("id").asInt();
        createdRecordIds.add("user:" + memberAId);
        tokens.put("memberA", login(memberAEmail, memberAPassword, "MEMBER"));

        String memberBPassword = createPassword("member-b");
        String memberBEmail = email("member-b");
        JsonNode memberB = createMember(memberBEmail, memberBPassword);
        int memberBId = memberB.path("id").asInt();
        createdRecordIds.add("user:" + memberBId);
        tokens.put("memberB", login(memberBEmail, memberBPassword, "MEMBER"));

        String priceMemberPassword = createPassword("price-member");
        String priceMemberEmail = email("price-member");
        JsonNode priceMember = createMember(priceMemberEmail, priceMemberPassword);
        int priceMemberId = priceMember.path("id").asInt();
        createdRecordIds.add("user:" + priceMemberId);
        tokens.put("priceMember", login(priceMemberEmail, priceMemberPassword, "MEMBER"));

        String coachPassword = createPassword("coach-a");
        String coachEmail = email("coach-a");
        JsonNode coach = createCoach(coachEmail, coachPassword);
        int coachId = coach.path("id").asInt();
        createdRecordIds.add("coach:" + coachId);
        tokens.put("coach", login(coachEmail, coachPassword, "COACH"));

        String secondCoachPassword = createPassword("coach-b");
        String secondCoachEmail = email("coach-b");
        JsonNode secondCoach = createCoach(secondCoachEmail, secondCoachPassword);
        int secondCoachId = secondCoach.path("id").asInt();
        createdRecordIds.add("coach:" + secondCoachId);
        tokens.put("coachB", login(secondCoachEmail, secondCoachPassword, "COACH"));

        assertEquals(200, request("GET", "/api/course-registrations",
                tokens.get("receptionist"), null).statusCode());
        assertEquals(403, request("GET", "/api/course-registrations",
                tokens.get("memberA"), null).statusCode());
        assertEquals(403, request("GET", "/api/course-registrations",
                tokens.get("coach"), null).statusCode());
        assertEquals(403, request("GET", "/api/course-registrations/me",
                tokens.get("receptionist"), null).statusCode());
        assertEquals(200, request("GET", "/api/course-registrations/me",
                tokens.get("memberA"), null).statusCode());

        int sportId = createSport();
        assignSport(coachId, sportId);
        assignSport(secondCoachId, sportId);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate boundaryStart = today.plusDays(30);
        int boundaryCourse = createCourse(sportId, "boundary-course");
        int boundaryOffering = createOffering(boundaryCourse, "boundary-offering",
                boundaryStart, new BigDecimal("100.00"));
        int capacityClass = createClass(boundaryOffering, coachId, "capacity-class", 1);
        int capacitySession = createSession(capacityClass, boundaryStart.atTime(9, 0),
                boundaryStart.atTime(10, 0));
        createdRecordIds.add("session:" + capacitySession);

        // 31 days before start is outside the 30-day opening window.
        LocalDate closedStart = today.plusDays(31);
        int closedCourse = createCourse(sportId, "closed-window-course");
        int closedOffering = createOffering(closedCourse, "closed-window-offering",
                closedStart, new BigDecimal("120.00"));
        int closedClass = createClass(closedOffering, coachId, "closed-window-class", 2);
        int registrationsBefore = registrationCount();
        assertEquals(409, postRegistration(tokens.get("memberA"), closedClass).statusCode());
        assertEquals(registrationsBefore, registrationCount(),
                "Rejected registration must not persist a row");

        // Competing MEMBERS race for the single remaining seat. A pending hold occupies it immediately.
        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = executor.submit(() -> concurrentRegistration(
                    tokens.get("memberA"), capacityClass, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentRegistration(
                    tokens.get("memberB"), capacityClass, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both HTTP workers should reach the start barrier");
            start.countDown();
            List<Integer> statuses = List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        } finally {
            executor.shutdownNow();
        }

        List<JsonNode> active = activeRegistrations(capacityClass);
        assertEquals(1, active.size(), "Database capacity must remain at one active hold/registration");
        JsonNode winningRegistration = active.getFirst();
        assertEquals("PENDING_PAYMENT", winningRegistration.path("status").asText());
        assertTrue(winningRegistration.path("paymentDeadline").isTextual());
        assertEquals(0, new BigDecimal("100.00").compareTo(winningRegistration.path("originalPrice").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(winningRegistration.path("membershipDiscount").decimalValue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(winningRegistration.path("finalPrice").decimalValue()));
        int winningMemberId = winningRegistration.path("memberId").asInt();
        String winningToken = winningMemberId == memberAId ? tokens.get("memberA") : tokens.get("memberB");
        String losingToken = winningMemberId == memberAId ? tokens.get("memberB") : tokens.get("memberA");
        confirmCounterCourseRegistration(winningToken, winningRegistration.path("id").asInt());
        assertFalse(containsClass(getJson("/api/course-registrations/me", losingToken), capacityClass),
                "A MEMBER must not see the other MEMBER registration");
        assertEquals(409, postRegistration(winningToken, capacityClass).statusCode(),
                "Duplicate registration should be rejected");
        int siblingClass = createClass(boundaryOffering, secondCoachId, "same-offering-sibling", 3);
        assertEquals(409, postRegistration(winningToken, siblingClass).statusCode(),
                "A second class in the same offering should be rejected");

        // A separate offering has one overlapping session and one adjacent session.
        int scheduleCourse = createCourse(sportId, "schedule-course");
        int scheduleOffering = createOffering(scheduleCourse, "schedule-offering",
                boundaryStart, new BigDecimal("150.00"));
        int overlappingClass = createClass(scheduleOffering, secondCoachId, "overlap-class", 5);
        int adjacentClass = createClass(scheduleOffering, coachId, "adjacent-class", 5);
        createdRecordIds.add("session:" + createSession(overlappingClass,
                boundaryStart.atTime(9, 30), boundaryStart.atTime(10, 30)));
        createdRecordIds.add("session:" + createSession(adjacentClass,
                boundaryStart.atTime(10, 0), boundaryStart.atTime(11, 0)));
        assertEquals(409, postRegistration(winningToken, overlappingClass).statusCode(),
                "Overlapping sessions must conflict");
        HttpResult adjacentResponse = postRegistration(winningToken, adjacentClass);
        assertEquals(201, adjacentResponse.statusCode(),
                "Sessions meeting at an endpoint are adjacent, not overlapping");
        createdRecordIds.add("registration:" + adjacentResponse.body().path("id").asInt());
        confirmCounterCourseRegistration(winningToken, adjacentResponse.body().path("id").asInt());

        JsonNode owned = getJson("/api/course-registrations/me", winningToken);
        assertTrue(containsClass(owned, capacityClass));
        assertTrue(containsClass(owned, adjacentClass));
        assertFalse(containsClass(owned, siblingClass));
        JsonNode sessionList = getJson("/api/course-registrations/me/class-sessions", winningToken);
        assertTrue(containsClass(sessionList, capacityClass));
        assertTrue(containsClass(sessionList, adjacentClass));
        assertFalse(containsClass(sessionList, siblingClass));

        // Membership price is computed by the server and persisted as an enrollment snapshot.
        int planId = createMembershipPlan();
        JsonNode membership = postJson("/api/memberships", tokens.get("admin"),
                Map.of("memberId", priceMemberId, "planId", planId, "startDate", today.toString(),
                        "autoRenew", false), 201);
        createdRecordIds.add("membership:" + membership.path("id").asInt());
        int priceCourse = createCourse(sportId, "price-course");
        int priceOffering = createOffering(priceCourse, "price-offering", today.plusDays(20),
                new BigDecimal("999.99"));
        int priceClass = createClass(priceOffering, coachId, "price-class", 4);
        createdRecordIds.add("session:" + createSession(priceClass, today.plusDays(20).atTime(13, 0),
                today.plusDays(20).atTime(14, 0)));
        JsonNode priced = postRegistration(tokens.get("priceMember"), priceClass).body();
        assertEquals("PENDING_PAYMENT", priced.path("status").asText());
        assertEquals("999.99", priced.path("originalPrice").decimalValue().toPlainString());
        assertEquals("255.00", priced.path("membershipDiscount").decimalValue().toPlainString());
        assertEquals("744.99", priced.path("finalPrice").decimalValue().toPlainString());
        createdRecordIds.add("registration:" + priced.path("id").asInt());
        confirmCounterCourseRegistration(tokens.get("priceMember"), priced.path("id").asInt());

        verifyRegistrationSnapshot(priced, priceMemberId, priceClass);
        assertEquals(403, postRegistration(tokens.get("admin"), capacityClass).statusCode(),
                "Only MEMBER may invoke the self-registration endpoint");
        assertTrue(createdRecordIds.size() >= 12);
        // Only IDs are emitted; credentials, hashes, and JWTs are never part of the report.
    }

    private void verifyApplicationDataSourceBeforePasswordReset(String expectedEmail) throws Exception {
        String expectedRunId = requiredEnv("E2E_ADMIN_RUN_ID");
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                ResultSet identity = statement.executeQuery(
                        "SELECT @@SERVERNAME, DB_NAME(), d.state_desc FROM sys.databases d WHERE d.name = DB_NAME()")) {
            assertTrue(identity.next());
            assertEquals("DESKTOP-EMK77PD", identity.getString(1));
            assertEquals("SportCenterDB", identity.getString(2));
            assertEquals("ONLINE", identity.getString(3));
        }
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT email, role, status FROM dbo.users WHERE id = ?")) {
            statement.setInt(1, E2E_ADMIN_ID);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedEmail.toLowerCase(), result.getString("email").toLowerCase());
                assertTrue(result.getString("email").toLowerCase().contains(expectedRunId.toLowerCase()));
                assertEquals("ADMIN", result.getString("role"));
                assertEquals("ACTIVE", result.getString("status"));
                assertFalse(result.next());
            }
        }
    }
    private int concurrentRegistration(String token, int classId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency start timed out");
        return postRegistration(token, classId).statusCode();
    }

    private int createSport() throws Exception {
        JsonNode response = postJson("/api/sports", tokens.get("admin"),
                Map.of("name", "Sport " + runId, "description", "E2E fixture"), 201);
        int id = response.path("id").asInt();
        createdRecordIds.add("sport:" + id);
        return id;
    }

    private int createCourse(int sportId, String label) throws Exception {
        JsonNode response = postJson("/api/courses", tokens.get("admin"),
                Map.of("sportId", sportId, "name", runId + " " + label,
                        "description", "E2E fixture", "level", "BEGINNER"), 201);
        int id = response.path("id").asInt();
        createdRecordIds.add("course:" + id);
        return id;
    }

    private int createOffering(int courseId, String label, LocalDate startDate, BigDecimal price) throws Exception {
        LocalDateTime deadline = startDate.minusDays(1).atTime(23, 59);
        JsonNode response = postJson("/api/course-offerings", tokens.get("admin"),
                Map.of("courseId", courseId, "name", runId + " " + label, "price", price,
                        "plannedSessions", 2, "sessionDuration", 60,
                        "startDate", startDate.toString(), "endDate", startDate.plusDays(2).toString(),
                        "registrationDeadline", deadline.toString(),
                        "cancellationDeadline", deadline.toString()), 201);
        int id = response.path("id").asInt();
        createdRecordIds.add("offering:" + id);
        return id;
    }

    private int createClass(int offeringId, int coachId, String label, int capacity) throws Exception {
        JsonNode response = postJson("/api/classes", tokens.get("admin"),
                Map.of("courseOfferingId", offeringId, "coachId", coachId,
                        "name", runId + " " + label, "capacity", capacity), 201);
        int id = response.path("id").asInt();
        createdRecordIds.add("class:" + id);
        return id;
    }

    private int createSession(int classId, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        JsonNode response = postJson("/api/class-sessions", tokens.get("admin"),
                Map.of("classId", classId, "startTime", startTime.toString(),
                        "endTime", endTime.toString(), "status", "SCHEDULED"), 201);
        return response.path("id").asInt();
    }

    private int createMembershipPlan() throws Exception {
        JsonNode response = postJson("/api/membership-plans", tokens.get("admin"),
                Map.of("name", runId + " discount plan", "description", "E2E fixture",
                        "price", new BigDecimal("100.00"), "durationMonths", 1,
                        "courseDiscountPercent", new BigDecimal("25.5"),
                        "personalTrainingDiscountPercent", BigDecimal.ZERO,
                        "allowPersonalTrainingPlan", false, "allowOneOnOneCoaching", false), 201);
        int id = response.path("id").asInt();
        createdRecordIds.add("membership-plan:" + id);
        return id;
    }

    private JsonNode createCoach(String email, String password) throws Exception {
        JsonNode response = postJson("/api/coaches", tokens.get("admin"),
                Map.of("name", runId + " coach", "email", email, "password", password,
                        "phone", "5550100", "gender", "X"), 201);
        createdRecordIds.add("user:" + response.path("userId").asInt());
        return response;
    }

    private JsonNode createMember(String email, String password) throws Exception {
        return postJson("/api/members", tokens.get("admin"),
                Map.of("name", runId + " member", "email", email, "password", password,
                        "dateOfBirth", LocalDate.now(BUSINESS_ZONE).minusYears(25).toString(),
                        "phone", "5550101", "gender", "X"), 201);
    }

    private JsonNode createUser(String email, String password, String role) throws Exception {
        return postJson("/api/users", tokens.get("admin"),
                Map.of("name", runId + " " + role, "email", email, "password", password,
                        "role", role, "gender", "X"), 201);
    }

    private void assignSport(int coachId, int sportId) throws Exception {
        postJson("/api/coaches/" + coachId + "/sports", tokens.get("admin"),
                Map.of("sportId", sportId, "experienceYears", 2, "certification", "E2E"), 201);
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        JsonNode result = postJson("/api/auth/login", null,
                Map.of("email", email, "password", password), 200);
        assertEquals(email, result.path("email").asText());
        assertNotNull(result.path("accessToken").asText(null));
        return result.path("accessToken").asText();
    }

    private JsonNode postJson(String path, String token, Object body, int expectedStatus) throws Exception {
        HttpResult response = request("POST", path, token, json(body));
        assertEquals(expectedStatus, response.statusCode(), "Unexpected status for POST " + path);
        return response.body();
    }

    private HttpResult postRegistration(String token, int classId) throws Exception {
        return request("POST", "/api/course-registrations", token,
                json(Map.of("classId", classId)));
    }

    private JsonNode getJson(String path, String token) throws Exception {
        HttpResult response = request("GET", path, token, null);
        assertEquals(200, response.statusCode(), "Unexpected status for GET " + path);
        return response.body();
    }

    private HttpResult request(String method, String path, String token, String body) throws Exception {
        var builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        java.net.http.HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "POST" -> builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            case "PATCH" -> builder.method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            default -> throw new IllegalArgumentException("Unsupported E2E HTTP method");
        };
        java.net.http.HttpResponse<String> response = http.send(
                request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode parsed = parseResponse(response.body(), method, path);
        return new HttpResult(response.statusCode(), parsed);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize E2E request");
        }
    }

    private JsonNode parseResponse(String body, String method, String path) {
        if (body.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Non-JSON HTTP response for " + method + " " + path);
        }
    }
    private int registrationCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.course_registration", Integer.class);
    }

    private List<JsonNode> activeRegistrations(int classId) throws Exception {
        String memberSql = "SELECT r.id, r.member_id, r.class_id, r.status, r.original_price, "
                + "r.membership_discount, r.final_price, r.payment_deadline FROM dbo.course_registration r "
                + "WHERE r.class_id = ? AND (r.status = 'CONFIRMED' OR "
                + "(r.status = 'PENDING_PAYMENT' AND r.payment_deadline > ?))";
        List<JsonNode> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(memberSql)) {
            statement.setInt(1, classId);
            statement.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now(BUSINESS_ZONE)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    var row = objectMapper.createObjectNode();
                    row.put("id", result.getInt("id"));
                    row.put("memberId", result.getInt("member_id"));
                    row.put("classId", result.getInt("class_id"));
                    row.put("status", result.getString("status"));
                    if (result.getTimestamp("payment_deadline") != null) {
                        row.put("paymentDeadline", result.getTimestamp("payment_deadline").toLocalDateTime().toString());
                    }
                    row.put("originalPrice", result.getBigDecimal("original_price"));
                    row.put("membershipDiscount", result.getBigDecimal("membership_discount"));
                    row.put("finalPrice", result.getBigDecimal("final_price"));
                    rows.add(row);
                    createdRecordIds.add("registration:" + result.getInt("id"));
                }
            }
        } catch (Exception exception) {
            throw exception;
        }
        return rows;
    }

    private void verifyRegistrationSnapshot(JsonNode api, int memberId, int classId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT member_id, class_id, status, original_price, membership_discount, final_price "
                                + "FROM dbo.course_registration WHERE id = ?")) {
            statement.setInt(1, api.path("id").asInt());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(memberId, result.getInt("member_id"));
                assertEquals(classId, result.getInt("class_id"));
                assertEquals("CONFIRMED", result.getString("status"));
                assertEquals(0, new BigDecimal("999.99").compareTo(result.getBigDecimal("original_price")));
                assertEquals(0, new BigDecimal("255.00").compareTo(result.getBigDecimal("membership_discount")));
                assertEquals(0, new BigDecimal("744.99").compareTo(result.getBigDecimal("final_price")));
                assertFalse(result.next());
            }
        }
    }

    private void confirmCounterCourseRegistration(String memberToken, int registrationId) throws Exception {
        JsonNode payment = postJson("/api/payments", memberToken, Map.of(
                "method", "COUNTER", "purpose", "COURSE_REGISTRATION", "referenceId", registrationId), 201);
        int paymentId = payment.path("id").asInt();
        createdRecordIds.add("payment:" + paymentId);
        JsonNode settled = patchJson("/api/payments/" + paymentId + "/counter", tokens.get("receptionist"),
                Map.of("success", true, "note", "Guarded E2E counter settlement"), 200);
        assertEquals("SUCCESS", settled.path("status").asText());
    }

    private JsonNode patchJson(String path, String token, Object body, int expectedStatus) throws Exception {
        HttpResult response = request("PATCH", path, token, json(body));
        assertEquals(expectedStatus, response.statusCode(), "Unexpected status for PATCH " + path);
        return response.body();
    }

    private boolean containsClass(JsonNode array, int classId) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (item.path("classId").asInt() == classId) return true;
        return false;
    }

    private String createPassword(String label) {
        String password = randomPassword();
        passwords.put(label, password);
        return password;
    }

    private String randomPassword() {
        byte[] bytes = new byte[36];
        RANDOM.nextBytes(bytes);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private String email(String label) {
        return "e2e+" + runId.toLowerCase() + "." + label + "@example.invalid";
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required E2E input: " + name);
        return value;
    }

    @AfterAll
    void clearCredentialReferencesAndReport() {
        // The DB account and fixtures remain as requested; no secret value is printed or persisted.
        passwords.clear();
        tokens.clear();
        adminPassword = null;
        adminEmail = null;
        if (!createdRecordIds.isEmpty()) {
            System.out.println("SportCenter SQL Server E2E fixture registry: " + String.join(", ", createdRecordIds));
        }
    }

    private record HttpResult(int statusCode, JsonNode body) { }
}

