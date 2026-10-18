package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ClassSessionResponse;
import group5.example.sportCenter.dto.CreateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionRequest;
import group5.example.sportCenter.dto.UpdateClassSessionStatusRequest;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.FacilityRepository;

@Service
@Transactional
public class ClassSessionService {

    private static final String CANCELLED = "CANCELLED";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String COMPLETED = "COMPLETED";
    private static final List<String> VALID_STATUSES = List.of(SCHEDULED, COMPLETED, CANCELLED);

    private final ClassSessionRepository classSessionRepository;
    private final CourseClassRepository courseClassRepository;
    private final FacilityRepository facilityRepository;
    private final Clock clock;

    @Autowired
    public ClassSessionService(
            ClassSessionRepository classSessionRepository,
            CourseClassRepository courseClassRepository,
            FacilityRepository facilityRepository) {
        this(classSessionRepository, courseClassRepository, facilityRepository, Clock.systemDefaultZone());
    }

    ClassSessionService(
            ClassSessionRepository classSessionRepository,
            CourseClassRepository courseClassRepository,
            FacilityRepository facilityRepository,
            Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.courseClassRepository = courseClassRepository;
        this.facilityRepository = facilityRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClassSessionResponse> getAll() {
        return classSessionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClassSessionResponse getById(Integer id) {
        return toResponse(findSession(id));
    }

    @Transactional(readOnly = true)
    public List<ClassSessionResponse> getByClassId(Integer classId) {
        if (!courseClassRepository.existsById(classId)) {
            throw new ResourceNotFoundException("CourseClass not found with id: " + classId);
        }
        return classSessionRepository.findByCourseClassId(classId).stream().map(this::toResponse).toList();
    }

    public ClassSessionResponse create(CreateClassSessionRequest request) {
        CourseClass courseClass = findCourseClass(request.getClassId());
        Facility facility = findFacility(request.getFacilityId());
        requireActiveFacility(facility, "Only ACTIVE facilities can be assigned to a new ClassSession");
        String status = request.getStatus() == null ? SCHEDULED : normalizeStatus(request.getStatus());

        validateRange(request.getStartTime(), request.getEndTime());
        validateWithinOffering(courseClass, request.getStartTime(), request.getEndTime());
        validateOfferingDuration(courseClass, request.getStartTime(), request.getEndTime());
        validateCompletedTime(status, request.getEndTime());
        if (!CANCELLED.equals(status)) {
            validateConflicts(courseClass, facility, request.getStartTime(), request.getEndTime(), null);
        }

        ClassSession session = new ClassSession();
        session.setCourseClass(courseClass);
        session.setFacility(facility);
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setStatus(status);
        return toResponse(classSessionRepository.save(session));
    }

    public ClassSessionResponse update(Integer id, UpdateClassSessionRequest request) {
        ClassSession session = findSession(id);
        if (isTerminal(session.getStatus())) {
            throw new IllegalArgumentException("A ClassSession in a terminal state cannot be rescheduled");
        }

        CourseClass courseClass = findCourseClass(request.getClassId());
        Facility facility = findFacility(request.getFacilityId());
        validateRange(request.getStartTime(), request.getEndTime());
        validateWithinOffering(courseClass, request.getStartTime(), request.getEndTime());
        validateOfferingDuration(courseClass, request.getStartTime(), request.getEndTime());
        validateFacilityForUpdate(session, facility, request.getStartTime());
        validateConflicts(courseClass, facility, request.getStartTime(), request.getEndTime(), id);

        session.setCourseClass(courseClass);
        session.setFacility(facility);
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        return toResponse(classSessionRepository.save(session));
    }

    public ClassSessionResponse updateStatus(Integer id, UpdateClassSessionStatusRequest request) {
        ClassSession session = findSession(id);
        String nextStatus = normalizeStatus(request.getStatus());
        String currentStatus = session.getStatus().toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(currentStatus)) {
            throw new IllegalArgumentException("ClassSession has an unsupported current status");
        }
        if (isTerminal(currentStatus)) {
            if (!currentStatus.equals(nextStatus)) {
                throw new IllegalArgumentException("A terminal ClassSession status cannot transition to another status");
            }
            return toResponse(session);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (COMPLETED.equals(nextStatus) && now.isBefore(session.getEndTime())) {
            throw new IllegalArgumentException("A ClassSession can be completed only at or after its endTime");
        }
        if (CANCELLED.equals(nextStatus) && !now.isBefore(session.getStartTime())) {
            throw new IllegalArgumentException("A ClassSession can be cancelled only before its startTime");
        }
        session.setStatus(nextStatus);
        return toResponse(classSessionRepository.save(session));
    }

    private void validateConflicts(
            CourseClass courseClass,
            Facility facility,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer excludedSessionId) {
        boolean facilityConflict = facility != null && (excludedSessionId == null
                ? classSessionRepository.existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        facility.getId(), CANCELLED, endTime, startTime)
                : classSessionRepository.existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                        facility.getId(), CANCELLED, endTime, startTime, excludedSessionId));
        if (facilityConflict) {
            throw new IllegalArgumentException("Facility is already booked during this time range");
        }

        Integer coachId = courseClass.getCoach().getId();
        boolean coachConflict = excludedSessionId == null
                ? classSessionRepository.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        coachId, CANCELLED, endTime, startTime)
                : classSessionRepository.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                        coachId, CANCELLED, endTime, startTime, excludedSessionId);
        if (coachConflict) {
            throw new IllegalArgumentException("Coach is already assigned to another ClassSession during this time range");
        }
    }

    private void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }

    private void validateWithinOffering(CourseClass courseClass, LocalDateTime startTime, LocalDateTime endTime) {
        var offering = courseClass.getCourseOffering();
        LocalDateTime rangeStart = offering.getStartDate().atStartOfDay();
        LocalDateTime rangeEndExclusive = offering.getEndDate().plusDays(1).atStartOfDay();
        if (startTime == null || endTime == null || startTime.isBefore(rangeStart)
                || !startTime.isBefore(rangeEndExclusive) || endTime.isBefore(rangeStart)
                || !endTime.isBefore(rangeEndExclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ClassSession startTime and endTime must fall within the CourseOffering startDate and endDate (inclusive)");
        }
    }

    private void validateOfferingDuration(CourseClass courseClass, LocalDateTime startTime, LocalDateTime endTime) {
        Integer requiredMinutes = courseClass.getCourseOffering().getSessionDuration();
        if (requiredMinutes == null || startTime == null || endTime == null
                || java.time.Duration.between(startTime, endTime).getSeconds() != requiredMinutes.longValue() * 60
                || java.time.Duration.between(startTime, endTime).getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ClassSession duration must equal the CourseOffering sessionDuration of "
                            + requiredMinutes + " minutes");
        }
    }

    private void validateCompletedTime(String status, LocalDateTime endTime) {
        if (COMPLETED.equals(status) && endTime.isAfter(LocalDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A ClassSession cannot be created as COMPLETED before its endTime");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be SCHEDULED, COMPLETED, or CANCELLED");
        }
        return normalized;
    }

    private boolean isTerminal(String status) {
        return COMPLETED.equalsIgnoreCase(status) || CANCELLED.equalsIgnoreCase(status);
    }

    private ClassSession findSession(Integer id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + id));
    }

    private CourseClass findCourseClass(Integer classId) {
        return courseClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + classId));
    }

    private Facility findFacility(Integer facilityId) {
        if (facilityId == null) {
            return null;
        }
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + facilityId));
    }

    private void validateFacilityForUpdate(
            ClassSession session,
            Facility requestedFacility,
            LocalDateTime requestedStartTime) {
        if (requestedFacility == null || "ACTIVE".equalsIgnoreCase(requestedFacility.getStatus())) {
            return;
        }

        Facility currentFacility = session.getFacility();
        boolean keepingSameFacility = currentFacility != null
                && currentFacility.getId().equals(requestedFacility.getId());
        boolean futureScheduledSession = SCHEDULED.equalsIgnoreCase(session.getStatus())
                && requestedStartTime.isAfter(LocalDateTime.now());

        if (keepingSameFacility && futureScheduledSession) {
            throw new IllegalArgumentException(
                    "This future scheduled ClassSession uses a Facility with status "
                            + requestedFacility.getStatus()
                            + "; select another ACTIVE facility or cancel the session");
        }
        if (!keepingSameFacility) {
            throw new IllegalArgumentException("Only ACTIVE facilities can be assigned to a ClassSession");
        }
    }

    private void requireActiveFacility(Facility facility, String message) {
        if (facility != null && !"ACTIVE".equalsIgnoreCase(facility.getStatus())) {
            throw new IllegalArgumentException(message);
        }
    }

    private ClassSessionResponse toResponse(ClassSession session) {
        List<String> warnings = new ArrayList<>();
        var offering = session.getCourseClass().getCourseOffering();
        if (!CANCELLED.equalsIgnoreCase(session.getStatus()) && offering.getPlannedSessions() != null) {
            long activeCount = classSessionRepository.countByCourseClassIdAndStatusNot(
                    session.getCourseClass().getId(), CANCELLED);
            if (activeCount > offering.getPlannedSessions()) {
                warnings.add("CourseClass ClassSession count exceeds CourseOffering plannedSessions; verify make-up session policy");
            }
        }

        Facility facility = session.getFacility();
        return new ClassSessionResponse(
                session.getId(),
                session.getCourseClass().getId(),
                session.getCourseClass().getName(),
                facility == null ? null : facility.getId(),
                facility == null ? null : facility.getName(),
                session.getStartTime(),
                session.getEndTime(),
                session.getStatus(),
                List.copyOf(warnings));
    }
}
