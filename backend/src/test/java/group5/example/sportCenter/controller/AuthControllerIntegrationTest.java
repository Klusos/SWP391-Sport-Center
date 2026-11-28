package group5.example.sportCenter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:default-mvc-json;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.payment.callback-secret=auth-test-payment-callback-secret"
})
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy security;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void anonymousJsonLoginSucceedsWithoutAuthorizationHeader() throws Exception {
        String email = "login-test-" + UUID.randomUUID() + "@example.test";
        String password = "test-only-password";

        User user = new User();
        user.setName("Login Integration Test");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("RECEPTIONIST");
        user.setStatus("ACTIVE");
        users.saveAndFlush(user);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void invalidBearerHeaderIsRejectedEvenThoughLoginIsPublic() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"irrelevant\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void failedJsonLoginReturnsSerializableJsonError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-" + UUID.randomUUID()
                                + "@example.test\",\"password\":\"irrelevant\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void swaggerResourcesArePublicButProtectedOperationsRetainAuthenticationAndRoleChecks() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SportCenter API"))
                .andExpect(jsonPath("$.info.version").value("1.0"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").exists());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        String email = "swagger-member-" + UUID.randomUUID() + "@example.test";
        String password = "test-only-password";
        User user = new User();
        user.setName("Swagger Security Test");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        users.saveAndFlush(user);

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
