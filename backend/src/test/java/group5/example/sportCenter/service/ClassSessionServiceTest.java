package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ClassSessionResponse;
import group5.example.sportCenter.dto.CreateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionStatusRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.FacilityRepository;

@ExtendWith(MockitoExtension.class)
class ClassSessionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-11T12:00:00");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private ClassSessionRepository classSessionRepository;
    @Mock
    private CourseClassRepository courseClassRepository;
    @Mock
    private FacilityRepository facilityRepository;
    private ClassSessionService service;

    private CourseClass courseClass;
    private Facility facility;

    @BeforeEach
    void setUp() {
        service = new ClassSessionService(classSessionRepository, courseClassRepository, facilityRepository, FIXED_CLOCK);

        Sport sport = new Sport();
        sport.setId(4);
        Course course = new Course();
        course.setSport(sport);
        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setStartDate(LocalDate.now().minusDays(10));
        offering.setEndDate(LocalDate.now().plusDays(10));
        offering.setPlannedSessions(10);

        Coach coach = new Coach();
        coach.setId(9);
        courseClass = new CourseClass();
        courseClass.setId(3);
        courseClass.setName("Morning class");
        courseClass.setCourseOffering(offering);
        courseClass.setCoach(coach);

        facility = new Facility();
        facility.setId(6);
        facility.setName("Main court");
        facility.setStatus("ACTIVE");
    }

    @Test
    void createsScheduledSessionByDefault() {
        stubAvailableClass();
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.create(request(null, null,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1)));

        assertEquals("SCHEDULED", response.status());
        assertTrue(response.warnings().isEmpty());
    }

    @Test
    void allowsActiveFacilityAndKeepsOverlapChecks() {
        stubAvailableClass();
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                org.mockito.ArgumentMatchers.eq(6), org.mockito.ArgumentMatchers.eq("CANCELLED"),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.create(request(6, null,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1)));

        assertEquals(6, response.facilityId());
        verify(classSessionRepository).existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                org.mockito.ArgumentMatchers.eq(6), org.mockito.ArgumentMatchers.eq("CANCELLED"),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void rejectsInactiveFacilityOnCreate() {
        stubAvailableClass();
        facility.setStatus("INACTIVE");
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.create(request(6, null,
                        LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1))));

        assertTrue(exception.getMessage().contains("ACTIVE"));
    }

    @Test
    void permitsNullFacilityOnCreate() {
        stubAvailableClass();
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.create(request(null, null,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1)));

        assertEquals(null, response.facilityId());
    }

    @Test
    void rejectsMissingFacilityOnCreateWithNotFound() {
        stubAvailableClass();
        when(facilityRepository.findById(77)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(request(77, null,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1))));
    }

    @Test
    void rejectsFacilityOverlap() {
        stubAvailableClass();
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                6, "CANCELLED", LocalDateTime.parse("2026-10-12T11:00:00"),
                LocalDateTime.parse("2026-10-12T10:00:00"))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(request(6, null,
                LocalDateTime.parse("2026-10-12T10:00:00"), LocalDateTime.parse("2026-10-12T11:00:00"))));
    }

    @Test
    void checksCoachOverlapEvenWithoutFacility() {
        stubAvailableClass();
        when(classSessionRepository.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                9, "CANCELLED", LocalDateTime.parse("2026-10-12T11:00:00"),
                LocalDateTime.parse("2026-10-12T10:00:00"))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(request(null, null,
                LocalDateTime.parse("2026-10-12T10:00:00"), LocalDateTime.parse("2026-10-12T11:00:00"))));
    }

    @Test
    void allowsSessionsThatOnlyTouchAtTheBoundary() {
        stubAvailableClass();
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                6, "CANCELLED", LocalDateTime.parse("2026-10-12T11:00:00"),
                LocalDateTime.parse("2026-10-12T10:00:00"))).thenReturn(false);
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.create(request(6, null,
                LocalDateTime.parse("2026-10-12T10:00:00"), LocalDateTime.parse("2026-10-12T11:00:00")));

        assertEquals("SCHEDULED", response.status());
        verify(classSessionRepository).existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                6, "CANCELLED", LocalDateTime.parse("2026-10-12T11:00:00"),
                LocalDateTime.parse("2026-10-12T10:00:00"));
    }

    @Test
    void allowsMakeUpSessionOutsideOfferingAndReturnsWarning() {
        stubAvailableClass();
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(11L);

        ClassSessionResponse response = service.create(request(null, null,
                LocalDate.now().plusDays(30).atTime(10, 0), LocalDate.now().plusDays(30).atTime(11, 0)));

        assertEquals("SCHEDULED", response.status());
        assertEquals(2, response.warnings().size());
        assertTrue(response.warnings().get(0).contains("outside"));
        assertTrue(response.warnings().get(1).contains("plannedSessions"));
    }

    @Test
    void updateIgnoresCurrentSessionWhenCheckingOverlap() {
        ClassSession existing = new ClassSession();
        existing.setId(12);
        existing.setCourseClass(courseClass);
        existing.setStartTime(LocalDateTime.parse("2026-10-12T10:00:00"));
        existing.setEndTime(LocalDateTime.parse("2026-10-12T11:00:00"));
        existing.setStatus("SCHEDULED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(courseClassRepository.findById(3)).thenReturn(Optional.of(courseClass));
        when(classSessionRepository.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                9, "CANCELLED", LocalDateTime.parse("2026-10-12T11:00:00"),
                LocalDateTime.parse("2026-10-12T10:00:00"), 12)).thenReturn(false);
        when(classSessionRepository.save(existing)).thenReturn(existing);
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        UpdateClassSessionRequest request = new UpdateClassSessionRequest();
        request.setClassId(3);
        request.setStartTime(existing.getStartTime());
        request.setEndTime(existing.getEndTime());
        service.update(12, request);

        verify(classSessionRepository).existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                9, "CANCELLED", existing.getEndTime(), existing.getStartTime(), 12);
    }

    @Test
    void updateFailsWhenCourseClassDoesNotExist() {
        ClassSession existing = new ClassSession();
        existing.setId(12);
        existing.setStatus("SCHEDULED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(courseClassRepository.findById(77)).thenReturn(Optional.empty());

        UpdateClassSessionRequest request = new UpdateClassSessionRequest();
        request.setClassId(77);
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));

        assertThrows(ResourceNotFoundException.class, () -> service.update(12, request));
    }

    @Test
    void rejectsChangingToInactiveFacilityOnUpdate() {
        ClassSession existing = scheduledSession(12, facilityWithId(5, "ACTIVE"));
        Facility inactiveFacility = facilityWithId(6, "INACTIVE");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(courseClassRepository.findById(3)).thenReturn(Optional.of(courseClass));
        when(facilityRepository.findById(6)).thenReturn(Optional.of(inactiveFacility));

        UpdateClassSessionRequest request = updateRequest(3, 6,
                LocalDateTime.now().plusDays(2).withNano(0), LocalDateTime.now().plusDays(2).withNano(0).plusHours(1));

        assertThrows(IllegalArgumentException.class, () -> service.update(12, request));
    }

    @Test
    void rejectsKeepingInactiveFacilityForFutureScheduledSessionWithGuidance() {
        Facility inactiveFacility = facilityWithId(6, "INACTIVE");
        ClassSession existing = scheduledSession(12, inactiveFacility);
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(courseClassRepository.findById(3)).thenReturn(Optional.of(courseClass));
        when(facilityRepository.findById(6)).thenReturn(Optional.of(inactiveFacility));

        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        UpdateClassSessionRequest request = updateRequest(3, 6, start, start.plusHours(1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(12, request));

        assertTrue(exception.getMessage().contains("select another ACTIVE facility or cancel"));
    }

    @Test
    void canReadHistoricalSessionWithInactiveFacility() {
        Facility inactiveFacility = facilityWithId(6, "INACTIVE");
        ClassSession historical = scheduledSession(12, inactiveFacility);
        historical.setStartTime(LocalDateTime.now().minusDays(2));
        historical.setEndTime(LocalDateTime.now().minusDays(2).plusHours(1));
        historical.setStatus("COMPLETED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(historical));
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.getById(12);

        assertEquals(6, response.facilityId());
        assertEquals("Main court", response.facilityName());
    }

    @Test
    void rejectsMarkingAFutureSessionCompleted() {
        ClassSession existing = scheduledSession(12, null);
        existing.setEndTime(NOW.plusNanos(1));
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(12, statusRequest("COMPLETED")));
    }

    @Test
    void doesNotAllowRestoringACancelledSession() {
        ClassSession existing = new ClassSession();
        existing.setId(12);
        existing.setStatus("CANCELLED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(12, statusRequest("SCHEDULED")));
    }

    @Test
    void completedSessionCannotBeCancelled() {
        ClassSession existing = new ClassSession();
        existing.setId(12);
        existing.setStatus("COMPLETED");
        existing.setEndTime(LocalDateTime.now().minusHours(1));
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(12, statusRequest("CANCELLED")));
    }

    @Test
    void scheduledSessionCanBeCancelledBeforeStart() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStartTime(NOW.plusNanos(1));
        existing.setEndTime(NOW.plusHours(1));
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(classSessionRepository.save(existing)).thenReturn(existing);

        ClassSessionResponse response = service.updateStatus(12, statusRequest("CANCELLED"));

        assertEquals("CANCELLED", response.status());
    }

    @Test
    void scheduledSessionCannotBeCancelledAtOrAfterStart() {
        for (LocalDateTime startTime : List.of(NOW, NOW.minusNanos(1))) {
            ClassSession existing = scheduledSession(12, null);
            existing.setStartTime(startTime);
            existing.setEndTime(startTime.plusHours(1));
            when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

            assertThrows(IllegalArgumentException.class,
                    () -> service.updateStatus(12, statusRequest("CANCELLED")));
        }
    }

    @Test
    void scheduledSessionCanBeCompletedExactlyAtEndTime() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStartTime(NOW.minusHours(1));
        existing.setEndTime(NOW);
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(classSessionRepository.save(existing)).thenReturn(existing);
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.updateStatus(12, statusRequest("COMPLETED"));

        assertEquals("COMPLETED", response.status());
    }

    @Test
    void scheduledSessionCannotBeCompletedBeforeEndTime() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStartTime(NOW.minusHours(1));
        existing.setEndTime(NOW.plusNanos(1));
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(12, statusRequest("COMPLETED")));
    }

    @Test
    void normalizesValidStatusInputToUppercase() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStartTime(NOW.minusHours(1));
        existing.setEndTime(NOW);
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));
        when(classSessionRepository.save(existing)).thenReturn(existing);
        when(classSessionRepository.countByCourseClassIdAndStatusNot(3, "CANCELLED")).thenReturn(1L);

        ClassSessionResponse response = service.updateStatus(12, statusRequest("completed"));

        assertEquals("COMPLETED", response.status());
    }

    @Test
    void completedSessionCannotReturnToScheduled() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStatus("COMPLETED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(12, statusRequest("SCHEDULED")));
    }

    @Test
    void cancelledSessionCannotMoveToCompleted() {
        ClassSession existing = scheduledSession(12, null);
        existing.setStatus("CANCELLED");
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(12, statusRequest("COMPLETED")));
    }

    @Test
    void terminalSessionsCannotBeRescheduled() {
        for (String status : List.of("COMPLETED", "CANCELLED")) {
            ClassSession existing = scheduledSession(12, null);
            existing.setStatus(status);
            when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

            UpdateClassSessionRequest request = updateRequest(3, null,
                    NOW.plusDays(3), NOW.plusDays(3).plusHours(1));
            assertThrows(IllegalArgumentException.class, () -> service.update(12, request));
        }
    }

    @Test
    void rejectsInvalidStatusAsBadRequest() {
        ClassSession existing = scheduledSession(12, null);
        when(classSessionRepository.findById(12)).thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(12, statusRequest("BANANA")));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsInvalidScheduleRangeAsBadRequest() {
        CreateClassSessionRequest request = request(null, null, NOW.plusHours(1), NOW.plusHours(1));
        when(courseClassRepository.findById(3)).thenReturn(Optional.of(courseClass));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(request));

        assertEquals(400, exception.getStatusCode().value());
    }

    private void stubAvailableClass() {
        when(courseClassRepository.findById(3)).thenReturn(Optional.of(courseClass));
        lenient().when(classSessionRepository.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                org.mockito.ArgumentMatchers.eq(9), org.mockito.ArgumentMatchers.eq("CANCELLED"),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
    }

    private CreateClassSessionRequest request(
            Integer facilityId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        CreateClassSessionRequest request = new CreateClassSessionRequest();
        request.setClassId(3);
        request.setFacilityId(facilityId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setStatus(status);
        return request;
    }

    private Facility facilityWithId(Integer id, String status) {
        Facility result = new Facility();
        result.setId(id);
        result.setName("Main court");
        result.setStatus(status);
        return result;
    }

    private ClassSession scheduledSession(Integer id, Facility assignedFacility) {
        ClassSession session = new ClassSession();
        session.setId(id);
        session.setCourseClass(courseClass);
        session.setFacility(assignedFacility);
        session.setStartTime(LocalDateTime.now().plusDays(2).withNano(0));
        session.setEndTime(session.getStartTime().plusHours(1));
        session.setStatus("SCHEDULED");
        return session;
    }

    private UpdateClassSessionRequest updateRequest(
            Integer classId, Integer facilityId, LocalDateTime startTime, LocalDateTime endTime) {
        UpdateClassSessionRequest request = new UpdateClassSessionRequest();
        request.setClassId(classId);
        request.setFacilityId(facilityId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        return request;
    }

    private UpdateClassSessionStatusRequest statusRequest(String status) {
        UpdateClassSessionStatusRequest request = new UpdateClassSessionStatusRequest();
        request.setStatus(status);
        return request;
    }
}
