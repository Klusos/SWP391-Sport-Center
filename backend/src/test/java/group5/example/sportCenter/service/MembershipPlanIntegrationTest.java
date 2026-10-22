package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("phase1")
@Transactional
class MembershipPlanIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private MembershipPlanRepository plans;
    @Autowired private MembershipRepository memberships;
    @Autowired private JwtService tokens;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void publicCatalogIsAnonymousAndIncludesOnlyActivePlanFields() throws Exception {
        MembershipPlan active = plan("ACTIVE");
        MembershipPlan inactive = plan("INACTIVE");

        mvc.perform(get("/api/public/membership-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].name").exists())
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[0].memberId").doesNotExist());
    }

    @Test
    void receptionistMemberAndCoachMayReadPublicCatalogButNotAdminCatalog() throws Exception {
        plan("ACTIVE");
        for (String role : new String[] {"RECEPTIONIST", "MEMBER", "COACH"}) {
            String token = bearer(user(role));
            mvc.perform(get("/api/public/membership-plans").header("Authorization", token))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/membership-plans").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanReadAllPlansAndCreateEditAndChangeStatus() throws Exception {
        String adminToken = bearer(user("ADMIN"));
        MembershipPlan inactive = plan("INACTIVE");

        mvc.perform(get("/api/membership-plans").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")].status").value("INACTIVE"));
        mvc.perform(get("/api/membership-plans/{id}", inactive.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactive.getId()));

        String createBody = planBody("New Demo Plan", "10.00", 6, "0", "100");
        String response = mvc.perform(post("/api/membership-plans")
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.monthlyPrice").value(10.00))
                .andReturn().getResponse().getContentAsString();
        int createdId = objectMapper.readTree(response).get("id").intValue();

        mvc.perform(put("/api/membership-plans/{id}", createdId)
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content(updateBody("Edited Demo Plan", "12.50", 12, "10", "20", "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited Demo Plan"));

        mvc.perform(patch("/api/membership-plans/{id}/status", createdId)
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\" inactive \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/membership-plans/{id}/status", createdId)
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(patch("/api/membership-plans/{id}/status", createdId)
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void invalidPriceDurationAndDiscountsAreRejected() throws Exception {
        String adminToken = bearer(user("ADMIN"));
        String[] invalidBodies = {
                planBody("Negative Price", "-0.01", 1, "0", "0"),
                planBody("Zero Monthly Price", "0.00", 12, "0", "0"),
                planBody("Large Discount", "10.00", 1, "100.01", "0"),
                planBody("Too Many Decimals", "10.001", 1, "0", "0")
        };
        for (String body : invalidBodies) {
            mvc.perform(post("/api/membership-plans")
                            .header("Authorization", adminToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        mvc.perform(patch("/api/membership-plans/{id}/status", 1)
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upgradeRelationshipsAreDirectionalUniqueAndAcyclic() throws Exception {
        String adminToken = bearer(user("ADMIN"));
        String firstResponse = mvc.perform(post("/api/membership-plans").header("Authorization", adminToken)
                        .contentType("application/json").content(planBody("Source plan", "100.00", 1, "0", "0")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int sourceId = objectMapper.readTree(firstResponse).get("id").intValue();

        String targetBody = planBody("Target plan", "200.00", 1, "0", "0")
                .replace("\"upgradeFromPlanIds\":[]", "\"upgradeFromPlanIds\":[" + sourceId + "]");
        String targetResponse = mvc.perform(post("/api/membership-plans").header("Authorization", adminToken)
                        .contentType("application/json").content(targetBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.upgradeFromPlanIds[0]").value(sourceId))
                .andReturn().getResponse().getContentAsString();
        int targetId = objectMapper.readTree(targetResponse).get("id").intValue();

        String preserveLinksBody = updateBody("Target plan", "200.00", 1, "0", "0", "ACTIVE")
                .replace("\"upgradeFromPlanIds\":[],", "");
        mvc.perform(put("/api/membership-plans/{id}", targetId).header("Authorization", adminToken)
                        .contentType("application/json").content(preserveLinksBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upgradeFromPlanIds[0]").value(sourceId));

        String cycleBody = updateBody("Source plan", "100.00", 1, "0", "0", "ACTIVE")
                .replace("\"upgradeFromPlanIds\":[]", "\"upgradeFromPlanIds\":[" + targetId + "]");
        mvc.perform(put("/api/membership-plans/{id}", sourceId).header("Authorization", adminToken)
                        .contentType("application/json").content(cycleBody))
                .andExpect(status().isConflict());

        String duplicateBody = planBody("Duplicate edge", "300.00", 1, "0", "0")
                .replace("\"upgradeFromPlanIds\":[]", "\"upgradeFromPlanIds\":[" + sourceId + "," + sourceId + "]");
        mvc.perform(post("/api/membership-plans").header("Authorization", adminToken)
                        .contentType("application/json").content(duplicateBody))
                .andExpect(status().isConflict());
    }

    @Test
    void everyNonAdminRoleAndGuestIsBlockedFromMutations() throws Exception {
        MembershipPlan plan = plan("ACTIVE");
        String body = updateBody(plan.getName(), "20.00", 12, "0", "0", "ACTIVE");
        String[] headers = {null, bearer(user("RECEPTIONIST")), bearer(user("MEMBER")), bearer(user("COACH"))};
        for (String token : headers) {
            var postRequest = post("/api/membership-plans").contentType("application/json").content(planBody("Blocked", "1", 1, "0", "0"));
            var patchRequest = patch("/api/membership-plans/{id}/status", plan.getId())
                    .contentType("application/json").content("{\"status\":\"INACTIVE\"}");
            var putRequest = put("/api/membership-plans/{id}", plan.getId())
                    .contentType("application/json").content(body);
            var deleteRequest = delete("/api/membership-plans/{id}", plan.getId());
            if (token != null) {
                postRequest.header("Authorization", token);
                patchRequest.header("Authorization", token);
                putRequest.header("Authorization", token);
                deleteRequest.header("Authorization", token);
            }
            int deniedStatus = token == null ? 401 : 403;
            mvc.perform(postRequest).andExpect(status().is(deniedStatus));
            mvc.perform(patchRequest).andExpect(status().is(deniedStatus));
            mvc.perform(putRequest).andExpect(status().is(deniedStatus));
            mvc.perform(deleteRequest).andExpect(status().is(deniedStatus));
        }
        assertEquals("ACTIVE", plans.findById(plan.getId()).orElseThrow().getStatus());
    }

    @Test
    void deleteDeactivatesPlanAndPreservesMembershipReferences() throws Exception {
        User member = user("MEMBER");
        MembershipPlan plan = plan("ACTIVE");
        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setAutoRenew(false);
        membership.setStartDate(java.time.LocalDate.now());
        membership.setEndDate(java.time.LocalDate.now().plusMonths(6));
        membership.setStatus("ACTIVE");
        membership = memberships.saveAndFlush(membership);
        Integer membershipId = membership.getId();
        Integer planId = plan.getId();

        mvc.perform(delete("/api/membership-plans/{id}", planId)
                        .header("Authorization", bearer(user("ADMIN"))))
                .andExpect(status().isNoContent());

        assertEquals("INACTIVE", plans.findById(planId).orElseThrow().getStatus());
        Membership preserved = memberships.findById(membershipId).orElseThrow();
        assertEquals(planId, preserved.getPlan().getId());
        assertTrue(preserved.getId() != null);
        assertFalse(memberships.findByPlanId(planId).isEmpty());
    }

    private MembershipPlan plan(String status) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName("Test plan " + UUID.randomUUID());
        plan.setDescription("A membership plan used for integration tests.");
        plan.setPrice(new BigDecimal("25.00"));
        plan.setMonthlyPrice(new BigDecimal("25.00"));
        plan.setDurationMonths(3);
        plan.setCourseDiscountPercent(new BigDecimal("5.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("10.00"));
        plan.setAllowPersonalTrainingPlan(true);
        plan.setAllowOneOnOneCoaching(false);
        plan.setStatus(status);
        return plans.saveAndFlush(plan);
    }

    private User user(String role) {
        User user = new User();
        user.setName("Membership plan test " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private String planBody(String name, String price, int months, String courseDiscount, String trainingDiscount) {
        return "{\"name\":\"" + name + "\",\"description\":\"Test plan\",\"monthlyPrice\":" + price
                + ",\"upgradeFromPlanIds\":[],\"courseDiscountPercent\":" + courseDiscount
                + ",\"personalTrainingDiscountPercent\":" + trainingDiscount
                + ",\"allowPersonalTrainingPlan\":true,\"allowOneOnOneCoaching\":false}";
    }

    private String updateBody(String name, String price, int months, String courseDiscount,
            String trainingDiscount, String status) {
        return planBody(name, price, months, courseDiscount, trainingDiscount)
                .replace("}", ",\"status\":\"" + status + "\"}");
    }
}
