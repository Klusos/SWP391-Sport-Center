package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceHistory;
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
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(AttendanceIntegrationTest.TestClockConfiguration.class)
class AttendanceIntegrationTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant TEST_INSTANT = Instant.parse("2026-10-18T05:00:00Z");

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        Clock attendanceTestClock() {
            return Clock.fixed(TEST_INSTANT, TEST_ZONE);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;
    @Autowired FilterChainProxy security;
    @Autowired DataSource dataSource;
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
    @Autowired AuditLogRepository auditLogs;
    @Autowired AttendanceHistoryRepository histories;
    @MockitoSpyBean AttendanceHistoryRepository historySpy;

    private MockMvc mvc;
    private LocalDateTime now;
    private User coachUser;
    private Coach coach;
    private User otherCoachUser;
    private Coach otherCoach;
    private User admin;
    private User receptionist;
    private User member;
    private User otherMember;
    private CourseClass coachClass;
    private ClassSession startedSession;

    @BeforeEach
    void setUp() {
        histories.deleteAllInBatch();
        attendance.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        now = LocalDateTime.now(Clock.fixed(TEST_INSTANT, TEST_ZONE));
        coachUser = user("COACH", "ACTIVE");
        coach = coach(coachUser);
        otherCoachUser = user("COACH", "ACTIVE");
        otherCoach = coach(otherCoachUser);
        admin = user("ADMIN", "ACTIVE");
        receptionist = user("RECEPTIONIST", "ACTIVE");
        member = user("MEMBER", "ACTIVE");
        otherMember = user("MEMBER", "ACTIVE");
        coachClass = courseClass(coach);
        startedSession = session(coachClass, now.minusMinutes(30), now.plusMinutes(30), "SCHEDULED");
        register(member, coachClass, CourseRegistrationStatus.CONFIRMED);
    }

    @Test
    void testDatabaseIsTheIsolatedH2Profile() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:phase1"));
            assertFalse(connection.getMetaData().getURL().contains("SportCenterDB"));
        }
    }

    @Test
    void assignedCoachDiscoversOnlyAssignedSessionsAndCannotReadOrMarkAnotherCoachRoster() throws Exception {
        ClassSession otherSession = session(courseClass(otherCoach), now.minusMinutes(10), now.plusMinutes(20), "SCHEDULED");

        mvc.perform(get("/api/attendance/coach/sessions").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classSessionId").value(startedSession.getId()))
                .andExpect(jsonPath("$[0].sportId").value(coachClass.getCourseOffering().getCourse().getSport().getId()))
                .andExpect(jsonPath("$[0].sportName").value(coachClass.getCourseOffering().getCourse().getSport().getName()))
                .andExpect(jsonPath("$[?(@.classSessionId == " + otherSession.getId() + ")]").doesNotExist());
        mvc.perform(get("/api/attendance/sessions/" + otherSession.getId() + "/roster")
                .header("Authorization", bearer(coachUser))).andExpect(status().isForbidden());
        mvc.perform(put("/api/attendance/sessions/" + otherSession.getId() + "/members/" + otherMember.getId())
                .header("Authorization", bearer(coachUser)).contentType("application/json")
                .content("{\"status\":\"PRESENT\"}")).andExpect(status().isForbidden());
    }

    @Test
    void rosterReturnsPersistedAttendanceIdsAndCoachCanReadHistoryWithoutExposingIdsForUnmarkedMembers() throws Exception {
        register(otherMember, coachClass, CourseRegistrationStatus.CANCELLED);
        User enrolledMember = user("MEMBER", "ACTIVE");
        register(enrolledMember, coachClass, CourseRegistrationStatus.CONFIRMED);
        markAsAdmin(startedSession, member, "PRESENT");
        Attendance persisted = attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId())
                .orElseThrow();

        var result = mvc.perform(get("/api/attendance/sessions/" + startedSession.getId() + "/roster")
                .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classSessionId").value(startedSession.getId()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andReturn();
        JsonNode members = objectMapper.readTree(result.getResponse().getContentAsString()).get("members");
        JsonNode markedMember = null;
        JsonNode unmarkedMember = null;
        for (JsonNode row : members) {
            if (row.path("memberId").asInt() == member.getId()) markedMember = row;
            if (row.path("memberId").asInt() == enrolledMember.getId()) unmarkedMember = row;
        }
        assertNotNull(markedMember);
        assertNotNull(unmarkedMember);
        assertEquals(persisted.getId(), markedMember.path("attendanceId").asInt());
        assertEquals("PRESENT", markedMember.path("attendanceStatus").asText());
        assertTrue(unmarkedMember.get("attendanceId").isNull());
        assertEquals("NOT_MARKED", unmarkedMember.path("attendanceStatus").asText());

        mvc.perform(get("/api/attendance/" + markedMember.path("attendanceId").asInt() + "/history")
                .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].newStatus").value("PRESENT"));
        mvc.perform(get("/api/attendance/" + markedMember.path("attendanceId").asInt() + "/history")
                .header("Authorization", bearer(otherCoachUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndAssignedCoachCanMarkButReceptionistIsReadOnlyForAttendance() throws Exception {
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markedById").value(admin.getId()));

        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(receptionist))
                .contentType("application/json").content("{\"status\":\"EXCUSED\",\"reason\":\"Verified by reception\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/sessions/" + startedSession.getId() + "/roster")
                .header("Authorization", bearer(receptionist))).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/" + attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow().getId()
                + "/history").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].changedById").value(admin.getId()));
    }

    @Test
    void memberCannotMarkAttendanceAndCanReadOnlyTheirOwnRecordsAndHistory() throws Exception {
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(member))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(markUrl(startedSession, member)).contentType("application/json")
                .content("{\"status\":\"PRESENT\"}")).andExpect(status().isUnauthorized());

        markAsAdmin(startedSession, member, "PRESENT");
        Attendance record = attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow();
        assertEquals(1, auditCount(record.getId(), "MARK"));
        assertEquals(0, auditCount(record.getId(), "CORRECT"));
        mvc.perform(get("/api/attendance/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].attendanceId").value(record.getId()))
                .andExpect(jsonPath("$[0].sportId").value(coachClass.getCourseOffering().getCourse().getSport().getId()))
                .andExpect(jsonPath("$[0].sportName").value(coachClass.getCourseOffering().getCourse().getSport().getName()))
                .andExpect(jsonPath("$[0].courseId").value(coachClass.getCourseOffering().getCourse().getId()))
                .andExpect(jsonPath("$[0].courseClassId").value(coachClass.getId()));
        mvc.perform(get("/api/attendance/me").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/attendance/" + record.getId() + "/history").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].oldStatus").doesNotExist())
                .andExpect(jsonPath("$[0].newStatus").value("PRESENT"));
        mvc.perform(get("/api/attendance/" + record.getId() + "/history").header("Authorization", bearer(otherMember)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyConfirmedRegistrationForSameClassIsEligible() throws Exception {
        User cancelledMember = user("MEMBER", "ACTIVE");
        User unregisteredMember = user("MEMBER", "ACTIVE");
        register(cancelledMember, coachClass, CourseRegistrationStatus.CANCELLED);

        mvc.perform(put(markUrl(startedSession, cancelledMember)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"ABSENT\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put(markUrl(startedSession, unregisteredMember)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"ABSENT\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, attendance.count());
    }

    @Test
    void acceptsAllStatusesAfterStartIncludingCompletedSessionsButRejectsEarlyAndCancelledSessions() throws Exception {
        ClassSession future = session(coachClass, now.plusMinutes(1), now.plusMinutes(45), "SCHEDULED");
        mvc.perform(put(markUrl(future, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());

        List<String> statuses = List.of("PRESENT", "ABSENT", "EXCUSED");
        for (String statusValue : statuses) {
            User rowMember = user("MEMBER", "ACTIVE");
            register(rowMember, coachClass, CourseRegistrationStatus.CONFIRMED);
            ClassSession completedSession = session(coachClass, now.minusHours(2), now.minusHours(1), "COMPLETED");
            mvc.perform(put(markUrl(completedSession, rowMember)).header("Authorization", bearer(coachUser))
                    .contentType("application/json").content("{\"status\":\"" + statusValue + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(statusValue))
                    .andExpect(jsonPath("$.sessionStatus").value("COMPLETED"));
        }

        ClassSession cancelled = session(coachClass, now.minusHours(2), now.minusHours(1), "CANCELLED");
        User cancelledSessionMember = user("MEMBER", "ACTIVE");
        register(cancelledSessionMember, coachClass, CourseRegistrationStatus.CONFIRMED);
        mvc.perform(put(markUrl(cancelled, cancelledSessionMember)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void correctionsRequireReasonAndAppendHistoryWhileRepeatedStatusIsIdempotent() throws Exception {
        markAsAdmin(startedSession, member, "PRESENT");
        Attendance record = attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow();

        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"ABSENT\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size());

        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"ABSENT\",\"reason\":\"Roster correction\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABSENT"));
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"ABSENT\",\"note\":\"ignored for idempotent repeat\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABSENT"));

        List<AttendanceHistory> events = histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId());
        assertEquals(2, events.size());
        assertEquals(null, events.get(0).getOldStatus());
        assertEquals(AttendanceStatus.PRESENT, events.get(0).getNewStatus());
        assertEquals(AttendanceStatus.PRESENT, events.get(1).getOldStatus());
        assertEquals(AttendanceStatus.ABSENT, events.get(1).getNewStatus());
        assertEquals("Roster correction", events.get(1).getReason());
        assertEquals(1, auditCount(record.getId(), "MARK"));
        assertEquals(1, auditCount(record.getId(), "CORRECT"));
        Attendance updated = attendance.findById(record.getId()).orElseThrow();
        assertEquals(coachUser.getId(), updated.getMarkedBy().getId());
    }

    @Test
    void cancelledSessionPreservesMemberHistoryAndIsFlaggedAsNotValidForStatistics() throws Exception {
        markAsAdmin(startedSession, member, "PRESENT");
        startedSession.setStatus("CANCELLED");
        sessions.saveAndFlush(startedSession);

        mvc.perform(get("/api/attendance/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sessionStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].validForStatistics").value(false))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
        Attendance record = attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow();
        assertEquals(1, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size());
    }

    @Test
    void sessionAndRegistrationStatusesAreNotChangedByAttendanceAndUniqueKeyPreventsDuplicateRows() throws Exception {
        markAsAdmin(startedSession, member, "PRESENT");
        assertEquals("SCHEDULED", sessions.findById(startedSession.getId()).orElseThrow().getStatus());
        assertEquals(1, registrations.findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(
                member.getId(), CourseRegistrationStatus.CONFIRMED).size());

        Attendance duplicate = new Attendance();
        duplicate.setClassSession(startedSession);
        duplicate.setMember(member);
        duplicate.setStatus(AttendanceStatus.ABSENT);
        duplicate.setMarkedBy(admin);
        duplicate.setMarkedAt(now);
        assertThrows(DataIntegrityViolationException.class, () -> attendance.saveAndFlush(duplicate));
        assertEquals(1, attendance.count());
    }

    @Test
    void concurrentInitialMarksProduceOneRecordAndOneHistoryEvent() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> concurrentMark(ready, start));
            Future<Integer> second = pool.submit(() -> concurrentMark(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(200, first.get(20, TimeUnit.SECONDS));
            assertEquals(200, second.get(20, TimeUnit.SECONDS));
            Attendance record = attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow();
            assertEquals(1, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(record.getId()).size());
            assertEquals(1, attendance.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void historyFailureRollsBackBothTheCurrentRecordAndHistory() throws Exception {
        doThrow(new DataIntegrityViolationException("simulated history write failure"))
                .when(historySpy).saveAndFlush(any());
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, attendance.count());
        assertEquals(0, histories.count());
    }

    @Test
    void staffSessionDiscoveryAndRoleBoundariesAreEnforced() throws Exception {
        mvc.perform(get("/api/attendance/sessions").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/sessions").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/sessions").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/coach/sessions").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/me").header("Authorization", bearer(coachUser)))
                .andExpect(status().isForbidden());
    }

    private int concurrentMark(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        return mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andReturn().getResponse().getStatus();
    }

    private void markAsAdmin(ClassSession session, User forMember, String statusValue) throws Exception {
        mvc.perform(put(markUrl(session, forMember)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"" + statusValue + "\"}"))
                .andExpect(status().isOk());
    }

    private String markUrl(ClassSession session, User forMember) {
        return "/api/attendance/sessions/" + session.getId() + "/members/" + forMember.getId();
    }

    private User user(String role, String status) {
        User result = new User();
        result.setName(role + " attendance test");
        result.setEmail(role.toLowerCase() + "-attendance-" + UUID.randomUUID() + "@example.test");
        result.setRole(role);
        result.setStatus(status);
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
        sport.setName("Attendance sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);

        Course course = new Course();
        course.setSport(sport);
        course.setName("Attendance course " + UUID.randomUUID());
        course.setLevel("BEGINNER");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Attendance offering " + UUID.randomUUID());
        offering.setPrice(new BigDecimal("100.00"));
        offering.setSessionDuration(60);
        offering.setStartDate(LocalDate.now(TEST_ZONE).minusDays(1));
        offering.setEndDate(LocalDate.now(TEST_ZONE).plusDays(90));
        offering.setRegistrationDeadline(now.plusDays(1));
        offering.setCancellationDeadline(now.plusDays(1));
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);

        CourseClass result = new CourseClass();
        result.setCourseOffering(offering);
        result.setCoach(assignedCoach);
        result.setName("Attendance class " + UUID.randomUUID());
        result.setCapacity(20);
        result.setStatus("ACTIVE");
        return classes.saveAndFlush(result);
    }

    private ClassSession session(CourseClass courseClass, LocalDateTime startTime, LocalDateTime endTime, String status) {
        ClassSession result = new ClassSession();
        result.setCourseClass(courseClass);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setStatus(status);
        return sessions.saveAndFlush(result);
    }

    private CourseRegistration register(User forMember, CourseClass courseClass, CourseRegistrationStatus status) {
        CourseRegistration result = new CourseRegistration();
        result.setMember(forMember);
        result.setCourseClass(courseClass);
        result.setStatus(status);
        result.setOriginalPrice(new BigDecimal("100.00"));
        result.setMembershipDiscount(new BigDecimal("0.00"));
        result.setFinalPrice(new BigDecimal("100.00"));
        return registrations.saveAndFlush(result);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private long auditCount(Integer attendanceId, String action) {
        return auditLogs.count((root, query, cb) -> cb.and(
                cb.equal(root.get("entityType"), "ATTENDANCE"),
                cb.equal(root.get("entityId"), attendanceId.toString()),
                cb.equal(root.get("action"), action)));
    }
}
