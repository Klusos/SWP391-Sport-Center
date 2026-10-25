package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.AuditLog;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("phase1")
@Transactional
class AuditLogIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @MockitoSpyBean private AuditLogRepository auditLogs;
    @Autowired private UserRepository users;
    @Autowired private JwtService tokens;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationRepository notifications;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void userCreationAndRealRoleAndStatusChangesAreAuditedWithoutSensitiveValues() throws Exception {
        User admin = user("ADMIN");
        String adminToken = bearer(admin);
        String email = UUID.randomUUID() + "@example.test";
        String createBody = "{\"name\":\"New Staff\",\"email\":\"" + email
                + "\",\"password\":\"do-not-log-this\",\"role\":\"COACH\"}";
        String created = mvc.perform(post("/api/users").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(created).get("id").intValue();
        assertEquals(1, auditLogs.count());
        AuditLog creation = auditLogs.findAll().get(0);
        assertEquals(admin.getId(), creation.getActorUserId());
        assertEquals("ADMIN", creation.getActorRole());
        assertEquals("CREATE", creation.getAction());
        assertEquals("USER", creation.getEntityType());
        org.junit.jupiter.api.Assertions.assertFalse(creation.getDescription().contains(email));
        org.junit.jupiter.api.Assertions.assertFalse(creation.getDescription().contains("do-not-log-this"));

        mvc.perform(put("/api/users/{id}", id).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Staff\",\"email\":\"" + email
                                + "\",\"role\":\"RECEPTIONIST\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals(3, auditLogs.count());
        assertEquals(1, auditLogs.count((root, query, cb) -> cb.equal(root.get("action"), "ROLE_CHANGE")));
        assertEquals(1, auditLogs.count((root, query, cb) -> cb.equal(root.get("action"), "STATUS_CHANGE")));

        mvc.perform(put("/api/users/{id}", id).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Staff\",\"email\":\"" + email
                                + "\",\"role\":\"RECEPTIONIST\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals(3, auditLogs.count(), "unchanged role and status do not create log entries");
    }

    @Test
    void onlyAdminCanReadPagedAndFilteredAuditLogs() throws Exception {
        User admin = user("ADMIN");
        auditLogs.insert(entry(admin, "CREATE", "USER", "7", "Account created."));
        auditLogs.insert(entry(admin, "STATUS_CHANGE", "MEMBERSHIP_PLAN", "7", "Plan status changed."));

        mvc.perform(get("/api/admin/audit-logs?page=0&size=1&entityType=USER")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].entityType").value("USER"))
                .andExpect(jsonPath("$.content[0].actorUserId").value(admin.getId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        for (String role : new String[] {"RECEPTIONIST", "COACH", "MEMBER"}) {
            mvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(user(role))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit-logs?from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/audit-logs").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/audit-logs/1").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dateRangeUsesInclusiveFromAndExclusiveToBoundaries() throws Exception {
        User admin = user("ADMIN");
        AuditLog atFrom = entry(admin, "CREATE", "USER", "from", "At lower boundary.");
        atFrom.setCreatedAt(java.time.LocalDateTime.parse("2026-10-01T00:00:00"));
        auditLogs.insert(atFrom);
        AuditLog atTo = entry(admin, "CREATE", "USER", "to", "At upper boundary.");
        atTo.setCreatedAt(java.time.LocalDateTime.parse("2026-10-02T00:00:00"));
        auditLogs.insert(atTo);

        mvc.perform(get("/api/admin/audit-logs?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entityId").value("from"));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackUserCreation() throws Exception {
        User admin = user("ADMIN");
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditLogs).insert(org.mockito.ArgumentMatchers.any(AuditLog.class));

        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/users").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rollback\",\"email\":\"" + email
                                + "\",\"password\":\"secret\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isInternalServerError());
        org.junit.jupiter.api.Assertions.assertFalse(users.existsByEmail(email));
    }

    @Test
    void successfulBroadcastCreatesOneAuditRecordAndAuditFailureRollsBackNotifications() throws Exception {
        User admin = user("ADMIN");
        user("MEMBER");
        int expectedRecipients = users.findEligibleActiveUserIds().size();
        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notice\",\"message\":\"Private full body not copied to audit\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientCount").value(expectedRecipients));
        assertEquals(expectedRecipients, notifications.count());
        assertEquals(1, auditLogs.count());
        assertEquals("BROADCAST", auditLogs.findAll().get(0).getAction());
        org.junit.jupiter.api.Assertions.assertFalse(auditLogs.findAll().get(0).getDescription().contains("Private full body"));

        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditLogs).insert(org.mockito.ArgumentMatchers.any(AuditLog.class));
        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second notice\",\"message\":\"Text\"}"))
                .andExpect(status().isInternalServerError());
        assertEquals(expectedRecipients, notifications.count(), "failed broadcast must not add recipient rows");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackBroadcastNotifications() throws Exception {
        User admin = user("ADMIN");
        user("MEMBER");
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditLogs).insert(org.mockito.ArgumentMatchers.any(AuditLog.class));

        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Rollback notice\",\"message\":\"Text\"}"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, notifications.count(), "notification rows must roll back with the failed audit insert");
        assertEquals(0, auditLogs.count());
    }

    @Test
    void membershipPlanCreateUpdateStatusAndLegacyDeleteAreAuditedOnlyOnActualChanges() throws Exception {
        User admin = user("ADMIN");
        String token = bearer(admin);
        String createBody = "{\"name\":\"Audit Plan\",\"description\":\"Initial\",\"price\":10,"
                + "\"durationMonths\":1,\"courseDiscountPercent\":0,\"personalTrainingDiscountPercent\":0,"
                + "\"allowPersonalTrainingPlan\":false,\"allowOneOnOneCoaching\":false}";
        String response = mvc.perform(post("/api/membership-plans").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int planId = objectMapper.readTree(response).get("id").intValue();
        assertEquals(1, auditLogs.count());

        String updateBody = "{\"name\":\"Audit Plan Updated\",\"description\":\"Changed\",\"price\":10,"
                + "\"durationMonths\":1,\"courseDiscountPercent\":0,\"personalTrainingDiscountPercent\":0,"
                + "\"allowPersonalTrainingPlan\":false,\"allowOneOnOneCoaching\":false,\"status\":\"ACTIVE\"}";
        mvc.perform(put("/api/membership-plans/{id}", planId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());
        assertEquals(2, auditLogs.count());
        mvc.perform(put("/api/membership-plans/{id}", planId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());
        assertEquals(2, auditLogs.count(), "an unchanged plan update must not add audit rows");
        mvc.perform(patch("/api/membership-plans/{id}/status", planId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/membership-plans/{id}/status", planId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/membership-plans/{id}", planId).header("Authorization", token))
                .andExpect(status().isNoContent());
        assertEquals(5, auditLogs.count());
        assertEquals(1, auditLogs.count((root, query, cb) -> cb.equal(root.get("action"), "CREATE")));
        assertEquals(1, auditLogs.count((root, query, cb) -> cb.equal(root.get("action"), "UPDATE")));
        assertEquals(3, auditLogs.count((root, query, cb) -> cb.equal(root.get("action"), "STATUS_CHANGE")));
    }

    private AuditLog entry(User actor, String action, String type, String id, String description) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actor.getId());
        entry.setActorRole(actor.getRole());
        entry.setAction(action);
        entry.setEntityType(type);
        entry.setEntityId(id);
        entry.setDescription(description);
        entry.setCreatedAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));
        return entry;
    }

    private User user(String role) {
        User user = new User();
        user.setName("Audit test " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-only-unused-hash");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
