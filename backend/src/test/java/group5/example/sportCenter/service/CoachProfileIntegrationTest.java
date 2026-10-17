package group5.example.sportCenter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasItem;

import java.util.UUID;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("phase1")
class CoachProfileIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private CoachRepository coaches;
    @Autowired private CoachSportRepository coachSports;
    @Autowired private SportRepository sports;
    @Autowired private JwtService tokens;
    @Autowired private ObjectMapper mapper;

    private MockMvc mvc;
    private User admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        admin = user("ADMIN", "ACTIVE");
    }

    @Test
    void publicCatalogReturnsOnlySafeProfileFieldsAndActiveSports() throws Exception {
        Sport activeSport = sport("ACTIVE");
        Sport inactiveSport = sport("INACTIVE");
        Coach activeCoach = coach("ACTIVE", "A short public biography");
        assignment(activeCoach, activeSport, 4, "National certificate");
        assignment(activeCoach, inactiveSport, 9, "Hidden inactive sport credential");
        Coach inactiveCoach = coach("INACTIVE", "Do not publish");
        Coach emptyProfileCoach = coach("ACTIVE", null);

        mvc.perform(get("/api/public/coaches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].name").value(activeCoach.getUser().getName()))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].bio").value("A short public biography"))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].sports[0].sportId").value(activeSport.getId()))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].sports[0].sportName").value(activeSport.getName()))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].sports[0].experienceYears").value(4))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].sports[0].certification").value("National certificate"))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].sports.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].email").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + activeCoach.getId() + ")].phone").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + inactiveCoach.getId() + ")]").isEmpty());
        mvc.perform(get("/api/public/coaches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + emptyProfileCoach.getId() + ")].bio").value(hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + emptyProfileCoach.getId() + ")].imageUrl").value(hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + emptyProfileCoach.getId() + ")].sports").value(hasItem(java.util.List.of())));
    }

    @Test
    void adminCanUpdateBioAndOtherRolesCannot() throws Exception {
        Coach coach = coach("ACTIVE", null);
        String body = mapper.writeValueAsString(java.util.Map.of("bio", "Updated biography"));

        mvc.perform(put("/api/coaches/{id}/profile", coach.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(coach.getId()))
                .andExpect(jsonPath("$.bio").value("Updated biography"));

        mvc.perform(put("/api/coaches/{id}/profile", coach.getId())
                        .header("Authorization", bearer(user("RECEPTIONIST", "ACTIVE")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/coaches/{id}/profile", 999999)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/coaches/{id}/profile", coach.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("bio", "x".repeat(1001)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCoachCreationStillCreatesAccountCoachAndSpecializationsWithOptionalBio() throws Exception {
        Sport activeSport = sport("ACTIVE");
        var payload = java.util.Map.of(
                "name", "Created Coach",
                "email", UUID.randomUUID() + "@example.test",
                "password", "safe-test-password",
                "bio", "Created through staff workflow",
                "specializations", java.util.List.of(java.util.Map.of(
                        "sportId", activeSport.getId(), "experienceYears", 3,
                        "certification", "Regional certificate")));

        mvc.perform(post("/api/staff/coaches")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.bio").value("Created through staff workflow"))
                .andExpect(jsonPath("$.email").value(payload.get("email")));
    }

    private Coach coach(String status, String bio) {
        Coach coach = new Coach();
        coach.setUser(user("COACH", "ACTIVE"));
        coach.setStatus(status);
        coach.setBio(bio);
        return coaches.saveAndFlush(coach);
    }

    private User user(String role, String status) {
        User user = new User();
        user.setName(role.equals("COACH") ? "Coach " + UUID.randomUUID() : "Profile " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus(status);
        return users.saveAndFlush(user);
    }

    private Sport sport(String status) {
        Sport sport = new Sport();
        sport.setName("Profile sport " + UUID.randomUUID());
        sport.setStatus(status);
        return sports.saveAndFlush(sport);
    }

    private void assignment(Coach coach, Sport sport, int years, String certification) {
        CoachSport row = new CoachSport();
        row.setCoach(coach);
        row.setSport(sport);
        row.setExperienceYears(years);
        row.setCertification(certification);
        coachSports.saveAndFlush(row);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
