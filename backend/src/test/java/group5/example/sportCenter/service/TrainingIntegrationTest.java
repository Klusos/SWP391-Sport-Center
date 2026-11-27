package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ProgressEvaluationRequest;
import group5.example.sportCenter.dto.RecordTrainingResultRequest;
import group5.example.sportCenter.dto.TrainingExerciseRequest;
import group5.example.sportCenter.dto.UpsertTrainingPlanRequest;
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
import group5.example.sportCenter.repository.AuditLogRepository;
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
import group5.example.sportCenter.repository.TrainingPlanWeekRepository;
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
    @Autowired TrainingService trainingService;
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
    @Autowired AuditLogRepository auditLogs;
    @Autowired TrainingPlanRepository plans;
    @Autowired TrainingPlanWeekRepository planWeeks;
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
        planWeeks.deleteAllInBatch();
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
    void weeklyGeneralPlanSavePreservesLegacyExerciseIdentityAndEnforcesPlanOwnership() throws Exception {
        String planUrl = "/api/training/classes/" + coachClass.getId() + "/plan";
        mvc.perform(put(planUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"goal\":\"Overall goal\"}")).andExpect(status().isOk());
        String legacyKept = mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Legacy unassigned\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer legacyKeptId = Integer.valueOf(legacyKept.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String legacyMoved = mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Move into week\",\"reps\":12}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer legacyMovedId = Integer.valueOf(legacyMoved.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String planBeforeSave = mvc.perform(get(planUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String expectedUpdatedAt = planBeforeSave.replaceAll(".*\"updatedAt\":\"([^\"]+)\".*", "$1");

        String body = "{\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\",\"goal\":\"Serve fundamentals\",\"description\":\"Complete weekly plan\","
                + "\"unassignedExerciseIds\":[" + legacyKeptId + "],\"weeks\":["
                + "{\"id\":null,\"weekNumber\":20,\"title\":\"Contact\",\"goal\":\"Consistent contact\","
                + "\"description\":\"Technique\",\"exercises\":[{\"id\":" + legacyMovedId
                + ",\"name\":\"Move into week\",\"sets\":2,\"reps\":12}]},"
                + "{\"id\":null,\"weekNumber\":2,\"title\":\"Footwork\",\"goal\":null,\"description\":null,\"exercises\":["
                + "{\"id\":null,\"name\":\"Split step\",\"sets\":3,\"reps\":8}]}]}";
        String saved = mvc.perform(put(planUrl + "/weekly").header("Authorization", bearer(coachUser))
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weeks.length()").value(2))
                .andExpect(jsonPath("$.weeks[0].weekNumber").value(2))
                .andExpect(jsonPath("$.weeks[1].weekNumber").value(20))
                .andExpect(jsonPath("$.weeks[1].goal").value("Consistent contact"))
                .andExpect(jsonPath("$.weeks[1].exercises[0].id").value(legacyMovedId))
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].id").value(legacyKeptId))
                .andReturn().getResponse().getContentAsString();
        Integer weekTwoId = Integer.valueOf(saved.replaceAll(".*\"weeks\":\\[\\{\"id\":(\\d+).*", "$1"));
        String savedUpdatedAt = saved.replaceAll(".*\"updatedAt\":\"([^\"]+)\".*", "$1");

        String stale = body.replace(expectedUpdatedAt, "2020-01-01T00:00:00");
        mvc.perform(put(planUrl + "/weekly").header("Authorization", bearer(coachUser))
                .contentType("application/json").content(stale)).andExpect(status().isConflict());
        assertEquals("Serve fundamentals", plans.findByCourseClassId(coachClass.getId()).orElseThrow().getGoal());

        mvc.perform(get(planUrl).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weeks.length()").value(2))
                .andExpect(jsonPath("$.weeks[0].startDate").isNotEmpty())
                .andExpect(jsonPath("$.weeks[1].outsideOffering").value(true));

        String duplicate = body.replace(expectedUpdatedAt, savedUpdatedAt)
                .replace("\"weekNumber\":20", "\"weekNumber\":2");
        mvc.perform(put(planUrl + "/weekly").header("Authorization", bearer(coachUser))
                .contentType("application/json").content(duplicate)).andExpect(status().isBadRequest());
        assertEquals(2, planWeeks.count());
        assertEquals(3, exercises.count());
        assertEquals(legacyMovedId, exercises.findByPlanIdOrderBySortOrderAscIdAsc(
                plans.findByCourseClassId(coachClass.getId()).orElseThrow().getId()).stream()
                .filter(exercise -> exercise.getWeek() != null).findFirst().orElseThrow().getId());

        CourseClass otherClass = courseClass(coaches.findByUserId(otherCoachUser.getId()).orElseThrow());
        String otherPlanUrl = "/api/training/classes/" + otherClass.getId() + "/plan";
        mvc.perform(put(otherPlanUrl).header("Authorization", bearer(otherCoachUser)).contentType("application/json")
                .content("{\"goal\":\"Other class\"}")).andExpect(status().isOk());
        String otherExercise = mvc.perform(post(otherPlanUrl + "/exercises").header("Authorization", bearer(otherCoachUser))
                .contentType("application/json").content("{\"name\":\"Other plan exercise\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer otherExerciseId = Integer.valueOf(otherExercise.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(put(planUrl + "/weekly").header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"expectedUpdatedAt\":\"" + savedUpdatedAt + "\",\"goal\":\"Must rollback\",\"unassignedExerciseIds\":[" + legacyKeptId + "],"
                        + "\"weeks\":[{\"id\":" + weekTwoId + ",\"weekNumber\":2,\"exercises\":[{\"id\":"
                        + otherExerciseId + ",\"name\":\"Foreign reference\"}]}]}"))
                .andExpect(status().isBadRequest());
        assertEquals("Serve fundamentals", plans.findByCourseClassId(coachClass.getId()).orElseThrow().getGoal());
        mvc.perform(put(planUrl + "/weekly").header("Authorization", bearer(admin)).contentType("application/json")
                .content(body)).andExpect(status().isForbidden());
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

        Attendance present = attendance.findByClassSessionIdAndMemberId(endedSession.getId(), member.getId()).orElseThrow();
        int historyBeforeRejectedCorrection = attendanceHistories
                .findByAttendanceIdOrderByChangedAtAscIdAsc(present.getId()).size();
        long auditBeforeRejectedCorrection = auditLogs.count();
        for (String statusValue : new String[] { "ABSENT", "EXCUSED" }) {
            mvc.perform(put("/api/attendance/sessions/" + endedSession.getId() + "/members/" + member.getId())
                    .header("Authorization", bearer(coachUser)).contentType("application/json")
                    .content("{\"status\":\"" + statusValue + "\",\"reason\":\"Owner-approved correction\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Học viên đã có kết quả tập luyện cho buổi học này. Không thể chuyển trạng thái điểm danh sang vắng mặt hoặc vắng có phép."));
        }
        mvc.perform(put("/api/attendance/sessions/" + endedSession.getId() + "/members/" + member.getId())
                .header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"status\":\"ABSENT\",\"reason\":\"Admin correction\"}"))
                .andExpect(status().isConflict());
        assertEquals(AttendanceStatus.PRESENT, attendance.findById(present.getId()).orElseThrow().getStatus());
        assertEquals(1, results.count());
        assertEquals(historyBeforeRejectedCorrection,
                attendanceHistories.findByAttendanceIdOrderByChangedAtAscIdAsc(present.getId()).size());
        assertEquals(auditBeforeRejectedCorrection, auditLogs.count());

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
    void sharedSessionLockPreventsConcurrentAbsentCorrectionAndResultCreationFromCommittingInconsistentState() throws Exception {
        markAttendance(endedSession, member, AttendanceStatus.PRESENT);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> correction = pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start gate timed out");
                return mvc.perform(put("/api/attendance/sessions/" + endedSession.getId() + "/members/" + member.getId())
                        .header("Authorization", bearer(coachUser)).contentType("application/json")
                        .content("{\"status\":\"ABSENT\",\"reason\":\"Concurrent consistency test\"}"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> result = pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start gate timed out");
                return mvc.perform(put("/api/training/sessions/" + endedSession.getId() + "/results/" + member.getId())
                        .header("Authorization", bearer(coachUser)).contentType("application/json")
                        .content("{\"score\":7,\"notes\":\"Concurrent consistency test\"}"))
                        .andReturn().getResponse().getStatus();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int correctionStatus = correction.get(10, TimeUnit.SECONDS);
            int resultStatus = result.get(10, TimeUnit.SECONDS);
            assertTrue((correctionStatus == 200 && resultStatus == 409)
                    || (correctionStatus == 409 && resultStatus == 200));
            AttendanceStatus finalStatus = attendance.findByClassSessionIdAndMemberId(endedSession.getId(), member.getId())
                    .orElseThrow().getStatus();
            assertEquals(finalStatus == AttendanceStatus.PRESENT, results.existsByClassSessionIdAndMemberId(
                    endedSession.getId(), member.getId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void progressEvaluationsAppendHistoryAndMemberSeesOwnTrainingOverview() throws Exception {
        mvc.perform(put("/api/training/classes/" + coachClass.getId() + "/plan").header("Authorization", bearer(coachUser))
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
    void classProgressEligibilityRequiresCurrentPresentAttendanceInCompletedSession() throws Exception {
        markAttendance(futureSession, member, AttendanceStatus.PRESENT);
        markAttendance(endedSession, member, AttendanceStatus.PRESENT);
        String progressUrl = "/api/training/classes/" + coachClass.getId() + "/progress";
        mvc.perform(get(progressUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(false))
                .andExpect(jsonPath("$[0].sessionsMarked").value(2))
                .andExpect(jsonPath("$[0].sessionsPresent").value(2));

        mvc.perform(post("/api/training/sessions/" + endedSession.getId() + "/complete")
                        .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk());
        mvc.perform(get(progressUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(true));

        ClassSession secondCompletedSession = session(coachClass, now.minusDays(2), now.minusDays(2).plusHours(1));
        markAttendance(secondCompletedSession, member, AttendanceStatus.PRESENT);
        mvc.perform(post("/api/training/sessions/" + secondCompletedSession.getId() + "/complete")
                        .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk());
        mvc.perform(get(progressUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(true))
                .andExpect(jsonPath("$[0].sessionsMarked").value(3))
                .andExpect(jsonPath("$[0].sessionsPresent").value(3));

        String attendanceUrl = "/api/attendance/sessions/" + endedSession.getId() + "/members/" + member.getId();
        mvc.perform(put(attendanceUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                        .content("{\"status\":\"ABSENT\",\"reason\":\"Correction\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(progressUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(true))
                .andExpect(jsonPath("$[0].sessionsPresent").value(2));

        String secondAttendanceUrl = "/api/attendance/sessions/" + secondCompletedSession.getId()
                + "/members/" + member.getId();
        mvc.perform(put(secondAttendanceUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                        .content("{\"status\":\"EXCUSED\",\"reason\":\"Correction\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(progressUrl).header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(true))
                .andExpect(jsonPath("$[0].sessionsMarked").value(3))
                .andExpect(jsonPath("$[0].sessionsPresent").value(2));
    }

    @Test
    void classProgressDoesNotUseAttendanceFromAnotherClassOrCancelledSessions() throws Exception {
        register(outsider, coachClass);
        CourseClass unrelatedClass = courseClass(coaches.findByUserId(otherCoachUser.getId()).orElseThrow());
        register(member, unrelatedClass);
        ClassSession completedElsewhere = session(unrelatedClass, now.minusDays(2), now.minusDays(2).plusHours(1));
        completedElsewhere.setStatus("COMPLETED");
        sessions.saveAndFlush(completedElsewhere);
        markAttendance(completedElsewhere, member, AttendanceStatus.PRESENT);

        ClassSession cancelledHere = session(coachClass, now.minusDays(2), now.minusDays(2).plusHours(1));
        cancelledHere.setStatus("CANCELLED");
        sessions.saveAndFlush(cancelledHere);
        markAttendance(cancelledHere, member, AttendanceStatus.PRESENT);

        mvc.perform(get("/api/training/classes/" + coachClass.getId() + "/progress")
                        .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hasQualifyingCompletedAttendance").value(false))
                .andExpect(jsonPath("$[1].hasQualifyingCompletedAttendance").value(false));
    }

    @Test
    void adminReadsTrainingButCannotWriteThroughHttpOrDirectServiceCalls() throws Exception {
        String planUrl = "/api/training/classes/" + coachClass.getId() + "/plan";
        String planResponse = mvc.perform(put(planUrl).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content("{\"goal\":\"Coach goal\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer planId = Integer.valueOf(planResponse.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String exerciseResponse = mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                        .contentType("application/json").content("{\"name\":\"Footwork\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer exerciseId = Integer.valueOf(exerciseResponse.replaceAll(".*\"id\":(\\d+).*", "$1"));
        markAttendance(endedSession, member, AttendanceStatus.PRESENT);

        mvc.perform(get("/api/training/classes").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.classId == " + coachClass.getId() + ")]").exists());
        mvc.perform(get(planUrl).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/training/classes/" + coachClass.getId() + "/sessions")
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/training/classes/" + coachClass.getId() + "/progress")
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/training/sessions/" + endedSession.getId() + "/results")
                .header("Authorization", bearer(admin))).andExpect(status().isOk());

        mvc.perform(put(planUrl).header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"goal\":\"Admin draft\"}")).andExpect(status().isForbidden());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"name\":\"Admin exercise\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/training/exercises/" + exerciseId).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"name\":\"Admin edit\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/training/exercises/" + exerciseId).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/training/sessions/" + endedSession.getId() + "/complete")
                .header("Authorization", bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(put("/api/training/sessions/" + endedSession.getId() + "/results/" + member.getId())
                .header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"score\":8}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/training/classes/" + coachClass.getId() + "/members/" + member.getId() + "/evaluations")
                .header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"rating\":4,\"comment\":\"Admin evaluation\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put(planUrl).header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"goal\":\"Coach revision\",\"description\":\"Revised\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(planId))
                .andExpect(jsonPath("$.goal").value("Coach revision"));
        assertEquals(1, plans.count());
        assertEquals(1, exercises.count());
        assertEquals("Footwork", exercises.findByPlanIdOrderBySortOrderAscIdAsc(planId).get(0).getName());
        assertEquals("SCHEDULED", sessions.findById(endedSession.getId()).orElseThrow().getStatus());
        assertEquals(0, results.count());
        assertEquals(0, evaluations.count());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Invalid order\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(planUrl + "/exercises").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"name\":\"Too many sets\",\"sets\":101}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, exercises.count());

        Jwt adminJwt = jwt(admin);
        assertServiceWriteDenied(() -> trainingService.upsertPlan(coachClass.getId(),
                new UpsertTrainingPlanRequest("Direct Admin edit", null), adminJwt));
        assertServiceWriteDenied(() -> trainingService.addExercise(coachClass.getId(),
                new TrainingExerciseRequest("Direct Admin exercise", null, null, null, null, null), adminJwt));
        assertServiceWriteDenied(() -> trainingService.updateExercise(exerciseId,
                new TrainingExerciseRequest("Direct Admin edit", null, null, null, null, null), adminJwt));
        assertServiceWriteDenied(() -> trainingService.deleteExercise(exerciseId, adminJwt));
        assertServiceWriteDenied(() -> trainingService.completeSession(endedSession.getId(), adminJwt));
        assertServiceWriteDenied(() -> trainingService.recordResult(endedSession.getId(), member.getId(),
                new RecordTrainingResultRequest(8, null), adminJwt));
        assertServiceWriteDenied(() -> trainingService.evaluate(coachClass.getId(), member.getId(),
                new ProgressEvaluationRequest(4, "Direct Admin evaluation"), adminJwt));
        assertEquals("Coach revision", plans.findByCourseClassId(coachClass.getId()).orElseThrow().getGoal());
        assertEquals(1, exercises.count());
        assertEquals(0, results.count());
        assertEquals(0, evaluations.count());
    }

    private void assertServiceWriteDenied(org.junit.jupiter.api.function.Executable write) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, write);
        assertEquals(403, exception.getStatusCode().value());
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
        result.setRegisteredAt(now.minusYears(1));
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

    private Jwt jwt(User user) {
        return jwtService.validateToken(jwtService.generateToken(user));
    }
}
