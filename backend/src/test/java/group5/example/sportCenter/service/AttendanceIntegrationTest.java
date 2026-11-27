package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;

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
import group5.example.sportCenter.repository.NotificationRepository;
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
    @Autowired NotificationRepository notifications;
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
                .andExpect(jsonPath("$[0].attendanceTimeEligible").value(true))
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
    void sessionReadModelSeparatesFutureEmptyRosterElapsedEmptyRosterAndCancellation() throws Exception {
        CourseClass emptyClass = courseClass(coach);
        ClassSession futureEmpty = session(emptyClass, now.plusDays(1), now.plusDays(1).plusHours(1), "SCHEDULED");
        ClassSession elapsedEmpty = session(emptyClass, now.minusDays(1), now.minusDays(1).plusHours(1), "COMPLETED");
        ClassSession cancelled = session(emptyClass, now.minusDays(2), now.minusDays(2).plusHours(1), "CANCELLED");

        var response = mvc.perform(get("/api/attendance/coach/sessions").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.classSessionId == " + futureEmpty.getId() + ")].attendanceCompleteness")
                        .value(org.hamcrest.Matchers.hasItem("NOT_APPLICABLE")))
                .andExpect(jsonPath("$[?(@.classSessionId == " + futureEmpty.getId() + ")].attendanceTimeEligible")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$[?(@.classSessionId == " + elapsedEmpty.getId() + ")].attendanceCompleteness")
                        .value(org.hamcrest.Matchers.hasItem("NOT_APPLICABLE")))
                .andExpect(jsonPath("$[?(@.classSessionId == " + elapsedEmpty.getId() + ")].attendanceTimeEligible")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.classSessionId == " + cancelled.getId() + ")].attendanceTimeEligible")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andReturn();
        assertNotNull(response.getResponse().getContentAsString());
    }

    @Test
    void rosterReturnsPersistedAttendanceIdsAndCoachCanReadHistoryWithoutExposingIdsForUnmarkedMembers() throws Exception {
        register(otherMember, coachClass, CourseRegistrationStatus.CANCELLED);
        User enrolledMember = user("MEMBER", "ACTIVE");
        register(enrolledMember, coachClass, CourseRegistrationStatus.CONFIRMED);
        Attendance fixture = persistAttendance(startedSession, member, AttendanceStatus.PRESENT, admin);
        appendInitialHistory(fixture);
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
        assertTrue(unmarkedMember.path("attendanceId").isMissingNode() || unmarkedMember.path("attendanceId").isNull());
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
        markAsAdmin(startedSession, member, "PRESENT");

        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(receptionist))
                .contentType("application/json").content("{\"status\":\"EXCUSED\",\"reason\":\"Verified by reception\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/sessions/" + startedSession.getId() + "/roster")
                .header("Authorization", bearer(receptionist))).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/" + attendance.findByClassSessionIdAndMemberId(startedSession.getId(), member.getId()).orElseThrow().getId()
                + "/history").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].changedById").value(admin.getId()));
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"ABSENT\",\"reason\":\"Coach correction\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markedById").value(coachUser.getId()));
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
    void initialSubmissionIsCompleteAtomicAndIndividualEndpointCannotCreateFirstRow() throws Exception {
        User second = user("MEMBER", "ACTIVE");
        register(second, coachClass, CourseRegistrationStatus.CONFIRMED);

        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, attendance.count());

        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(coachUser))
                .contentType("application/json")
                .content("{\"members\":[{\"memberId\":" + member.getId() + ",\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isConflict());
        assertEquals(0, attendance.count(), "incomplete roster must not partially persist");

        String complete = "{\"members\":[{\"memberId\":" + member.getId()
                + ",\"status\":\"PRESENT\"},{\"memberId\":" + second.getId() + ",\"status\":\"ABSENT\"}]}";
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content(complete))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendanceCompleteness").value("COMPLETE"))
                .andExpect(jsonPath("$.eligibleStudentCount").value(2)).andExpect(jsonPath("$.recordedAttendanceCount").value(2));
        assertEquals(2, attendance.findByClassSessionId(startedSession.getId()).size());
        assertEquals(1, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(
                attendance.findByClassSessionIdAndMemberId(startedSession.getId(), second.getId()).orElseThrow().getId()).size());
        long absentNotices = notifications.findByUserId(second.getId(), PageRequest.of(0, 20)).getContent().stream()
                .filter(item -> item.getTitle().contains("điểm danh")).count();
        assertEquals(1, absentNotices);
        var absentNotice = notifications.findByUserId(second.getId(), PageRequest.of(0, 20)).getContent().stream()
                .filter(item -> item.getTitle().contains("điểm danh")).findFirst().orElseThrow();
        assertTrue(absentNotice.getMessage().contains("Vắng mặt"));
        assertTrue(absentNotice.getMessage().contains(coachClass.getName()));
        assertFalse(absentNotice.getMessage().contains("ABSENT"));
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content(complete)).andExpect(status().isOk());
        assertEquals(1, notifications.findByUserId(second.getId(), PageRequest.of(0, 20)).getContent().stream()
                .filter(item -> item.getTitle().contains("điểm danh")).count(), "unchanged retry does not notify twice");
    }

    @Test
    void historicalPartialCompletionPreservesExcusedRecordAndRejectsDuplicateOrUnexpectedIds() throws Exception {
        User second = user("MEMBER", "ACTIVE");
        register(second, coachClass, CourseRegistrationStatus.CONFIRMED);
        Attendance existing = new Attendance();
        existing.setClassSession(startedSession); existing.setMember(member); existing.setStatus(AttendanceStatus.EXCUSED);
        existing.setMarkedBy(admin); existing.setMarkedAt(now.minusMinutes(5)); existing.setNote("Historical approved absence");
        attendance.saveAndFlush(existing);
        appendInitialHistory(existing);
        LocalDateTime originalMarkedAt = existing.getMarkedAt();

        String duplicate = "{\"members\":[{\"memberId\":" + member.getId() + ",\"status\":\"EXCUSED\"},"
                + "{\"memberId\":" + member.getId() + ",\"status\":\"EXCUSED\"},"
                + "{\"memberId\":" + second.getId() + ",\"status\":\"ABSENT\"}]}";
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(admin))
                .contentType("application/json").content(duplicate)).andExpect(status().isBadRequest());
        String unexpected = "{\"members\":[{\"memberId\":" + member.getId() + ",\"status\":\"EXCUSED\"},"
                + "{\"memberId\":" + second.getId() + ",\"status\":\"ABSENT\"},"
                + "{\"memberId\":" + otherMember.getId() + ",\"status\":\"PRESENT\"}]}";
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(admin))
                .contentType("application/json").content(unexpected)).andExpect(status().isConflict());
        String valid = "{\"members\":[{\"memberId\":" + member.getId() + ",\"status\":\"EXCUSED\"},"
                + "{\"memberId\":" + second.getId() + ",\"status\":\"ABSENT\"}]}";
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(admin))
                .contentType("application/json").content(valid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceCompleteness").value("COMPLETE"));
        Attendance preserved = attendance.findById(existing.getId()).orElseThrow();
        assertEquals(AttendanceStatus.EXCUSED, preserved.getStatus());
        assertEquals(originalMarkedAt, preserved.getMarkedAt());
        assertEquals("Historical approved absence", preserved.getNote());
        assertEquals(1, histories.findByAttendanceIdOrderByChangedAtAscIdAsc(existing.getId()).size());
        assertEquals(2, attendance.findByClassSessionId(startedSession.getId()).size());
    }

    @Test
    void historicalRegistrationCancelledAfterSessionStartRemainsInEligibleRoster() throws Exception {
        User historicalMember = user("MEMBER", "ACTIVE");
        CourseRegistration registration = register(historicalMember, coachClass, CourseRegistrationStatus.CANCELLED);
        registration.setRegisteredAt(now.minusDays(10));
        registration.setCancelledAt(now.minusMinutes(20));
        registrations.saveAndFlush(registration);
        ClassSession session = session(coachClass, now.minusMinutes(30), now.minusMinutes(5), "COMPLETED");

        mvc.perform(get("/api/attendance/sessions/" + session.getId() + "/roster")
                .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibleStudentCount").value(2))
                .andExpect(jsonPath("$.members[?(@.memberId == " + historicalMember.getId() + ")].memberId")
                        .value(org.hamcrest.Matchers.hasItem(historicalMember.getId())));
    }

    @Test
    void memberStatisticsExposeElapsedUnrecordedSessionsWithoutCountingThemAsAbsent() throws Exception {
        ClassSession elapsed = session(coachClass, now.minusHours(3), now.minusHours(2), "COMPLETED");
        Attendance historical = new Attendance(); historical.setClassSession(elapsed); historical.setMember(member);
        historical.setStatus(AttendanceStatus.EXCUSED); historical.setMarkedBy(admin); historical.setMarkedAt(now.minusHours(1));
        attendance.saveAndFlush(historical);
        ClassSession unrecorded = session(coachClass, now.minusHours(6), now.minusHours(5), "COMPLETED");

        mvc.perform(get("/api/attendance/me/overview").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligibleElapsedCount").value(2))
                .andExpect(jsonPath("$[0].recordedElapsedCount").value(1))
                .andExpect(jsonPath("$[0].unrecordedElapsedCount").value(1))
                .andExpect(jsonPath("$[0].presentCount").value(0))
                .andExpect(jsonPath("$[0].excusedCount").value(1))
                .andExpect(jsonPath("$[0].unrecordedElapsedCount").value(1));
        mvc.perform(get("/api/attendance/me/overview").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void newAttendanceAllowsOnlyPresentAndAbsentAndRejectsEarlyOrCancelledSessions() throws Exception {
        ClassSession future = session(coachClass, now.plusMinutes(1), now.plusMinutes(45), "SCHEDULED");
        mvc.perform(put(markUrl(future, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());

        List<String> statuses = List.of("PRESENT", "ABSENT");
        for (String statusValue : statuses) {
            User rowMember = user("MEMBER", "ACTIVE");
            register(rowMember, coachClass, CourseRegistrationStatus.CONFIRMED);
            ClassSession completedSession = session(coachClass, now.minusHours(2), now.minusHours(1), "COMPLETED");
            mvc.perform(post(submitUrl(completedSession)).header("Authorization", bearer(coachUser))
                    .contentType("application/json").content(submissionBody(completedSession, rowMember, statusValue)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.attendanceCompleteness").value("COMPLETE"));
        }

        ClassSession excusedSession = session(coachClass, now.minusHours(2), now.minusHours(1), "COMPLETED");
        User excusedMember = user("MEMBER", "ACTIVE");
        register(excusedMember, coachClass, CourseRegistrationStatus.CONFIRMED);
        mvc.perform(post(submitUrl(excusedSession)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content(submissionBody(excusedSession, excusedMember, "EXCUSED")))
                .andExpect(status().isBadRequest());

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
        long notificationsBefore = notifications.findByUserId(member.getId(), PageRequest.of(0, 100)).getTotalElements();

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
        assertEquals(notificationsBefore + 1,
                notifications.findByUserId(member.getId(), PageRequest.of(0, 100)).getTotalElements(),
                "only the genuine status transition notifies the affected Member");
    }

    @Test
    void authorizedAdminAndCoachCanStillCorrectAttendanceWhenNoTrainingResultExists() throws Exception {
        markAsAdmin(startedSession, member, "PRESENT");
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"status\":\"ABSENT\",\"reason\":\"Roster correction\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABSENT"));
        mvc.perform(put(markUrl(startedSession, member)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"PRESENT\",\"reason\":\"Verified attendance\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRESENT"));

        ClassSession second = session(coachClass, now.minusHours(2), now.minusHours(1), "COMPLETED");
        User secondMember = user("MEMBER", "ACTIVE");
        register(secondMember, coachClass, CourseRegistrationStatus.CONFIRMED);
        markAsAdmin(second, secondMember, "PRESENT");
        mvc.perform(put(markUrl(second, secondMember)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"status\":\"EXCUSED\",\"reason\":\"Medical note\"}"))
                .andExpect(status().isBadRequest());
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
        User second = user("MEMBER", "ACTIVE");
        register(second, coachClass, CourseRegistrationStatus.CONFIRMED);
        AtomicInteger historyWrites = new AtomicInteger();
        doAnswer(invocation -> {
            if (historyWrites.incrementAndGet() == 2) throw new DataIntegrityViolationException("simulated later history write failure");
            return invocation.callRealMethod();
        }).when(historySpy).saveAndFlush(any());
        mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"members\":[{\"memberId\":" + member.getId()
                        + ",\"status\":\"ABSENT\"},{\"memberId\":" + second.getId() + ",\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, attendance.count());
        assertEquals(0, histories.count());
        assertEquals(0, notifications.findByUserId(member.getId(), PageRequest.of(0, 100)).getTotalElements());
        assertEquals(0, notifications.findByUserId(second.getId(), PageRequest.of(0, 100)).getTotalElements());
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
        return mvc.perform(post(submitUrl(startedSession)).header("Authorization", bearer(coachUser))
                .contentType("application/json").content(submissionBody(startedSession, member, "PRESENT")))
                .andReturn().getResponse().getStatus();
    }

    private void markAsAdmin(ClassSession session, User forMember, String statusValue) throws Exception {
        mvc.perform(post(submitUrl(session)).header("Authorization", bearer(admin))
                .contentType("application/json").content(submissionBody(session, forMember, statusValue)))
                .andExpect(status().isOk());
    }

    private String submitUrl(ClassSession session) {
        return "/api/attendance/sessions/" + session.getId() + "/submit";
    }

    private String submissionBody(ClassSession session, User selected, String statusValue) {
        String rows = registrations.findByCourseClassIdAndStatus(session.getCourseClass().getId(), CourseRegistrationStatus.CONFIRMED)
                .stream().map(registration -> "{\"memberId\":" + registration.getMember().getId()
                        + ",\"status\":\"" + (registration.getMember().getId().equals(selected.getId()) ? statusValue : "PRESENT")
                        + "\"}").collect(java.util.stream.Collectors.joining(","));
        return "{\"members\":[" + rows + "]}";
    }

    private void appendInitialHistory(Attendance record) {
        AttendanceHistory event = new AttendanceHistory();
        event.setAttendance(record); event.setOldStatus(null); event.setNewStatus(record.getStatus());
        event.setChangedBy(record.getMarkedBy()); event.setChangedAt(record.getMarkedAt());
        histories.saveAndFlush(event);
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
        result.setRegisteredAt(now.minusDays(1));
        result.setOriginalPrice(new BigDecimal("100.00"));
        result.setMembershipDiscount(new BigDecimal("0.00"));
        result.setFinalPrice(new BigDecimal("100.00"));
        return registrations.saveAndFlush(result);
    }

    private Attendance persistAttendance(ClassSession session, User forMember, AttendanceStatus status, User marker) {
        Attendance record = new Attendance();
        record.setClassSession(session); record.setMember(forMember); record.setStatus(status);
        record.setMarkedBy(marker); record.setMarkedAt(now);
        return attendance.saveAndFlush(record);
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
