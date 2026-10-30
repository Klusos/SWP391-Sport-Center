package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashSet;
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
import group5.example.sportCenter.dto.CreateClassSessionRequest;
import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.dto.UpdateClassRequest;
import group5.example.sportCenter.dto.UpdateClassSessionRequest;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class ClassSessionGenerationIntegrationTest {
    private static final LocalDate START = LocalDate.of(2027, 1, 4); // Monday
    private static final LocalDate END = LocalDate.of(2027, 1, 31); // Sunday

    @Autowired ClassSessionGenerationService generation;
    @Autowired ClassSessionService classSessions;
    @Autowired CourseClassService courseClassService;
    @Autowired FacilityService facilityService;
    @MockitoSpyBean ClassSessionRepository sessionRows;
    @Autowired CourseClassRepository classRows;
    @Autowired CourseOfferingRepository offeringRows;
    @Autowired CourseRepository courseRows;
    @Autowired SportRepository sportRows;
    @Autowired CoachRepository coachRows;
    @Autowired FacilityRepository facilityRows;
    @Autowired UserRepository userRows;
    @Autowired CoachSportRepository coachSportRows;
    @Autowired PersonalTrainingBookingRepository ptBookingRows;
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
        ptBookingRows.deleteAll();
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
        facility.setSports(new LinkedHashSet<>(List.of(sport)));
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
    void facilityCanPersistMultipleActiveSportsAndRejectsDuplicateOrInactiveAssignments() {
        Sport second = sport("Second sport");
        CreateFacilityRequest create = new CreateFacilityRequest();
        create.setSportIds(List.of(facility.getSport().getId(), second.getId()));
        create.setName("Multi-sport room " + UUID.randomUUID());
        create.setType("STUDIO");
        var response = facilityService.createFacility(create);
        assertEquals(2, response.getSports().size());

        Facility loaded = facilityRows.findById(response.getId()).orElseThrow();
        assertEquals(2, loaded.getSports().size());

        create.setSportIds(List.of(second.getId(), second.getId()));
        var duplicate = assertThrows(ResponseStatusException.class, () -> facilityService.createFacility(create));
        assertEquals(400, duplicate.getStatusCode().value());

        second.setStatus("INACTIVE");
        sportRows.saveAndFlush(second);
        create.setSportIds(List.of(second.getId()));
        var inactive = assertThrows(ResponseStatusException.class, () -> facilityService.createFacility(create));
        assertEquals(400, inactive.getStatusCode().value());
    }

    @Test
    void compatibleManualSessionSucceedsButIncompatibleManualAndGeneratedSessionsAreRejected() {
        ClassSessionGenerationRequest compatible = request(List.of(DayOfWeek.TUESDAY), LocalTime.of(10, 0), START.plusDays(1), 1, facility.getId());
        assertTrue(generation.preview(compatible).canGenerate());
        assertTrue(classSessions.create(createSession(START.atTime(10, 0), facility.getId())).id() > 0);
        assertEquals(1, generation.generate(compatible).createdCount());

        Sport otherSport = sport("Other sport");
        facility.setSport(otherSport);
        facility.setSports(new LinkedHashSet<>(List.of(otherSport)));
        facilityRows.saveAndFlush(facility);

        ClassSessionGenerationRequest incompatible = request(List.of(DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), START.plusDays(2), 1, facility.getId());
        var preview = generation.preview(incompatible);
        assertFalse(preview.canGenerate());
        assertTrue(preview.candidates().stream().flatMap(candidate -> candidate.conflicts().stream())
                .anyMatch(text -> text.contains("does not support")));
        assertThrows(IllegalArgumentException.class, () -> classSessions.create(
                createSession(START.plusDays(2).atTime(10, 0), facility.getId())));
        assertThrows(ResponseStatusException.class, () -> generation.generate(incompatible));
    }

    @Test
    void removingSportNeededByFutureSessionIsRejected() {
        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), START, 1, facility.getId());
        generation.generate(request);
        Sport other = sport("Another sport");

        UpdateFacilityRequest update = new UpdateFacilityRequest();
        update.setName(facility.getName());
        update.setType(facility.getType());
        update.setStatus("ACTIVE");
        update.setSportIds(List.of(other.getId()));
        var conflict = assertThrows(ResponseStatusException.class,
                () -> facilityService.updateFacility(facility.getId(), update));
        assertEquals(409, conflict.getStatusCode().value());
    }

    @Test
    void pendingPtBookingBlocksManualAndGeneratedClassSessions() {
        LocalDateTime start = START.atTime(10, 0);
        ptBooking(coach, start, start.plusHours(1), PersonalTrainingBookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(15));

        ClassSessionGenerationRequest request = request(List.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), START, 1, null);
        var preview = generation.preview(request);
        assertFalse(preview.canGenerate());
        assertTrue(preview.candidates().getFirst().conflicts().stream().anyMatch(text -> text.contains("PT Booking")));
        assertThrows(ResponseStatusException.class, () -> generation.generate(request));
        assertThrows(IllegalArgumentException.class,
                () -> classSessions.create(createSession(start, null)));
    }

    @Test
    void reschedulingAnExistingClassSessionIntoAnActivePtBookingIsRejected() {
        LocalDateTime originalStart = START.atTime(10, 0);
        ClassSession existing = sessionRows.saveAndFlush(
                session(originalStart, originalStart.plusHours(1), "SCHEDULED", null));
        Integer existingId = existing.getId();
        LocalDateTime conflictStart = START.plusDays(7).atTime(10, 0);
        ptBooking(coach, conflictStart, conflictStart.plusHours(1), PersonalTrainingBookingStatus.CONFIRMED, null);

        UpdateClassSessionRequest update = new UpdateClassSessionRequest();
        update.setClassId(courseClass.getId());
        update.setStartTime(conflictStart);
        update.setEndTime(conflictStart.plusHours(1));

        assertThrows(IllegalArgumentException.class, () -> classSessions.update(existingId, update));
    }

    @Test
    void adjacentPtAndClassTimesAreAllowedAndInactivePtBookingsDoNotBlock() {
        LocalDateTime classStart = START.atTime(11, 0);
        ptBooking(coach, START.atTime(10, 0), classStart, PersonalTrainingBookingStatus.CONFIRMED, null);
        ptBooking(coach, classStart, classStart.plusHours(1), PersonalTrainingBookingStatus.CANCELLED, null);
        ptBooking(coach, classStart, classStart.plusHours(1), PersonalTrainingBookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusSeconds(1));

        var preview = generation.preview(request(List.of(DayOfWeek.MONDAY), LocalTime.of(11, 0), START, 1, null));
        assertTrue(preview.canGenerate());
        assertEquals(1, generation.generate(request(List.of(DayOfWeek.MONDAY), LocalTime.of(11, 0), START, 1, null))
                .createdCount());
    }

    @Test
    void coachReassignmentRejectsConflictingPtBookingButAllowsConflictFreeCoach() {
        Coach target = qualifiedCoach("reassign-conflict");
        LocalDateTime start = START.atTime(10, 0);
        ClassSession existing = session(start, start.plusHours(1), "SCHEDULED", null);
        sessionRows.saveAndFlush(existing);
        ptBooking(target, start, start.plusHours(1), PersonalTrainingBookingStatus.CONFIRMED, null);

        UpdateClassRequest update = updateClass(target, "Reassigned class");
        assertThrows(IllegalArgumentException.class, () -> courseClassService.updateClass(courseClass.getId(), update));

        Coach clearCoach = qualifiedCoach("reassign-clear");
        var result = courseClassService.updateClass(courseClass.getId(), updateClass(clearCoach, "Reassigned class"));
        assertEquals(clearCoach.getId(), result.getCoachId());
    }

    private Coach qualifiedCoach(String suffix) {
        User user = user("COACH");
        user.setName(suffix);
        userRows.saveAndFlush(user);
        Coach candidate = new Coach();
        candidate.setUser(user);
        candidate.setStatus("ACTIVE");
        candidate = coachRows.saveAndFlush(candidate);
        CoachSport qualification = new CoachSport();
        qualification.setCoach(candidate);
        qualification.setSport(offering.getCourse().getSport());
        qualification.setExperienceYears(1);
        coachSportRows.saveAndFlush(qualification);
        return candidate;
    }

    private PersonalTrainingBooking ptBooking(Coach assignedCoach, LocalDateTime start, LocalDateTime end,
            PersonalTrainingBookingStatus status, LocalDateTime paymentDeadline) {
        PersonalTrainingBooking booking = new PersonalTrainingBooking();
        booking.setMember(member);
        booking.setCoach(assignedCoach);
        booking.setSport(offering.getCourse().getSport());
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setOriginalPrice(new BigDecimal("100.00"));
        booking.setDiscount(BigDecimal.ZERO);
        booking.setFinalPrice(new BigDecimal("100.00"));
        booking.setStatus(status);
        booking.setPaymentExpiresAt(paymentDeadline);
        return ptBookingRows.saveAndFlush(booking);
    }

    private UpdateClassRequest updateClass(Coach assignedCoach, String name) {
        UpdateClassRequest request = new UpdateClassRequest();
        request.setCoachId(assignedCoach.getId());
        request.setName(name);
        request.setCapacity(courseClass.getCapacity());
        request.setStatus("ACTIVE");
        return request;
    }

    @Test
    void facilityAssignmentMutationRemainsAdminOnly() throws Exception {
        Sport other = sport("Auth sport");
        String body = "{\"sportIds\":[" + facility.getSport().getId() + "," + other.getId()
                + "],\"name\":\"Authorized room\",\"type\":\"STUDIO\"}";
        mvc.perform(post("/api/facilities").header("Authorization", bearer(admin))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sports[1].id").exists());
        for (User user : List.of(receptionist, member, coachUser)) {
            mvc.perform(post("/api/facilities").header("Authorization", bearer(user))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
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

    private CreateClassSessionRequest createSession(LocalDateTime start, Integer facilityId) {
        CreateClassSessionRequest request = new CreateClassSessionRequest();
        request.setClassId(courseClass.getId());
        request.setFacilityId(facilityId);
        request.setStartTime(start);
        request.setEndTime(start.plusMinutes(offering.getSessionDuration()));
        request.setStatus("SCHEDULED");
        return request;
    }

    private Sport sport(String prefix) {
        Sport sport = new Sport();
        sport.setName(prefix + " " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        return sportRows.saveAndFlush(sport);
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
