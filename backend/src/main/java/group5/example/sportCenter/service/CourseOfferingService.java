package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
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
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.entity.CourseRegistrationStatus;

@Service
@Transactional(readOnly = true)
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final ClassSessionRepository classSessionRepository;
    private final Clock clock;
    private final CourseClassRepository courseClassRepository;
    private final ClassSessionService classSessionService;
    private final CourseRegistrationRepository courseRegistrationRepository;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public CourseOfferingService(
            CourseOfferingRepository courseOfferingRepository,
            CourseRepository courseRepository,
            ClassSessionRepository classSessionRepository,
            CourseClassRepository courseClassRepository,
            ClassSessionService classSessionService,
            CourseRegistrationRepository courseRegistrationRepository,
            Clock clock) {

        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRepository = courseRepository;
        this.classSessionRepository = classSessionRepository;
        this.courseClassRepository = courseClassRepository;
        this.classSessionService = classSessionService;
        this.courseRegistrationRepository = courseRegistrationRepository;
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
        return getOfferingsByCourse(courseId, false);
    }

    public List<CourseOfferingResponse> getOfferingsByCourse(Integer courseId, boolean includeRegistrationReadiness) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found with id: " + courseId);
        }
        return courseOfferingRepository.findByCourseId(courseId).stream()
                .map(offering -> toResponse(offering, includeRegistrationReadiness)).toList();
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
        boolean commercialFieldsChanged = !sameMoney(request.getPrice(), offering.getPrice())
                || plannedSessionsChanged || sessionDurationChanged
                || !Objects.equals(request.getStartDate(), offering.getStartDate())
                || !Objects.equals(request.getEndDate(), offering.getEndDate())
                || !Objects.equals(request.getRegistrationDeadline(), offering.getRegistrationDeadline())
                || !Objects.equals(request.getCancellationDeadline(), offering.getCancellationDeadline());
        LocalDateTime originalOpening = offering.getRegistrationDeadline().minusDays(14);
        if (commercialFieldsChanged && !LocalDateTime.now(clock.withZone(BUSINESS_ZONE)).isBefore(originalOpening)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Commercial CourseOffering terms are locked from the scheduled registration opening time (14 days before the current registration deadline)");
        }
        boolean registrationSensitiveScheduleChange = plannedSessionsChanged || sessionDurationChanged
                || !Objects.equals(request.getStartDate(), offering.getStartDate())
                || !Objects.equals(request.getEndDate(), offering.getEndDate())
                || !Objects.equals(request.getCancellationDeadline(), offering.getCancellationDeadline());
        if (registrationSensitiveScheduleChange
                && courseRegistrationRepository.existsByCourseClass_CourseOffering_IdAndStatusIn(id,
                        List.of(CourseRegistrationStatus.CONFIRMED, CourseRegistrationStatus.PENDING_PAYMENT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CourseOffering schedule and cancellation terms cannot change while confirmed or pending registrations exist");
        }
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

        if (plannedSessionsChanged) {
            java.util.Map<Integer, Long> activeSessionCounts = classSessionRepository
                    .findByCourseClass_CourseOffering_Id(id).stream()
                    .filter(session -> !"CANCELLED".equalsIgnoreCase(session.getStatus()))
                    .collect(java.util.stream.Collectors.groupingBy(
                            session -> session.getCourseClass().getId(), java.util.stream.Collectors.counting()));
            long requestedPlanned = request.getPlannedSessions() == null ? -1
                    : request.getPlannedSessions().longValue();
            if (activeSessionCounts.values().stream().anyMatch(count -> count > requestedPlanned)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "CourseOffering plannedSessions cannot be reduced below an existing Class schedule count");
            }
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

    private boolean sameMoney(java.math.BigDecimal requested, java.math.BigDecimal persisted) {
        return requested == null ? persisted == null
                : persisted != null && requested.compareTo(persisted) == 0;
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

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
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

    private CourseOfferingResponse toResponse(CourseOffering offering) {
        return toResponse(offering, false);
    }

    private CourseOfferingResponse toResponse(CourseOffering offering, boolean includeRegistrationReadiness) {

        Course course = offering.getCourse();

        LocalDateTime scheduledOpeningAt = offering.getRegistrationDeadline() == null
                ? null : offering.getRegistrationDeadline().minusDays(14);
        List<Integer> readyClassIds = includeRegistrationReadiness
                ? courseClassRepository.findByCourseOfferingId(offering.getId()).stream()
                        .filter(classSessionService::isRegistrationReady)
                        .map(group5.example.sportCenter.entity.CourseClass::getId).toList()
                : List.of();
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        boolean available = "OPEN".equalsIgnoreCase(offering.getStatus()) && scheduledOpeningAt != null
                && !now.isBefore(scheduledOpeningAt) && offering.getRegistrationDeadline() != null
                && now.isBefore(offering.getRegistrationDeadline())
                && offering.getEndDate() != null && !LocalDate.now(clock.withZone(BUSINESS_ZONE)).isAfter(offering.getEndDate())
                && includeRegistrationReadiness && !readyClassIds.isEmpty();

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

                offering.getStatus(),
                scheduledOpeningAt,
                readyClassIds,
                available
        );
    }
}
