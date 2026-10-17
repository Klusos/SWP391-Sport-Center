package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateCourseRequest;
import group5.example.sportCenter.dto.CreateSportRequest;
import group5.example.sportCenter.dto.UpdateCourseRequest;
import group5.example.sportCenter.dto.UpdateSportRequest;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.SportRepository;

@SpringBootTest
@ActiveProfiles("phase1")
class CourseCatalogAndPublicSportIntegrationTest {
    @Autowired private CourseService coursesService;
    @Autowired private SportService sportService;
    @Autowired private CourseRepository courses;
    @Autowired private SportRepository sports;
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;

    private MockMvc mvc;
    private Sport activeSport;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        activeSport = sport("ACTIVE", "Active " + unique);
    }

    @Test
    void courseCreationAcceptsOnlyEnrollmentLevelsAndNormalizesCase() {
        for (String level : new String[] {"beginner", "INTERMEDIATE", " Advanced "}) {
            var created = coursesService.createCourse(courseRequest(level));
            assertEquals(level.trim().toUpperCase(), created.getLevel());
        }
        for (String invalid : new String[] {null, "", "   ", "NOVICE", "EXPERT"}) {
            assertBadRequest(() -> coursesService.createCourse(courseRequest(invalid)), invalid);
        }
    }

    @Test
    void courseUpdateRejectsUnsupportedLevelAndStatusButAcceptsSupportedStatus() {
        Course course = new Course();
        course.setSport(activeSport);
        course.setName("Existing course");
        course.setLevel("BEGINNER");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);

        UpdateCourseRequest invalidLevel = updateRequest("NOVICE", "ACTIVE");
        Integer id = course.getId();
        assertBadRequest(() -> coursesService.updateCourse(id, invalidLevel), "unsupported level");
        assertBadRequest(() -> coursesService.updateCourse(id, updateRequest("ADVANCED", "ARCHIVED")),
                "unsupported status");

        assertEquals("INACTIVE", coursesService.updateCourse(id,
                updateRequest("advanced", " inactive ")).getStatus());
        assertEquals("ADVANCED", courses.findById(id).orElseThrow().getLevel());
    }

    @Test
    void anonymousPublicSportOptionsContainOnlyActiveSportIdsAndNames() throws Exception {
        String inactiveName = "Hidden " + UUID.randomUUID().toString().substring(0, 8);
        sport("INACTIVE", inactiveName);

        mvc.perform(get("/api/public/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + activeSport.getId() + ")].name").value(activeSport.getName()))
                .andExpect(jsonPath("$[?(@.name == '" + inactiveName + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + activeSport.getId() + ")].status").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + activeSport.getId() + ")].description").doesNotExist());
    }

    @Test
    void vietnameseSportTextSurvivesCreateReadUpdateAndPublicJsonResponse() throws Exception {
        CreateSportRequest create = new CreateSportRequest();
        create.setName("C\u1edd Vua " + UUID.randomUUID().toString().substring(0, 6));
        create.setDescription("R\u00e8n luy\u1ec7n t\u01b0 duy, chi\u1ebfn thu\u1eadt v\u00e0 kh\u1ea3 n\u0103ng t\u1eadp trung.");
        var created = sportService.createSport(create);

        String updatedName = "C\u1edd Vua Tr\u1ebb " + UUID.randomUUID().toString().substring(0, 6);
        String updatedDescription = "Ph\u00f9 h\u1ee3p v\u1edbi ng\u01b0\u1eddi m\u1edbi b\u1eaft \u0111\u1ea7u; ch\u00fa tr\u1ecdng n\u1ec1n t\u1ea3ng v\u00e0 th\u1ef1c h\u00e0nh.";
        UpdateSportRequest update = new UpdateSportRequest();
        update.setName(updatedName);
        update.setDescription(updatedDescription);
        update.setStatus("ACTIVE");
        sportService.updateSport(created.getId(), update);

        Sport stored = sports.findById(created.getId()).orElseThrow();
        assertEquals(updatedName, stored.getName());
        assertEquals(updatedDescription, stored.getDescription());
        mvc.perform(get("/api/public/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + created.getId() + ")].name").value(updatedName));
    }

    @Test
    void publicSportPermitDoesNotExposeExistingSportCatalogRoute() throws Exception {
        mvc.perform(get("/api/sports")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicCourseFlowShowsOnlyActiveCoursesForAnActiveSport() throws Exception {
        Course active = course("ACTIVE", activeSport);
        Course inactive = course("INACTIVE", activeSport);
        Sport inactiveSport = sport("INACTIVE", "Inactive " + UUID.randomUUID().toString().substring(0, 8));
        Course hiddenSportCourse = course("ACTIVE", inactiveSport);

        mvc.perform(get("/api/public/sports/{id}/courses", activeSport.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")]").isEmpty());
        mvc.perform(get("/api/public/courses/{id}", active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(active.getId()));
        mvc.perform(get("/api/public/courses/{id}", inactive.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/sports/{id}/courses", inactiveSport.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/courses/{id}", hiddenSportCourse.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicCourseRoutesDoNotExposeAdminMutationRoutes() throws Exception {
        mvc.perform(get("/api/public/sports/{id}/courses", activeSport.getId()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());
    }

    private Sport sport(String status, String name) {
        Sport sport = new Sport();
        sport.setName(name);
        sport.setStatus(status);
        return sports.saveAndFlush(sport);
    }

    private CreateCourseRequest courseRequest(String level) {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setSportId(activeSport.getId());
        request.setName("Course " + UUID.randomUUID().toString().substring(0, 8));
        request.setLevel(level);
        return request;
    }

    private Course course(String status, Sport sport) {
        Course course = new Course();
        course.setSport(sport);
        course.setName("Course " + UUID.randomUUID().toString().substring(0, 8));
        course.setLevel("BEGINNER");
        course.setStatus(status);
        return courses.saveAndFlush(course);
    }

    private UpdateCourseRequest updateRequest(String level, String status) {
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setName("Updated course");
        request.setLevel(level);
        request.setStatus(status);
        return request;
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action, String context) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action, context);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
