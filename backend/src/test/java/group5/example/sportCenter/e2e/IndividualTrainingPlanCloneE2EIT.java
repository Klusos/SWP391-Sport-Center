package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import group5.example.sportCenter.repository.MembershipRepository;

/**
 * Opt-in SQL Server clone E2E. Accounts/classes must be owner-provisioned and
 * explicitly marked; the test writes only individual-plan API data in a test
 * transaction, which is rolled back after each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=" + CloneDatasourceSafetyGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sportcenter.scheduling.enabled=false"
})
@ContextConfiguration(initializers = CloneDatasourceSafetyGuard.class)
class IndividualTrainingPlanCloneE2EIT {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String BASE = "/api/training";

    @Autowired WebApplicationContext webContext;
    @Autowired FilterChainProxy security;
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;
    @Autowired MembershipRepository memberships;

    private MockMvc mvc;
    private String marker;
    private Credentials coach;
    private Credentials member;
    private Credentials otherMember;
    private Credentials unassignedCoach;
    private Credentials expiredMember;
    private Integer createdPlanId;

    @BeforeEach
    void verifyCloneAndLoadOwnerProvisionedCredentials() throws Exception {
        // This must precede any fixture mutation. The test performs no fixture provisioning.
        CloneDatasourceSafetyGuard.verifyRuntime(dataSource, environment, applicationContext);
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(security).build();
        marker = required("SPORTCENTER_ITP_E2E_MARKER");
        if (!marker.matches("[A-Za-z0-9_-]{6,40}")) {
            throw new IllegalStateException("SPORTCENTER_ITP_E2E_MARKER must be a 6-40 character safe marker");
        }
        coach = credentials("COACH");
        member = credentials("MEMBER");
        otherMember = credentials("OTHER_MEMBER");
        unassignedCoach = credentials("UNASSIGNED_COACH");
        expiredMember = credentials("EXPIRED_MEMBER");
    }

    @Test
    @Transactional
    void coachAndMemberWorkflowUsesMarkedQualifiedFixturesAndRollsBackPlanWrites() throws Exception {
        JsonNode coachLogin = login(coach);
        JsonNode memberLogin = login(member);
        JsonNode otherMemberLogin = login(otherMember);
        JsonNode unassignedCoachLogin = login(unassignedCoach);
        int memberId = memberLogin.path("userId").asInt();
        assertTrue(memberId > 0, "Member login must identify the pre-provisioned Member");
        assertTrue(memberLogin.path("name").asText().contains(marker), "Member fixture must carry the E2E marker in its name");
        assertTrue(otherMemberLogin.path("name").asText().contains(marker), "Other Member fixture must carry the E2E marker in its name");
        assertTrue(coachLogin.path("name").asText().contains(marker), "Coach fixture must carry the E2E marker in its name");
        assertTrue(unassignedCoachLogin.path("name").asText().contains(marker), "Unassigned Coach fixture must carry the E2E marker in its name");

        String className = marker + "-ITP-ACTIVE";
        JsonNode classList = json(authenticatedGet(BASE + "/classes", coachLogin), 200);
        JsonNode targetClass = findBy(classList, "className", className);
        int classId = targetClass.path("classId").asInt();
        assertTrue(classId > 0, "Expected exactly one owner-provisioned Coach class marked " + className);
        JsonNode progress = json(authenticatedGet(BASE + "/classes/" + classId + "/progress", coachLogin), 200);
        assertTrue(findInt(progress, "memberId", memberId) != null,
                "Target Member must have a confirmed CourseRegistration in the marked class");
        assertTrue(memberships.findEffectivePersonalTrainingPlanMemberships(
                memberId, LocalDate.now(clock.withZone(BUSINESS_ZONE))).size() > 0,
                "Target Member must have a currently effective Membership with allowPersonalTrainingPlan=true");

        JsonNode existing = json(authenticatedGet(BASE + "/classes/" + classId + "/individual-plans", coachLogin), 200);
        assertEquals(0, existing.size(), "Marked fixture must not already have an individual plan");
        JsonNode unassignedClasses = json(authenticatedGet(BASE + "/classes", unassignedCoachLogin), 200);
        assertFalse(hasTextField(unassignedClasses, "className", className),
                "Unassigned Coach must not own the marked class");

        String createPath = BASE + "/classes/" + classId + "/members/" + memberId + "/individual-plan";
        String firstBody = planBody("Initial " + marker, "Initial description", "Week 1", "Mobility drill");
        MvcResult created = mvc.perform(post(createPath).header("Authorization", bearer(coachLogin))
                        .contentType("application/json").content(firstBody))
                .andReturn();
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        JsonNode createdBody = mapper.readTree(created.getResponse().getContentAsString());
        createdPlanId = createdBody.path("id").asInt();
        assertTrue(createdPlanId > 0);
        assertEquals(memberId, createdBody.path("memberId").asInt());
        assertEquals(classId, createdBody.path("classId").asInt());
        assertEquals(1, createdBody.path("currentRevision").path("revisionNumber").asInt());
        assertEquals("Mobility drill", createdBody.path("currentRevision").path("weeks").get(0)
                .path("exercises").get(0).path("name").asText());

        String updateBody = planBody("Updated " + marker, "Updated description", "Week 1 revised", "Balance drill");
        mvc.perform(put(BASE + "/individual-plans/" + createdPlanId)
                        .header("Authorization", bearer(coachLogin)).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision.revisionNumber").value(2))
                .andExpect(jsonPath("$.currentRevision.weeks[0].exercises[0].name").value("Balance drill"));
        mvc.perform(get(BASE + "/individual-plans/" + createdPlanId).header("Authorization", bearer(coachLogin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.goal").value("Updated " + marker));
        JsonNode history = json(authenticatedGet(BASE + "/individual-plans/" + createdPlanId + "/revisions", memberLogin), 200);
        assertEquals(2, history.size());
        assertEquals(2, history.get(0).path("revisionNumber").asInt());
        assertEquals("Initial " + marker, history.get(1).path("goal").asText(), "Revision 1 must remain immutable");
        assertEquals("Mobility drill", history.get(1).path("weeks").get(0).path("exercises").get(0).path("name").asText());

        mvc.perform(post(createPath).header("Authorization", bearer(coachLogin))
                        .contentType("application/json").content(firstBody))
                .andExpect(status().isConflict());
        JsonNode memberPlans = json(authenticatedGet(BASE + "/me/individual-plans", memberLogin), 200);
        assertTrue(findInt(memberPlans, "id", createdPlanId) != null,
                "Member list must include the newly created plan");
        mvc.perform(get(BASE + "/individual-plans/" + createdPlanId).header("Authorization", bearer(memberLogin)))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/individual-plans/" + createdPlanId + "/revisions")
                        .header("Authorization", bearer(memberLogin))).andExpect(status().isOk());
        mvc.perform(post(createPath).header("Authorization", bearer(memberLogin))
                        .contentType("application/json").content(firstBody)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/individual-plans/" + createdPlanId).header("Authorization", bearer(memberLogin))
                        .contentType("application/json").content(firstBody)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/individual-plans/" + createdPlanId).header("Authorization", bearer(otherMemberLogin)))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/classes/" + classId + "/individual-plans")
                        .header("Authorization", bearer(unassignedCoachLogin))).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/individual-plans/" + createdPlanId)
                        .header("Authorization", bearer(unassignedCoachLogin))
                        .contentType("application/json").content(firstBody)).andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void expiredMarkedMembershipBlocksCoachWritesButKeepsHistoricalMemberRead() throws Exception {
        JsonNode coachLogin = login(coach);
        JsonNode expiredMemberLogin = login(expiredMember);
        int memberId = expiredMemberLogin.path("userId").asInt();
        assertTrue(expiredMemberLogin.path("name").asText().contains(marker));
        assertTrue(memberships.findEffectivePersonalTrainingPlanMemberships(
                memberId, LocalDate.now(clock.withZone(BUSINESS_ZONE))).isEmpty(),
                "Expired fixture must not have current qualifying Membership eligibility");
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        assertTrue(memberships.findByMemberId(memberId).stream().anyMatch(membership ->
                        Boolean.TRUE.equals(membership.getPlan().getAllowPersonalTrainingPlan())
                                && membership.getEndDate() != null && !membership.getEndDate().isAfter(today)),
                "Expired fixture must include a qualifying Membership whose exclusive endDate has passed");

        JsonNode ownPlans = json(authenticatedGet(BASE + "/me/individual-plans", expiredMemberLogin), 200);
        String historicalClassName = marker + "-ITP-EXPIRED";
        JsonNode historical = findBy(ownPlans, "className", historicalClassName);
        JsonNode coachClasses = json(authenticatedGet(BASE + "/classes", coachLogin), 200);
        assertTrue(hasTextField(coachClasses, "className", historicalClassName),
                "The tested Coach must be assigned to the expired plan's class");
        int planId = historical.path("id").asInt();
        assertTrue(planId > 0, "Pre-provisioned expired fixture must have a historical plan in " + historicalClassName);
        mvc.perform(get(BASE + "/individual-plans/" + planId).header("Authorization", bearer(expiredMemberLogin)))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/individual-plans/" + planId + "/revisions")
                        .header("Authorization", bearer(expiredMemberLogin))).andExpect(status().isOk());
        mvc.perform(put(BASE + "/individual-plans/" + planId).header("Authorization", bearer(coachLogin))
                        .contentType("application/json")
                        .content(planBody("Must be rejected", "", "Week", "Exercise")))
                .andExpect(status().isForbidden());
    }

    @AfterTransaction
    void createdPlanWasRolledBack() throws Exception {
        if (createdPlanId == null) return;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM dbo.individual_training_plan WHERE id = ?")) {
            statement.setInt(1, createdPlanId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1), "Test-created plan must not persist after rollback");
            }
        } finally {
            createdPlanId = null;
        }
    }

    private JsonNode login(Credentials credentials) throws Exception {
        return json(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", credentials.email(),
                        "password", credentials.password()))), 200);
    }

    private JsonNode json(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(expectedStatus, result.getResponse().getStatus(), "Unexpected API status: " + result.getResponse().getContentAsString());
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedGet(String path, JsonNode login) {
        return get(path).header("Authorization", bearer(login));
    }
    private static String bearer(JsonNode login) { return "Bearer " + login.path("accessToken").asText(); }
    private static String planBody(String goal, String description, String title, String exercise) {
        return "{\"goal\":\"" + escape(goal) + "\",\"description\":\"" + escape(description)
                + "\",\"weeks\":[{\"weekNumber\":1,\"title\":\"" + escape(title)
                + "\",\"description\":\"Week detail\",\"exercises\":[{\"name\":\"" + escape(exercise)
                + "\",\"sets\":3,\"reps\":10,\"durationMinutes\":null,\"notes\":\"E2E\",\"sortOrder\":0}]}]}";
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private Credentials credentials(String role) {
        return new Credentials(required("SPORTCENTER_ITP_E2E_" + role + "_EMAIL"),
                required("SPORTCENTER_ITP_E2E_" + role + "_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required local E2E environment variable: " + name);
        return value;
    }
    private static JsonNode findBy(JsonNode array, String field, String expected) {
        assertTrue(array.isArray(), "Expected the documented list response to be a JSON array");
        JsonNode match = null;
        for (JsonNode item : array) {
            if (expected.equals(item.path(field).asText())) {
                assertTrue(match == null, "Fixture marker must identify exactly one item: " + expected);
                match = item;
            }
        }
        assertNotNull(match, "Missing owner-provisioned fixture marked " + expected);
        return match;
    }
    private static Integer findInt(JsonNode array, String field, int expected) {
        assertTrue(array.isArray(), "Expected the documented list response to be a JSON array");
        for (JsonNode item : array) if (item.path(field).asInt() == expected) return expected;
        return null;
    }
    private static boolean hasTextField(JsonNode node, String field, String expected) {
        if (node.isObject() && expected.equals(node.path(field).asText())) return true;
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) if (hasTextField(child, field, expected)) return true;
        }
        return false;
    }
    private record Credentials(String email, String password) { }
}
