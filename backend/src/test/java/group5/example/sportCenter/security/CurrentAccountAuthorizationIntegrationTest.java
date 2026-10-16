package group5.example.sportCenter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("phase1")
@Transactional
class CurrentAccountAuthorizationIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private JwtService tokens;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void activeAdminCanPerformAdminMutation() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        User target = user("MEMBER", "ACTIVE");

        mvc.perform(put("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(updateBody(target)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedAdminOldTokenIsDenied() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        User target = user("MEMBER", "ACTIVE");
        String oldToken = bearer(admin);
        admin.setStatus("INACTIVE");
        users.saveAndFlush(admin);

        mvc.perform(put("/api/users/{id}", target.getId())
                        .header("Authorization", oldToken)
                        .contentType("application/json")
                        .content(updateBody(target)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("The account is inactive"));
    }

    @Test
    void demotedAdminOldTokenUsesCurrentRole() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        User target = user("MEMBER", "ACTIVE");
        String oldToken = bearer(admin);
        admin.setRole("RECEPTIONIST");
        users.saveAndFlush(admin);

        mvc.perform(put("/api/users/{id}", target.getId())
                        .header("Authorization", oldToken)
                        .contentType("application/json")
                        .content(updateBody(target)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedAccountOldTokenIsDenied() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        User target = user("MEMBER", "ACTIVE");
        String oldToken = bearer(admin);
        users.delete(admin);
        users.flush();

        mvc.perform(put("/api/users/{id}", target.getId())
                        .header("Authorization", oldToken)
                        .contentType("application/json")
                        .content(updateBody(target)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("The authenticated account no longer exists"));
    }

    @Test
    void activeMemberCannotPerformAdminMutation() throws Exception {
        User member = user("MEMBER", "ACTIVE");
        User target = user("MEMBER", "ACTIVE");

        mvc.perform(put("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(member))
                        .contentType("application/json")
                        .content(updateBody(target)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberStillCanViewOnlyOwnMemberships() throws Exception {
        User member = user("MEMBER", "ACTIVE");
        User otherMember = user("MEMBER", "ACTIVE");
        String token = bearer(member);

        mvc.perform(get("/api/memberships/member/{id}", member.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/memberships/member/{id}", otherMember.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    private User user(String role, String status) {
        User user = new User();
        user.setName("Authorization test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus(status);
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private String updateBody(User target) throws Exception {
        return objectMapper.writeValueAsString(new UserUpdate(
                "Updated test member",
                target.getEmail(),
                "MEMBER",
                "ACTIVE"));
    }

    private record UserUpdate(String name, String email, String role, String status) { }
}
