package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.UpdateCourseOfferingRequest;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;

@ExtendWith(MockitoExtension.class)
class CourseOfferingServiceTest {
    @Mock private CourseOfferingRepository offerings;
    @Mock private CourseRepository courses;
    @Mock private ClassSessionRepository sessions;

    private CourseOfferingService service;
    private CourseOffering offering;
    private UpdateCourseOfferingRequest request;

    @BeforeEach
    void setUp() {
        service = new CourseOfferingService(offerings, courses, sessions,
                Clock.fixed(LocalDateTime.parse("2026-01-01T00:00:00").toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Sport sport = new Sport();
        sport.setId(7);
        sport.setName("Swimming");
        Course course = new Course();
        course.setId(9);
        course.setName("Basics");
        course.setSport(sport);
        offering = new CourseOffering();
        offering.setId(11);
        offering.setCourse(course);
        offering.setName("Spring term");
        offering.setPrice(new BigDecimal("100.00"));
        offering.setPlannedSessions(8);
        offering.setSessionDuration(60);
        offering.setStartDate(LocalDate.of(2027, 1, 10));
        offering.setEndDate(LocalDate.of(2027, 2, 10));
        offering.setRegistrationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        offering.setCancellationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        offering.setStatus("OPEN");
        lenient().when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));

        request = new UpdateCourseOfferingRequest();
        request.setName("Spring term updated");
        request.setPrice(new BigDecimal("100.00"));
        request.setPlannedSessions(java.math.BigDecimal.valueOf(8));
        request.setSessionDuration(java.math.BigDecimal.valueOf(60));
        request.setStartDate(LocalDate.of(2027, 1, 10));
        request.setEndDate(LocalDate.of(2027, 2, 10));
        request.setRegistrationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        request.setCancellationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        request.setStatus("OPEN");
    }

    @Test
    void rejectsOfferingDateUpdateThatWouldExcludeExistingSessions() {
        request.setStartDate(LocalDate.of(2027, 1, 11));
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(sessions.existsOutsideOfferingRange(11,
                LocalDate.of(2027, 1, 11).atStartOfDay(),
                LocalDate.of(2027, 2, 11).atStartOfDay())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request));

        assertEquals(400, error.getStatusCode().value());
        verify(offerings, never()).save(offering);
    }

    @Test
    void allowsHistoricalOfferingWhenDatesAndDeadlinesRemainUnchanged() {
        offering.setStartDate(LocalDate.of(2025, 1, 10));
        offering.setEndDate(LocalDate.of(2025, 2, 10));
        offering.setRegistrationDeadline(LocalDateTime.of(2025, 1, 8, 12, 0));
        offering.setCancellationDeadline(LocalDateTime.of(2025, 1, 9, 12, 0));
        request.setStartDate(offering.getStartDate());
        request.setEndDate(offering.getEndDate());
        request.setRegistrationDeadline(offering.getRegistrationDeadline());
        request.setCancellationDeadline(offering.getCancellationDeadline());
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.existsOutsideOfferingRange(any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> service.updateOffering(11, request));
    }

    @Test
    void rejectsRegistrationDeadlineAfterCancellationDeadline() {
        request.setRegistrationDeadline(LocalDateTime.of(2027, 1, 9, 13, 0));
        request.setCancellationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsChangingOfferingDurationWhenExistingSessionWouldMismatch() {
        request.setSessionDuration(java.math.BigDecimal.valueOf(90));
        ClassSession existing = new ClassSession();
        existing.setStartTime(LocalDateTime.of(2027, 1, 12, 9, 0));
        existing.setEndTime(LocalDateTime.of(2027, 1, 12, 10, 0));
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(sessions.findByCourseClass_CourseOffering_Id(11)).thenReturn(java.util.List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request));

        assertEquals(400, error.getStatusCode().value());
        verify(offerings, never()).save(offering);
    }

    @Test
    void rejectsCreatingOfferingWithPastStartOrExpiredDeadlines() {
        var create = createRequest(10, 60);
        Course course = offering.getCourse();
        course.setStatus("ACTIVE");
        when(courses.findById(9)).thenReturn(Optional.of(course));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.createOffering(create));
        create.setStartDate(LocalDate.of(2025, 1, 10));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());

        create.setStartDate(LocalDate.of(2027, 1, 10));
        create.setRegistrationDeadline(LocalDateTime.of(2025, 1, 8, 12, 0));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());

        create.setRegistrationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        create.setCancellationDeadline(LocalDateTime.of(2027, 1, 10, 1, 0));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());

        create.setCancellationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        create.setRegistrationDeadline(LocalDateTime.of(2027, 1, 9, 13, 0));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());
    }

    @Test
    void createAcceptsBothNumericBoundariesAndRejectsValuesOutsideThem() {
        var create = createRequest(1, 30);
        prepareCreate();
        assertDoesNotThrow(() -> service.createOffering(create));

        create.setPlannedSessions(java.math.BigDecimal.valueOf(60));
        create.setSessionDuration(java.math.BigDecimal.valueOf(180));
        assertDoesNotThrow(() -> service.createOffering(create));

        for (Integer planned : new Integer[] { null, 0, 61 }) {
            create.setPlannedSessions(planned == null ? null : java.math.BigDecimal.valueOf(planned));
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.createOffering(create)).getStatusCode().value(), "plannedSessions=" + planned);
        }
        create.setPlannedSessions(java.math.BigDecimal.valueOf(8));
        for (Integer duration : new Integer[] { null, 29, 181 }) {
            create.setSessionDuration(duration == null ? null : java.math.BigDecimal.valueOf(duration));
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.createOffering(create)).getStatusCode().value(), "sessionDuration=" + duration);
        }
        create.setPlannedSessions(new BigDecimal("1.5"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());
        create.setPlannedSessions(java.math.BigDecimal.valueOf(8));
        create.setSessionDuration(new BigDecimal("30.5"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.createOffering(create)).getStatusCode().value());
    }

    @Test
    void updateAcceptsNumericBoundariesAndRejectsInvalidChangedValues() {
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.existsOutsideOfferingRange(any(), any(), any())).thenReturn(false);

        request.setPlannedSessions(java.math.BigDecimal.valueOf(1));
        request.setSessionDuration(java.math.BigDecimal.valueOf(30));
        assertDoesNotThrow(() -> service.updateOffering(11, request));
        request.setPlannedSessions(java.math.BigDecimal.valueOf(60));
        request.setSessionDuration(java.math.BigDecimal.valueOf(180));
        assertDoesNotThrow(() -> service.updateOffering(11, request));

        for (Integer planned : new Integer[] { null, 0, 61 }) {
            request.setPlannedSessions(planned == null ? null : java.math.BigDecimal.valueOf(planned));
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.updateOffering(11, request)).getStatusCode().value(), "plannedSessions=" + planned);
        }
        request.setPlannedSessions(java.math.BigDecimal.valueOf(8));
        for (Integer duration : new Integer[] { null, 29, 181 }) {
            request.setSessionDuration(duration == null ? null : java.math.BigDecimal.valueOf(duration));
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.updateOffering(11, request)).getStatusCode().value(), "sessionDuration=" + duration);
        }
        request.setPlannedSessions(new BigDecimal("8.5"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request)).getStatusCode().value());
        request.setPlannedSessions(java.math.BigDecimal.valueOf(8));
        request.setSessionDuration(new BigDecimal("60.5"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request)).getStatusCode().value());
    }

    @Test
    void unrelatedUpdatePreservesLegacyOutOfRangeNumericValues() {
        offering.setPlannedSessions(75);
        offering.setSessionDuration(240);
        request.setPlannedSessions(java.math.BigDecimal.valueOf(75));
        request.setSessionDuration(java.math.BigDecimal.valueOf(240));
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.existsOutsideOfferingRange(any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> service.updateOffering(11, request));
        assertEquals(75, offering.getPlannedSessions());
        assertEquals(240, offering.getSessionDuration());
    }

    @Test
    void unrelatedUpdatePreservesLegacyNullPlannedSessions() {
        offering.setPlannedSessions(null);
        request.setPlannedSessions(null);
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.existsOutsideOfferingRange(any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> service.updateOffering(11, request));
        assertEquals(null, offering.getPlannedSessions());
    }

    @Test
    void rejectsChangingLegacyNumericValuesToValuesOutsideApprovedRanges() {
        offering.setPlannedSessions(75);
        offering.setSessionDuration(240);
        when(offerings.findByIdForScheduleUpdate(11)).thenReturn(Optional.of(offering));
        request.setPlannedSessions(java.math.BigDecimal.valueOf(61));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request)).getStatusCode().value());

        request.setPlannedSessions(java.math.BigDecimal.valueOf(75));
        request.setSessionDuration(java.math.BigDecimal.valueOf(181));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updateOffering(11, request)).getStatusCode().value());
    }

    private void prepareCreate() {
        Course course = offering.getCourse();
        course.setStatus("ACTIVE");
        when(courses.findById(9)).thenReturn(Optional.of(course));
        when(offerings.save(any(CourseOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private group5.example.sportCenter.dto.CreateCourseOfferingRequest createRequest(Integer planned, Integer duration) {
        var create = new group5.example.sportCenter.dto.CreateCourseOfferingRequest();
        create.setCourseId(9);
        create.setName("Spring term");
        create.setPrice(new BigDecimal("100.00"));
        create.setPlannedSessions(planned == null ? null : java.math.BigDecimal.valueOf(planned));
        create.setSessionDuration(duration == null ? null : java.math.BigDecimal.valueOf(duration));
        create.setStartDate(LocalDate.of(2027, 1, 10));
        create.setEndDate(LocalDate.of(2027, 2, 10));
        create.setRegistrationDeadline(LocalDateTime.of(2027, 1, 8, 12, 0));
        create.setCancellationDeadline(LocalDateTime.of(2027, 1, 9, 12, 0));
        return create;
    }
}
