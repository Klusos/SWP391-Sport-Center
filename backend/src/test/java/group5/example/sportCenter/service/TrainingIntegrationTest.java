package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceStatus;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.AttendanceHistoryRepository;
import group5.example.sportCenter.repository.AttendanceRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.ProgressEvaluationRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.TrainingExerciseRepository;
import group5.example.sportCenter.repository.TrainingPlanRepository;
import group5.example.sportCenter.repository.TrainingResultRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(TrainingIntegrationTest.TestClockConfiguration.class)
class TrainingIntegrationTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant TEST_INSTANT = Instant.parse("2026-10-18T05:00:00Z");

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        Clock trainingTestClock() {
            return Clock.fixed(TEST_INSTANT, TEST_ZONE);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired CoachRepository coaches;
    @Autowired SportRepository sports;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceHistoryRepository attendanceHistories;
    @Autowired TrainingPlanRepository plans;
    @Autowired TrainingExerciseRepository exercises;
    @Autowired TrainingResultRepository results;
    @Autowired ProgressEvaluationRepository evaluations;

    private MockMvc mvc;
    private LocalDateTime now;
    private User coachUser;
    private User otherCoachUser;
    private User admin;
    private User receptionist;
    private User member;
    private User outsider;
    private CourseClass coachClass;
    private ClassSession endedSession;
    private ClassSession futureSession;

    @BeforeEach
    void setUp() {
        results.deleteAllInBatch();
        evaluations.deleteAllInBatch();
        exercises.deleteAllInBatch();
        plans.deleteAllInBatch();
        attendanceHistories.deleteAllInBatch();
        attendance.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        now = LocalDateTime.now(Clock.fixed(TEST_INSTANT, TEST_ZONE));
        coachUser = user("COACH");
        Coach coach = coach(coachUser);
        otherCoachUser = user("COACH");
        coach(otherCoachUser);
        admin = user("ADMIN");
        receptionist = user("RECEPTIONIST");
        member = user("MEMBER");
        outsider = user("MEMBER");
        coachClass = courseClass(coach);
        endedSession = session(coachClass, now.minusHours(3), now.minusHours(1).minusMinutes(30));
        futureSession = session(coachClass, now.plusDays(2), now.plusDays(2).plusMinutes(90));
        register(member, coachClass);
    }

    @Test
    void assignedCoachBuildsPlanWithGoalAndExercisesThatOnlyEnrolledMembersCanRead() throws Exception {
        String planUrl = "/api/training/classes/" + coachClass.getId() + "/plan";
        mvc.perform(get(planUrl).header("Authorization", bearer(coachUser))).andExpect(status().isNotFound());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Footwork\"}"))
                .andExpect(status().isConflict());

        mvc.perform(put(planUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"goal\":\"  Serve consistently  \",\"description\":\"Eight-week fundamentals\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("Serve consistently"))
                .andExpect(jsonPath("$.exercises").isEmpty());
        Integer planId = plans.findByCourseClassId(coachClass.getId()).orElseThrow().getId();
        mvc.perform(put(planUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"goal\":\"Serve consistently\",\"description\":\"Eight-week fundamentals\"}"))
                .andExpect(status().isOk());
        assertEquals(planId, plans.findByCourseClassId(coachClass.getId()).orElseThrow().getId());
        assertEquals(1, plans.count());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Invalid order\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Footwork ladder\",\"sets\":3,\"reps\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0));
        String created = mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Short serve\",\"durationMinutes\":15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andReturn().getResponse().getContentAsString();
        Integer exerciseId = Integer.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(put("/api/training/exercises/" + exerciseId).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Short serve drill\",\"durationMinutes\":20,\"sortOrder\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationMinutes").value(20));
        mvc.perform(put("/api/training/exercises/" + exerciseId).header("Authorization", bearer(otherCoachUser))
                .contentType("application/json").content("{\"name\":\"Hijack\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(planUrl).header("Authorization", bearer(receptionist)).contentType("application/json")
                .content("{\"goal\":\"Not allowed\"}")).andExpect(status().isForbidden());

        mvc.perform(get(planUrl).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[1].name").value("Short serve drill"));
        mvc.perform(get(planUrl).header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(get(planUrl).header("Authorization", bearer(otherCoachUser))).andExpect(status().isForbidden());

        mvc.perform(delete("/api/training/exercises/" + exerciseId).header("Authorization", bearer(coachUser)))
                .andExpect(status().isNoContent());
        assertEquals(1, exercises.count());
    }

    @Test
    void coachCompletesOnlyEndedSessionsAndCompletionIsIdempotent() throws Exception {
        mvc.perform(post("/api/training/sessions/" + futureSession.getId() + "/complete")
                .header("Authorization", bearer(coachUser))).andExpect(status().isConflict());
        mvc.perform(post("/api/training/sessions/" + endedSession.getId() + "/complete")
                .header("Authorization", bearer(otherCoachUser))).andExpect(status().isForbidden());
        mvc.perform(post("/api/training/sessions/" + endedSession.getId() + "/complete")
                .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.canComplete").value(false));
        mvc.perform(post("/api/training/sessions/" + endedSession.getId() + "/complete")
                .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/training/classes/" + coachClass.getId() + "/sessions").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(endedSession.getId()))
                .andExpect(jsonPath("$[1].canRecordResults").value(false));
        mvc.perform(get("/api/training/classes").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].completedSessions").value(1));
    }

    @Test
    void resultsRequirePresentAttendanceAndUpdateInPlace() throws Exception {
        String resultUrl = "/api/training/sessions/" + endedSession.getId() + "/results/" + member.getId();
        mvc.perform(put(resultUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"score\":7}")).andExpect(status().isConflict());

        markAttendance(endedSession, member, AttendanceStatus.ABSENT);
        mvc.perform(put(resultUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"score\":7}")).andExpect(status().isConflict());

        Attendance record = attendance.findByClassSessionIdAndMemberId(endedSession.getId(), member.getId()).orElseThrow();
        record.setStatus(AttendanceStatus.PRESENT);
        attendance.saveAndFlush(record);
        mvc.perform(put(resultUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"score\":11}")).andExpect(status().isBadRequest());
        mvc.perform(put(resultUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"score\":7,\"notes\":\"Good grip\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(7));
        mvc.perform(put(resultUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"score\":8,\"notes\":\"Better footwork\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(8));
        assertEquals(1, results.count());

        mvc.perform(put("/api/training/sessions/" + futureSession.getId() + "/results/" + member.getId())
                .header("Authorization", bearer(coachUser)).contentType("application/json").content("{\"score\":5}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/training/sessions/" + endedSession.getId() + "/results/" + outsider.getId())
                .header("Authorization", bearer(coachUser)).contentType("application/json").content("{\"score\":5}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/training/sessions/" + endedSession.getId() + "/results").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$.members[0].score").value(8))
                .andExpect(jsonPath("$.members[0].notes").value("Better footwork"));
        mvc.perform(get("/api/training/sessions/" + endedSession.getId() + "/results").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void progressEvaluationsAppendHistoryAndMemberSeesOwnTrainingOverview() throws Exception {
        mvc.perform(put("/api/training/classes/" + coachClass.getId() + "/plan").header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"goal\":\"Rally 20 shots\"}"))
                .andExpect(status().isOk());
        markAttendance(endedSession, member, AttendanceStatus.PRESENT);
        mvc.perform(put("/api/training/sessions/" + endedSession.getId() + "/results/" + member.getId())
                .header("Authorization", bearer(coachUser)).contentType("application/json").content("{\"score\":6}"))
                .andExpect(status().isOk());

        String evaluateUrl = "/api/training/classes/" + coachClass.getId() + "/members/" + member.getId() + "/evaluations";
        mvc.perform(post(evaluateUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"rating\":3,\"comment\":\"On track\"}")).andExpect(status().isCreated());
        mvc.perform(post(evaluateUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"rating\":6,\"comment\":\"Out of range\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(evaluateUrl).header("Authorization", bearer(otherCoachUser)).contentType("application/json")
                .content("{\"rating\":4,\"comment\":\"Not my class\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/training/classes/" + coachClass.getId() + "/members/" + outsider.getId() + "/evaluations")
                .header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"rating\":4,\"comment\":\"Not enrolled\"}")).andExpect(status().isConflict());

        mvc.perform(get("/api/training/classes/" + coachClass.getId() + "/progress").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$[0].sessionsPresent").value(1))
                .andExpect(jsonPath("$[0].averageScore").value(6.0))
                .andExpect(jsonPath("$[0].latestEvaluation.rating").value(3));

        mvc.perform(get("/api/training/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plan.goal").value("Rally 20 shots"))
                .andExpect(jsonPath("$[0].sessionsPresent").value(1))
                .andExpect(jsonPath("$[0].results[0].score").value(6))
                .andExpect(jsonPath("$[0].evaluations[0].comment").value("On track"));
        mvc.perform(get("/api/training/me").header("Authorization", bearer(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/training/me").header("Authorization", bearer(coachUser))).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccessMemberWritesAndInvalidReferencesAreRejected() throws Exception {
        String planUrl = "/api/training/classes/" + coachClass.getId() + "/plan";
        mvc.perform(get("/api/training/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/training/me")).andExpect(status().isUnauthorized());
        mvc.perform(put(planUrl).contentType("application/json").content("{\"goal\":\"No auth\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/training/classes/999999/plan").header("Authorization", bearer(coachUser)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/training/sessions/999999/complete").header("Authorization", bearer(coachUser)))
                .andExpect(status().isNotFound());
        mvc.perform(put(planUrl).header("Authorization", bearer(member)).contentType("application/json")
                .content("{\"goal\":\"Member write\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(member)).contentType("application/json")
                .content("{\"name\":\"Member exercise\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/training/sessions/" + endedSession.getId() + "/results/" + member.getId())
                .header("Authorization", bearer(member)).contentType("application/json").content("{\"score\":8}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/training/classes/" + coachClass.getId() + "/members/" + member.getId() + "/evaluations")
                .header("Authorization", bearer(member)).contentType("application/json")
                .content("{\"rating\":4,\"comment\":\"Member write\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, plans.count());
        assertEquals(0, exercises.count());
        assertEquals(0, results.count());
        assertEquals(0, evaluations.count());
    }

    private User user(String role) {
        User result = new User();
        result.setName(role + " training test " + UUID.randomUUID().toString().substring(0, 8));
        result.setEmail(role.toLowerCase() + "-training-" + UUID.randomUUID() + "@example.test");
        result.setRole(role);
        result.setStatus("ACTIVE");
        result.setPasswordHash("test-only-unused-hash");
        return users.saveAndFlush(result);
    }

    private Coach coach(User owner) {
        Coach result = new Coach();
        result.setUser(owner);
        result.setStatus("ACTIVE");
        return coaches.saveAndFlush(result);
    }

    private CourseClass courseClass(Coach assignedCoach) {
        Sport sport = new Sport();
        sport.setName("Training sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);

        Course course = new Course();
        course.setSport(sport);
        course.setName("Training course " + UUID.randomUUID());
        course.setLevel("BEGINNER");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Training offering " + UUID.randomUUID());
        offering.setPrice(new BigDecimal("100.00"));
        offering.setSessionDuration(90);
        offering.setStartDate(LocalDate.now(TEST_ZONE).minusDays(1));
        offering.setEndDate(LocalDate.now(TEST_ZONE).plusDays(60));
        offering.setRegistrationDeadline(now.plusDays(1));
        offering.setCancellationDeadline(now.plusDays(1));
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);

        CourseClass result = new CourseClass();
        result.setCourseOffering(offering);
        result.setCoach(assignedCoach);
        result.setName("Training class " + UUID.randomUUID());
        result.setCapacity(20);
        result.setStatus("ACTIVE");
        return classes.saveAndFlush(result);
    }

    private ClassSession session(CourseClass courseClass, LocalDateTime startTime, LocalDateTime endTime) {
        ClassSession result = new ClassSession();
        result.setCourseClass(courseClass);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setStatus("SCHEDULED");
        return sessions.saveAndFlush(result);
    }

    private void register(User forMember, CourseClass courseClass) {
        CourseRegistration result = new CourseRegistration();
        result.setMember(forMember);
        result.setCourseClass(courseClass);
        result.setStatus(CourseRegistrationStatus.CONFIRMED);
        result.setOriginalPrice(new BigDecimal("100.00"));
        result.setMembershipDiscount(new BigDecimal("0.00"));
        result.setFinalPrice(new BigDecimal("100.00"));
        registrations.saveAndFlush(result);
    }

    private void markAttendance(ClassSession session, User forMember, AttendanceStatus status) {
        Attendance record = new Attendance();
        record.setClassSession(session);
        record.setMember(forMember);
        record.setStatus(status);
        record.setMarkedBy(coachUser);
        record.setMarkedAt(now);
        attendance.saveAndFlush(record);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
