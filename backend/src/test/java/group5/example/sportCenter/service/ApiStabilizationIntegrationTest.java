package group5.example.sportCenter.service;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.MemberProfile;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.MemberProfileRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("phase1")
class ApiStabilizationIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private SportRepository sports;
    @Autowired private CoachRepository coaches;
    @Autowired private CoachSportRepository coachSports;
    @Autowired private CourseRepository courses;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private CourseClassRepository classes;
    @Autowired private FacilityRepository facilities;
    @Autowired private MemberProfileRepository profiles;
    @Autowired private JwtService tokens;
    @MockitoSpyBean private CourseService courseService;

    private MockMvc mvc;
    private User admin;
    private User member;
    private User receptionist;
    private Sport sport;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        admin = user("ADMIN");
        member = user("MEMBER");
        receptionist = user("RECEPTIONIST");
        sport = sport("Consistency Test Sport " + UUID.randomUUID());
    }

    @Test
    void relationshipBackedDtosMapWithOpenSessionInViewDisabled() throws Exception {
        User coachUser = user("COACH");
        Coach coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coaches.saveAndFlush(coach);

        CoachSport qualification = new CoachSport();
        qualification.setCoach(coach);
        qualification.setSport(sport);
        qualification.setExperienceYears(3);
        qualification.setCertification("Test certification");
        coachSports.saveAndFlush(qualification);

        Course course = new Course();
        course.setSport(sport);
        course.setName("Lazy mapping course");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Lazy mapping offering");
        offering.setPrice(new BigDecimal("50.00"));
        offering.setSessionDuration(60);
        offering.setStartDate(LocalDate.now().plusDays(10));
        offering.setEndDate(LocalDate.now().plusDays(20));
        offering.setRegistrationDeadline(LocalDate.now().plusDays(9).atStartOfDay());
        offering.setCancellationDeadline(LocalDate.now().plusDays(8).atStartOfDay());
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);

        CourseClass courseClass = new CourseClass();
        courseClass.setCourseOffering(offering);
        courseClass.setCoach(coach);
        courseClass.setName("Lazy mapping class");
        courseClass.setCapacity(12);
        courseClass.setStatus("ACTIVE");
        courseClass = classes.saveAndFlush(courseClass);

        Facility facility = new Facility();
        facility.setSport(sport);
        facility.setName("Lazy mapping facility");
        facility.setType("Studio");
        facility.setStatus("ACTIVE");
        facility = facilities.saveAndFlush(facility);

        MemberProfile profile = new MemberProfile();
        profile.setMember(member);
        profile.setHeight(new BigDecimal("170.00"));
        profile.setWeight(new BigDecimal("65.00"));
        profile.setGoal("Build stamina");
        profile = profiles.saveAndFlush(profile);

        String adminToken = bearer(admin);
        mvc.perform(get("/api/coaches").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userName").exists());
        mvc.perform(get("/api/coaches/{id}", coach.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(coachUser.getEmail()));
        mvc.perform(get("/api/coaches/{id}/sports", coach.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sportName").value(sport.getName()));
        mvc.perform(get("/api/member-profiles/{id}", profile.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberName").value(member.getName()));
        mvc.perform(get("/api/member-profiles").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.memberId == " + member.getId() + ")]").exists());
        mvc.perform(get("/api/member-profiles").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.memberId == " + member.getId() + ")]").exists());
        mvc.perform(get("/api/member-profiles/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.goal").value("Build stamina"));
        mvc.perform(get("/api/courses").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sportName").exists());
        mvc.perform(get("/api/course-offerings/{id}", offering.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sportName").value(sport.getName()));
        mvc.perform(get("/api/classes/{id}", courseClass.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coachName").value(coachUser.getName()));
        mvc.perform(get("/api/facilities/{id}", facility.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sportName").value(sport.getName()));
    }

    @Test
    void receptionistCanReadProfilesButCannotCreateOrEditThem() throws Exception {
        MemberProfile profile = new MemberProfile();
        profile.setMember(member);
        profile.setHeight(new BigDecimal("170.00"));
        profile.setWeight(new BigDecimal("65.00"));
        profile.setGoal("Read-only profile");
        profile = profiles.saveAndFlush(profile);

        String token = bearer(receptionist);
        mvc.perform(get("/api/member-profiles").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.memberId == " + member.getId() + " && @.goal == 'Read-only profile')]").exists());

        mvc.perform(get("/api/member-profiles/{id}", profile.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/member-profiles/member/{memberId}", member.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/member-profiles").header("Authorization", token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/member-profiles/{id}", profile.getId()).header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"height\":171,\"weight\":66,\"goal\":\"Changed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageMemberFieldsAndDeactivateWithoutDeletingHistory() throws Exception {
        MemberProfile profile = new MemberProfile();
        profile.setMember(member);
        profile.setHeight(new BigDecimal("170.00"));
        profile.setWeight(new BigDecimal("65.00"));
        profile.setGoal("Initial goal");
        profile = profiles.saveAndFlush(profile);

        String adminToken = bearer(admin);
        String receptionistToken = bearer(receptionist);
        String accountBody = "{\"name\":\"Updated member\",\"email\":\"" + member.getEmail()
                + "\",\"dateOfBirth\":\"2000-01-01\",\"gender\":\"F\",\"phone\":\"1234567890\"}";

        mvc.perform(put("/api/members/{id}", member.getId()).header("Authorization", receptionistToken)
                        .contentType("application/json").content(accountBody))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/members/{id}/deactivate", member.getId()).header("Authorization", receptionistToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/member-profiles/{id}", profile.getId()).header("Authorization", receptionistToken)
                        .contentType("application/json")
                        .content("{\"height\":171,\"weight\":66,\"goal\":\"Changed\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/members/{id}", member.getId()).header("Authorization", adminToken)
                        .contentType("application/json").content(accountBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated member"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(put("/api/member-profiles/{id}", profile.getId()).header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("{\"height\":171,\"weight\":66,\"goal\":\"Updated goal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.goal").value("Updated goal"));
        mvc.perform(delete("/api/users/{id}", member.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/members/{id}/deactivate", member.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        User persisted = users.findById(member.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("MEMBER", persisted.getRole());
        org.junit.jupiter.api.Assertions.assertEquals("INACTIVE", persisted.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(profiles.existsById(profile.getId()));
    }

    @Test
    void adminAndReceptionistCanCreateMemberThroughExistingRegistrationFlow() throws Exception {
        String json = "{\"name\":\"New member\",\"email\":\"" + UUID.randomUUID()
                + "@example.test\",\"password\":\"SafeTestPassword123\",\"dateOfBirth\":\"2000-01-01\"}";
        mvc.perform(post("/api/members").header("Authorization", bearer(admin))
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/members").header("Authorization", bearer(receptionist))
                        .contentType("application/json").content("{\"name\":\"Another member\",\"email\":\""
                                + UUID.randomUUID() + "@example.test\",\"password\":\"SafeTestPassword123\",\"dateOfBirth\":\"2000-01-01\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void validationConflictMissingAndResponseStatusUseOneJsonShape() throws Exception {
        String token = bearer(admin);
        mvc.perform(post("/api/sports").header("Authorization", token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Sport name is required")))
                .andExpect(jsonPath("$.timestamp").exists());

        mvc.perform(post("/api/sports").header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"" + sport.getName() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Sport name already exists"))
                .andExpect(jsonPath("$.timestamp").exists());

        mvc.perform(get("/api/courses/2147483000").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Course not found")))
                .andExpect(jsonPath("$.timestamp").exists());

        Facility facility = new Facility();
        facility.setSport(sport);
        facility.setName("Status validation facility");
        facility.setType("Studio");
        facility.setStatus("ACTIVE");
        facility = facilities.saveAndFlush(facility);
        mvc.perform(put("/api/facilities/{id}", facility.getId()).header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"Studio\",\"type\":\"Studio\",\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Facility status must be ACTIVE or INACTIVE"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedServerErrorsReturnOnlyGenericSafeMessage() throws Exception {
        doThrow(new IllegalStateException("SQL password and internal table details"))
                .when(courseService).getCourseById(2147483000);

        String body = mvc.perform(get("/api/courses/2147483000")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("SQL password"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("IllegalStateException"));
    }

    @Test
    void unauthenticatedAndForbiddenRequestsReturnJsonErrors() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.timestamp").exists());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Authentication is required or the access token is invalid"));

        mvc.perform(post("/api/users").header("Authorization", bearer(member))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You are not allowed to perform this action"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private User user(String role) {
        User user = new User();
        user.setName("API consistency user");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private Sport sport(String name) {
        Sport sport = new Sport();
        sport.setName(name);
        sport.setStatus("ACTIVE");
        return sports.saveAndFlush(sport);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
