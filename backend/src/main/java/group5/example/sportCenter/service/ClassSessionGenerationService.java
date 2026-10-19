package group5.example.sportCenter.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ClassSessionGenerationPreviewResponse;
import group5.example.sportCenter.dto.ClassSessionGenerationRequest;
import group5.example.sportCenter.dto.ClassSessionGenerationResponse;
import group5.example.sportCenter.dto.CreateClassSessionRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.FacilityRepository;

@Service
public class ClassSessionGenerationService {
    private static final String CANCELLED = "CANCELLED";

    private final CourseClassRepository courseClassRepository;
    private final ClassSessionRepository classSessionRepository;
    private final FacilityRepository facilityRepository;
    private final ClassSessionService classSessionService;

    public ClassSessionGenerationService(CourseClassRepository courseClassRepository,
            ClassSessionRepository classSessionRepository, FacilityRepository facilityRepository,
            ClassSessionService classSessionService) {
        this.courseClassRepository = courseClassRepository;
        this.classSessionRepository = classSessionRepository;
        this.facilityRepository = facilityRepository;
        this.classSessionService = classSessionService;
    }

    @Transactional(readOnly = true)
    public ClassSessionGenerationPreviewResponse preview(ClassSessionGenerationRequest request) {
        validateRequest(request);
        CourseClass courseClass = findClass(request.getCourseClassId());
        Facility facility = findFacility(request.getFacilityId());
        return buildPreview(request, courseClass, facility);
    }

    @Transactional
    public ClassSessionGenerationResponse generate(ClassSessionGenerationRequest request) {
        validateRequest(request);
        CourseClass courseClass = courseClassRepository.findByIdForScheduleUpdate(request.getCourseClassId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CourseClass not found with id: " + request.getCourseClassId()));
        classSessionService.lockOffering(courseClass);
        Facility facility = findFacility(request.getFacilityId());
        facility = classSessionService.lockScheduleResources(courseClass, facility);

        ClassSessionGenerationPreviewResponse preview = buildPreview(request, courseClass, facility);
        if (!preview.canGenerate()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.join("; ", preview.conflicts()));
        }

        List<group5.example.sportCenter.dto.ClassSessionResponse> created = new ArrayList<>();
        for (ClassSessionGenerationPreviewResponse.Candidate candidate : preview.candidates()) {
            CreateClassSessionRequest create = new CreateClassSessionRequest();
            create.setClassId(courseClass.getId());
            create.setFacilityId(facility == null ? null : facility.getId());
            create.setStartTime(candidate.startTime());
            create.setEndTime(candidate.endTime());
            create.setStatus("SCHEDULED");
            created.add(classSessionService.create(create));
        }
        return new ClassSessionGenerationResponse(courseClass.getId(), created.size(), List.copyOf(created));
    }

    private ClassSessionGenerationPreviewResponse buildPreview(ClassSessionGenerationRequest request,
            CourseClass courseClass, Facility facility) {
        CourseOffering offering = courseClass.getCourseOffering();
        validateFirstDate(request, offering);

        if (offering.getPlannedSessions() == null || offering.getSessionDuration() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CourseOffering plannedSessions and sessionDuration must be configured before schedule generation");
        }
        int planned = offering.getPlannedSessions();
        long existing = classSessionRepository.countByCourseClassIdAndStatusNot(courseClass.getId(), CANCELLED);
        long remaining = Math.max(0L, planned - existing);
        int requested = request.getRequestedSessionCount() == null
                ? Math.toIntExact(remaining) : request.getRequestedSessionCount();
        List<String> conflicts = new ArrayList<>();

        if (existing > planned) {
            conflicts.add("This class already has " + existing + " non-cancelled sessions, exceeding the planned target of " + planned);
        }
        if (requested > remaining) {
            conflicts.add("Requested " + requested + " sessions, but only " + remaining + " planned-session slots remain");
        }
        if (requested == 0) {
            conflicts.add("This class has no remaining planned-session slots");
        }

        LocalDate cursor = request.getFirstEligibleDate() == null
                ? offering.getStartDate() : request.getFirstEligibleDate();
        Set<DayOfWeek> weekdays = EnumSet.copyOf(request.getWeekdays());
        List<ClassSessionGenerationPreviewResponse.Candidate> candidates = new ArrayList<>();
        List<String> generatedCandidateIssues = new ArrayList<>();
        while (!cursor.isAfter(offering.getEndDate()) && candidates.size() < requested) {
            if (weekdays.contains(cursor.getDayOfWeek())) {
                LocalDateTime start = cursor.atTime(request.getStartTime());
                LocalDateTime end = start.plusMinutes(offering.getSessionDuration());
                List<String> candidateConflicts = classSessionService.validateGeneratedCandidate(
                        courseClass, facility, start, end);
                for (int i = 0; i < candidates.size(); i++) {
                    ClassSessionGenerationPreviewResponse.Candidate prior = candidates.get(i);
                    if (overlaps(prior.startTime(), prior.endTime(), start, end)) {
                        candidateConflicts.add("Overlaps another proposed session on " + prior.startTime().toLocalDate()
                                + " (Coach conflict" + (facility == null ? "" : " and Facility conflict") + ")");
                        break;
                    }
                }
                candidates.add(new ClassSessionGenerationPreviewResponse.Candidate(start, end,
                        List.copyOf(candidateConflicts)));
                for (String issue : candidateConflicts) {
                    generatedCandidateIssues.add(start.toLocalDate() + ": " + issue);
                }
            }
            cursor = cursor.plusDays(1);
        }
        if (candidates.size() < requested) {
            conflicts.add("Only " + candidates.size() + " matching weekdays are available between the first eligible date and offering end date; "
                    + requested + " were requested");
        }
        conflicts.addAll(generatedCandidateIssues);

        Coach coach = courseClass.getCoach();
        return new ClassSessionGenerationPreviewResponse(courseClass.getId(), courseClass.getName(), offering.getName(),
                offering.getStartDate(), offering.getEndDate(), coach.getId(),
                coach.getUser() == null ? null : coach.getUser().getName(),
                facility == null ? null : facility.getId(), facility == null ? null : facility.getName(),
                offering.getSessionDuration(), planned, existing, remaining, requested,
                List.copyOf(candidates), List.copyOf(conflicts), conflicts.isEmpty());
    }

    private void validateFirstDate(ClassSessionGenerationRequest request, CourseOffering offering) {
        LocalDate first = request.getFirstEligibleDate();
        if (first != null && (first.isBefore(offering.getStartDate()) || first.isAfter(offering.getEndDate()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "First eligible date must be within the CourseOffering date range");
        }
    }

    private void validateRequest(ClassSessionGenerationRequest request) {
        if (request == null || request.getWeekdays() == null || request.getWeekdays().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one weekday");
        }
        if (request.getCourseClassId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CourseClass ID is required");
        }
        if (request.getWeekdays().stream().anyMatch(day -> day == null)
                || request.getWeekdays().stream().distinct().count() != request.getWeekdays().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekdays must be valid and must not repeat");
        }
        if (request.getStartTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily start time is required");
        }
        if (request.getRequestedSessionCount() != null && request.getRequestedSessionCount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested session count must be positive");
        }
    }

    private boolean overlaps(LocalDateTime firstStart, LocalDateTime firstEnd,
            LocalDateTime secondStart, LocalDateTime secondEnd) {
        return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
    }

    private CourseClass findClass(Integer id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CourseClass ID is required");
        return courseClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + id));
    }

    private Facility findFacility(Integer id) {
        if (id == null) return null;
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + id));
    }
}
