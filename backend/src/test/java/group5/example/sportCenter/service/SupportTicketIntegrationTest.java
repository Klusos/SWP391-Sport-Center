package group5.example.sportCenter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Transactional
class SupportTicketIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private JwtService tokens;
    private MockMvc mvc;
    private User member;
    private User otherMember;
    private User receptionist;
    private User admin;
    private User coach;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        member = user("MEMBER");
        otherMember = user("MEMBER");
        receptionist = user("RECEPTIONIST");
        admin = user("ADMIN");
        coach = user("COACH");
    }

    @Test
    void memberCreatesListsAndReadsOnlyOwnTickets() throws Exception {
        int ticketId = createTicket(member, "Thanh toán khóa học", "PAYMENT");
        createTicket(otherMember, "Gói hội viên", "MEMBERSHIP");

        mvc.perform(get("/api/support-tickets/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/support-tickets/{id}", ticketId).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(0))
                .andExpect(jsonPath("$.description").value("Tôi cần được hỗ trợ."));
        mvc.perform(get("/api/support-tickets/{id}", ticketId + 1).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/support-tickets/{id}/status", ticketId).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/support-tickets").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"   \",\"category\":\"OTHER\",\"description\":\"mô tả\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCanSearchReplyAndManageValidStatusTransitions() throws Exception {
        int ticketId = createTicket(member, "Không vào được lớp", "COURSE");
        mvc.perform(get("/api/support-tickets?search=lớp&category=COURSE&page=0&size=10")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].memberName").value(member.getName()));
        mvc.perform(get("/api/support-tickets?page=0&size=10").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(patch("/api/support-tickets/{id}/assign-to-me", ticketId).header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedStaffId").value(receptionist.getId()));
        mvc.perform(post("/api/support-tickets/{id}/messages", ticketId).header("Authorization", bearer(receptionist))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Chúng tôi đang kiểm tra.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.messages.length()").value(1));
        mvc.perform(patch("/api/support-tickets/{id}/status", ticketId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(patch("/api/support-tickets/{id}/status", ticketId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/support-tickets/{id}/messages", ticketId).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Xin hỗ trợ thêm.\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/support-tickets/{id}/status", ticketId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void memberReplyReopensResolvedTicketAndCoachAndAnonymousAreDenied() throws Exception {
        int ticketId = createTicket(member, "Yêu cầu hỗ trợ", "GENERAL");
        mvc.perform(patch("/api/support-tickets/{id}/status", ticketId).header("Authorization", bearer(receptionist))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/support-tickets/{id}/messages", ticketId).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Vấn đề vẫn còn.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist()).andExpect(jsonPath("$.messages.length()").value(1));
        mvc.perform(get("/api/support-tickets").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/support-tickets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/support-tickets").header("Authorization", bearer(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"x\",\"category\":\"OTHER\",\"description\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    private int createTicket(User owner, String subject, String category) throws Exception {
        String body = "{\"subject\":\"" + subject + "\",\"category\":\"" + category
                + "\",\"description\":\"Tôi cần được hỗ trợ.\"}";
        String response = mvc.perform(post("/api/support-tickets").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return new tools.jackson.databind.ObjectMapper().readTree(response).get("id").intValue();
    }

    private User user(String role) {
        User user = new User();
        user.setName("Support " + role + " " + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(UUID.randomUUID() + "@support.example.test");
        user.setPasswordHash("test-only-unused-hash");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) { return "Bearer " + tokens.generateToken(user); }
}
