package group5.example.sportCenter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class StaffManagementIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired SportRepository sports;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseClassRepository classes;
    @Autowired JwtService tokens;

    MockMvc mvc;
    User admin;
    User receptionist;
    User member;
    User coachUser;
    Sport sport;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        admin = user("ADMIN");
        receptionist = user("RECEPTIONIST");
        member = user("MEMBER");
        coachUser = user("COACH");
        sport = new Sport();
        sport.setName("Staff test sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);
    }

    @Test
    void adminCreatesCoachAndSpecializationAndCanSafelyDeactivateIt() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String json = "{\"name\":\"New coach\",\"email\":\"" + email
                + "\",\"password\":\"SafePassword123\",\"phone\":\"12345678\",\"specializations\":[{\"sportId\":"
                + sport.getId() + ",\"experienceYears\":5,\"certification\":\"Level A\"}]}";
        String response = mvc.perform(post("/api/staff/coaches").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userName").value("New coach"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer coachId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        Integer userId = com.jayway.jsonpath.JsonPath.read(response, "$.userId");
        org.junit.jupiter.api.Assertions.assertEquals("COACH", users.findById(userId).orElseThrow().getRole());
        org.junit.jupiter.api.Assertions.assertEquals(1, coachSports.findByCoachId(coachId).size());

        Course course = new Course();
        course.setSport(sport);
        course.setName("Assigned course");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);
        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Assigned offering");
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
        courseClass.setCoach(coaches.findById(coachId).orElseThrow());
        courseClass.setName("Existing coach assignment");
        courseClass.setCapacity(10);
        courseClass.setStatus("ACTIVE");
        courseClass = classes.saveAndFlush(courseClass);

        mvc.perform(patch("/api/staff/{id}/status", userId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        org.junit.jupiter.api.Assertions.assertEquals("INACTIVE", coaches.findById(coachId).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(coachId, classes.findById(courseClass.getId()).orElseThrow().getCoach().getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, coachSports.findByCoachId(coachId).size());
    }

    @Test
    void adminCreatesReceptionistAndDuplicateOrInvalidInputIsRejected() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = "{\"name\":\"New receptionist\",\"email\":\"" + email
                + "\",\"password\":\"SafePassword123\"}";
        String created = mvc.perform(post("/api/staff/receptionists").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer receptionistId = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mvc.perform(post("/api/staff/receptionists").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/staff/receptionists").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/staff/receptionists").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second receptionist\",\"email\":\"" + UUID.randomUUID()
                                + "@example.test\",\"password\":\"SafePassword123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("RECEPTIONIST"));

        mvc.perform(put("/api/staff/{id}", receptionistId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated receptionist\",\"email\":\"" + email
                                + "\",\"phone\":\"5551234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated receptionist"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));
        mvc.perform(patch("/api/staff/{id}/status", receptionistId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void onlyAdminCanCreateEditOrChangeStaffStatusAndMemberCannotBeConverted() throws Exception {
        String body = "{\"name\":\"Staff\",\"email\":\"" + UUID.randomUUID()
                + "@example.test\",\"password\":\"SafePassword123\"}";
        String update = "{\"name\":\"Changed\",\"email\":\"" + member.getEmail() + "\"}";
        for (User caller : new User[] { receptionist, member, coachUser }) {
            String token = bearer(caller);
            mvc.perform(post("/api/staff/receptionists").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/staff/coaches").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/staff/{id}", member.getId()).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(update))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/staff/{id}/status", member.getId()).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/users/{id}", member.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Member\",\"email\":\"" + member.getEmail()
                                + "\",\"role\":\"COACH\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    private User user(String role) {
        User user = new User();
        user.setName("Staff test user");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
