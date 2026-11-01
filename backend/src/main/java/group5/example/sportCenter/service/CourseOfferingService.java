package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CourseOfferingResponse;
import group5.example.sportCenter.dto.CreateCourseOfferingRequest;
import group5.example.sportCenter.dto.UpdateCourseOfferingRequest;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseRepository;

@Service
@Transactional(readOnly = true)
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final ClassSessionRepository classSessionRepository;
    private final Clock clock;

    public CourseOfferingService(
            CourseOfferingRepository courseOfferingRepository,
            CourseRepository courseRepository,
            ClassSessionRepository classSessionRepository,
            Clock clock) {

        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRepository = courseRepository;
        this.classSessionRepository = classSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public CourseOfferingResponse createOffering(
            CreateCourseOfferingRequest request) {

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found with id: "
                                        + request.getCourseId()));

        if (!"ACTIVE".equalsIgnoreCase(course.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot create offering for an inactive course");
        }

        validateNumericRules(request.getPlannedSessions(), request.getSessionDuration());

        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationDeadline(),
                request.getCancellationDeadline(), true, true);

        CourseOffering offering = new CourseOffering();

        offering.setCourse(course);
        offering.setName(request.getName());
        offering.setPrice(request.getPrice());
        offering.setPlannedSessions(request.getPlannedSessions().intValueExact());
        offering.setSessionDuration(request.getSessionDuration().intValueExact());
        offering.setStartDate(request.getStartDate());
        offering.setEndDate(request.getEndDate());
        offering.setRegistrationDeadline(
                request.getRegistrationDeadline());
        offering.setCancellationDeadline(
                request.getCancellationDeadline());

        offering.setStatus("OPEN");

        return toResponse(
                courseOfferingRepository.save(offering));
    }

    public List<CourseOfferingResponse> getAllOfferings() {

        return courseOfferingRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CourseOfferingResponse getOfferingById(
            Integer id) {

        CourseOffering offering =
                courseOfferingRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Course offering not found with id: "
                                                + id));

        return toResponse(offering);
    }

    public List<CourseOfferingResponse> getOfferingsByCourse(
            Integer courseId) {

        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException(
                    "Course not found with id: " + courseId);
        }

        return courseOfferingRepository
                .findByCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CourseOfferingResponse updateOffering(
            Integer id,
            UpdateCourseOfferingRequest request) {

        CourseOffering offering =
                courseOfferingRepository.findByIdForScheduleUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Course offering not found with id: "
                                                + id));

        boolean plannedSessionsChanged = !sameInteger(request.getPlannedSessions(), offering.getPlannedSessions());
        boolean sessionDurationChanged = !sameInteger(request.getSessionDuration(), offering.getSessionDuration());
        if (plannedSessionsChanged) {
            validatePlannedSessions(request.getPlannedSessions());
        }
        if (sessionDurationChanged) {
            validateSessionDuration(request.getSessionDuration());
        }

        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationDeadline(),
                request.getCancellationDeadline(),
                !Objects.equals(request.getStartDate(), offering.getStartDate()),
                !Objects.equals(request.getRegistrationDeadline(), offering.getRegistrationDeadline())
                        || !Objects.equals(request.getCancellationDeadline(), offering.getCancellationDeadline()));

        if (sessionDurationChanged
                && classSessionRepository.findByCourseClass_CourseOffering_Id(id).stream()
                        .anyMatch(session -> !hasDuration(session.getStartTime(), session.getEndTime(),
                                request.getSessionDuration().intValueExact()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CourseOffering sessionDuration cannot change because existing ClassSessions would no longer match it");
        }

        if (classSessionRepository.existsOutsideOfferingRange(id,
                request.getStartDate().atStartOfDay(),
                request.getEndDate().plusDays(1).atStartOfDay())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CourseOffering dates cannot exclude an existing ClassSession");
        }

        offering.setName(request.getName());
        offering.setPrice(request.getPrice());
        offering.setPlannedSessions(
                request.getPlannedSessions() == null ? null : request.getPlannedSessions().intValueExact());
        offering.setSessionDuration(
                request.getSessionDuration() == null ? null : request.getSessionDuration().intValueExact());
        offering.setStartDate(request.getStartDate());
        offering.setEndDate(request.getEndDate());
        offering.setRegistrationDeadline(
                request.getRegistrationDeadline());
        offering.setCancellationDeadline(
                request.getCancellationDeadline());
        offering.setStatus(CatalogValues.status(request.getStatus(), "CourseOffering", List.of("OPEN", "CLOSED")));

        return toResponse(
                courseOfferingRepository.save(offering));
    }

    private boolean hasDuration(LocalDateTime startTime, LocalDateTime endTime, Integer durationMinutes) {
        return startTime != null && endTime != null && durationMinutes != null
                && java.time.Duration.between(startTime, endTime).getSeconds() == durationMinutes.longValue() * 60
                && java.time.Duration.between(startTime, endTime).getNano() == 0;
    }

    private boolean sameInteger(java.math.BigDecimal requested, Integer persisted) {
        return requested == null ? persisted == null
                : persisted != null && requested.compareTo(java.math.BigDecimal.valueOf(persisted)) == 0;
    }

    private void validateNumericRules(java.math.BigDecimal plannedSessions, java.math.BigDecimal durationMinutes) {
        validatePlannedSessions(plannedSessions);
        validateSessionDuration(durationMinutes);
    }

    private void validatePlannedSessions(java.math.BigDecimal plannedSessions) {
        if (!isIntegerInRange(plannedSessions, 1, 60)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CourseOffering plannedSessions must be an integer from 1 to 60");
        }
    }

    private void validateSessionDuration(java.math.BigDecimal durationMinutes) {
        if (!isIntegerInRange(durationMinutes, 30, 180)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CourseOffering sessionDuration must be an integer from 30 to 180 minutes");
        }
    }

    private boolean isIntegerInRange(java.math.BigDecimal value, int min, int max) {
        if (value == null) return false;
        try {
            int integer = value.intValueExact();
            return integer >= min && integer <= max;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime registrationDeadline,
            LocalDateTime cancellationDeadline,
            boolean validateNotPast,
            boolean validateDeadlinesNotExpired) {

        if (startDate == null || endDate == null || registrationDeadline == null || cancellationDeadline == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Offering startDate, endDate, registrationDeadline and cancellationDeadline are required");
        }

        if (!startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        if (validateNotPast && startDate.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A new or changed offering startDate cannot be in the past");
        }
        if (validateDeadlinesNotExpired
                && (!registrationDeadline.isAfter(now) || !cancellationDeadline.isAfter(now))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New or changed registrationDeadline and cancellationDeadline must be in the future");
        }

        LocalDateTime startDateTime =
                startDate.atStartOfDay();

        if (registrationDeadline.isAfter(startDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Registration deadline cannot be after course start date");
        }

        if (cancellationDeadline.isAfter(startDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancellation deadline cannot be after course start date");
        }
        if (registrationDeadline.isAfter(cancellationDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Registration deadline must be on or before cancellation deadline");
        }
    }

    private CourseOfferingResponse toResponse(
            CourseOffering offering) {

        Course course = offering.getCourse();

        return new CourseOfferingResponse(
                offering.getId(),

                course.getId(),
                course.getName(),

                course.getSport().getId(),
                course.getSport().getName(),

                offering.getName(),
                offering.getPrice(),

                offering.getPlannedSessions(),
                offering.getSessionDuration(),

                offering.getStartDate(),
                offering.getEndDate(),

                offering.getRegistrationDeadline(),
                offering.getCancellationDeadline(),

                offering.getStatus()
        );
    }
}
