package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ClassSessionGenerationRequest;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class ClassSessionGenerationIntegrationTest {
    private static final LocalDate START = LocalDate.of(2027, 1, 4); // Monday
    private static final LocalDate END = LocalDate.of(2027, 1, 31); // Sunday

    @Autowired ClassSessionGenerationService generation;
    @Autowired ClassSessionService classSessions;
    @MockitoSpyBean ClassSessionRepository sessionRows;
    @Autowired CourseClassRepository classRows;
    @Autowired CourseOfferingRepository offeringRows;
    @Autowired CourseRepository courseRows;
    @Autowired SportRepository sportRows;
    @Autowired CoachRepository coachRows;
    @Autowired FacilityRepository facilityRows;
    @Autowired UserRepository userRows;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private MockMvc mvc;
    private CourseClass courseClass;
    private CourseOffering offering;
    private Coach coach;
    private Facility facility;
    private User admin;
    private User receptionist;
    private User member;
    private User coachUser;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        admin = user("ADMIN");
        receptionist = user("RECEPTIONIST");
        member = user("MEMBER");
        coachUser = user("COACH");

        Sport sport = new Sport();
        sport.setName("Generation sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sportRows.saveAndFlush(sport);

        Course course = new Course();
        course.setSport(sport);
        course.setName("Generation course " + UUID.randomUUID());
        course.setStatus("ACTIVE");
        course = courseRows.saveAndFlush(course);

        offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Generation offering " + UUID.randomUUID());
        offering.setPrice(new BigDecimal("100000.00"));
        offering.setPlannedSessions(8);
        offering.setSessionDuration(60);
        offering.setStartDate(START);
        offering.setEndDate(END);
        offering.setRegistrationDeadline(START.atStartOfDay().minusDays(30));
        offering.setCancellationDeadline(START.atStartOfDay().minusDays(20));
        offering.setStatus("OPEN");
        offering = offeringRows.saveAndFlush(offering);

        coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coachRows.saveAndFlush(coach);

        facility = new Facility();
        facility.setSport(sport);
        facility.setName("Generation facility " + UUID.randomUUID());
        facility.setType("COURT");
        facility.setStatus("ACTIVE");
        facility = facilityRows.saveAndFlush(facility);

        courseClass = new CourseClass();
        courseClass.setCourseOffering(offering);
        courseClass.setCoach(coach);
        courseClass.setName("Generation class " + UUID.randomUUID());
        courseClass.setCapacity(20);
        courseClass.setStatus("ACTIVE");
        courseClass = classRows.saveAndFlush(courseClass);
    }

    @Test
    void generatesWeeklyDatesChronologicallyWithPersistedOfferingDurationAndInclusiveBoundaries() {
        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(10, 0), START, 4, null);
        var preview = generation.preview(request);
        assertTrue(preview.canGenerate());
        assertEquals(List.of(START.atTime(10, 0), START.plusDays(2).atTime(10, 0),
                START.plusDays(7).atTime(10, 0), START.plusDays(9).atTime(10, 0)),
                preview.candidates().stream().map(c -> c.startTime()).toList());
        assertTrue(preview.candidates().stream().allMatch(c -> c.endTime().equals(c.startTime().plusMinutes(60))));

        var result = generation.generate(request);
        assertEquals(4, result.createdCount());
        assertEquals(4L, sessionRows.countByCourseClassIdAndStatusNot(courseClass.getId(), "CANCELLED"));

        ClassSessionGenerationRequest boundary = request(List.of(DayOfWeek.SUNDAY), LocalTime.of(22, 0), END, 1, null);
        assertTrue(generation.preview(boundary).canGenerate());
    }

    @Test
    void reportsInsufficientDatesAndRejectsCountsAboveRemainingTarget() {
        ClassSessionGenerationRequest tooMany = request(List.of(DayOfWeek.MONDAY), LocalTime.NOON, START, 9, null);
        var preview = generation.preview(tooMany);
        assertFalse(preview.canGenerate());
        assertTrue(preview.conflicts().stream().anyMatch(text -> text.contains("planned-session slots")));
        assertThrows(ResponseStatusException.class, () -> generation.generate(tooMany));

        ClassSessionGenerationRequest insufficient = request(List.of(DayOfWeek.SUNDAY), LocalTime.NOON,
                END.minusDays(1), 2, null);
        var shortPreview = generation.preview(insufficient);
        assertFalse(shortPreview.canGenerate());
        assertTrue(shortPreview.conflicts().stream().anyMatch(text -> text.contains("Only 1 matching weekdays")));
    }

    @Test
    void cancelledRowsDoNotConsumeSlotsOrCauseOverlapButScheduledRowsBlockThePreview() {
        ClassSession cancelled = session(START.atTime(10, 0), START.atTime(11, 0), "CANCELLED", facility);
        sessionRows.saveAndFlush(cancelled);
        assertEquals(0L, sessionRows.countByCourseClassIdAndStatusNot(courseClass.getId(), "CANCELLED"));

        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), START, 1, facility.getId());
        assertTrue(generation.preview(request).canGenerate());

        ClassSession conflicting = session(START.plusDays(7).atTime(10, 0), START.plusDays(7).atTime(11, 0),
                "SCHEDULED", facility);
        sessionRows.saveAndFlush(conflicting);
        var conflictPreview = generation.preview(request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0),
                START, 2, facility.getId()));
        assertFalse(conflictPreview.canGenerate());
        assertTrue(conflictPreview.candidates().stream().anyMatch(candidate -> !candidate.conflicts().isEmpty()));
        assertThrows(ResponseStatusException.class, () -> generation.generate(
                request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), START, 2, facility.getId())));
        assertEquals(2, sessionRows.findByCourseClassId(courseClass.getId()).size());
    }

    @Test
    void facilityMustBeActiveAndDurationCannotBeOverriddenByRequest() {
        facility.setStatus("INACTIVE");
        facilityRows.saveAndFlush(facility);
        var inactivePreview = generation.preview(request(List.of(DayOfWeek.MONDAY), LocalTime.NOON,
                START, 1, facility.getId()));
        assertFalse(inactivePreview.canGenerate());
        assertTrue(inactivePreview.candidates().getFirst().conflicts().stream()
                .anyMatch(text -> text.contains("ACTIVE facilities")));
        facility.setStatus("ACTIVE");
        facilityRows.saveAndFlush(facility);

        var preview = generation.preview(request(List.of(DayOfWeek.MONDAY), LocalTime.NOON, START, 1, null));
        assertEquals(60, preview.sessionDurationMinutes());
        assertEquals(60, java.time.Duration.between(preview.candidates().getFirst().startTime(),
                preview.candidates().getFirst().endTime()).toMinutes());
    }

    @Test
    void confirmRevalidatesAfterPreviewAndAdminOnlyEndpointsRejectOtherRoles() throws Exception {
        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), START, 1, null);
        assertTrue(generation.preview(request).canGenerate());
        sessionRows.saveAndFlush(session(START.atTime(10, 0), START.atTime(11, 0), "SCHEDULED", null));
        assertThrows(ResponseStatusException.class, () -> generation.generate(request));

        String body = "{\"courseClassId\":" + courseClass.getId()
                + ",\"weekdays\":[\"MONDAY\"],\"startTime\":\"10:00:00\",\"firstEligibleDate\":\"2027-01-04\",\"requestedSessionCount\":1}";
        mvc.perform(post("/api/class-sessions/generation/preview").header("Authorization", bearer(admin))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/class-sessions/generation/preview")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/class-sessions/generation/confirm")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        for (User user : List.of(receptionist, coachUser, member)) {
            mvc.perform(post("/api/class-sessions/generation/preview").header("Authorization", bearer(user))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/class-sessions/generation/confirm").header("Authorization", bearer(user))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/class-sessions/generation/confirm").header("Authorization", bearer(admin))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRepeatedWeekdaysAndRollsBackWhenPersistenceFailsMidBatch() {
        ClassSessionGenerationRequest repeated = request(List.of(DayOfWeek.MONDAY, DayOfWeek.MONDAY),
                LocalTime.of(9, 0), START, 2, null);
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> generation.preview(repeated));
        assertEquals(400, invalid.getStatusCode().value());

        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) {
                throw new DataIntegrityViolationException("simulated batch insert failure");
            }
            ClassSession session = invocation.getArgument(0);
            entityManager.persist(session);
            return session;
        }).when(sessionRows).save(any(ClassSession.class));

        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(9, 0), START, 2, null);
        assertThrows(DataIntegrityViolationException.class, () -> generation.generate(request));
        assertEquals(0L, sessionRows.countByCourseClassIdAndStatusNot(courseClass.getId(), "CANCELLED"));
    }

    @Test
    void concurrentConfirmationsCannotExceedThePerClassTarget() throws Exception {
        offering.setPlannedSessions(3);
        offeringRows.saveAndFlush(offering);
        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(9, 0), START, 3, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> generation.generate(request).createdCount();
            List<Future<Integer>> futures = executor.invokeAll(List.of(call, call));
            int successes = 0;
            int rejected = 0;
            for (Future<Integer> future : futures) {
                try { successes += future.get(); }
                catch (java.util.concurrent.ExecutionException exception) { rejected++; }
            }
            assertEquals(1, rejected);
            assertEquals(3, successes);
            assertEquals(3L, sessionRows.countByCourseClassIdAndStatusNot(courseClass.getId(), "CANCELLED"));
        }
    }

    private ClassSessionGenerationRequest request(List<DayOfWeek> weekdays, LocalTime time,
            LocalDate first, Integer count, Integer facilityId) {
        ClassSessionGenerationRequest request = new ClassSessionGenerationRequest();
        request.setCourseClassId(courseClass.getId());
        request.setWeekdays(weekdays);
        request.setStartTime(time);
        request.setFirstEligibleDate(first);
        request.setRequestedSessionCount(count);
        request.setFacilityId(facilityId);
        return request;
    }

    private ClassSession session(LocalDateTime start, LocalDateTime end, String status, Facility assignedFacility) {
        ClassSession session = new ClassSession();
        session.setCourseClass(courseClass);
        session.setFacility(assignedFacility);
        session.setStartTime(start);
        session.setEndTime(end);
        session.setStatus(status);
        return session;
    }

    private User user(String role) {
        User user = new User();
        user.setName("Schedule " + role);
        user.setEmail(UUID.randomUUID() + "@schedule.example.test");
        user.setPasswordHash("test-only");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return userRows.saveAndFlush(user);
    }

    private String bearer(User user) { return "Bearer " + jwtService.generateToken(user); }
}
