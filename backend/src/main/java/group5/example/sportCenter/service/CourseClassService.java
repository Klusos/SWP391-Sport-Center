package group5.example.sportCenter.service;

import java.util.List;
import java.util.Objects;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CourseClassResponse;
import group5.example.sportCenter.dto.CreateClassRequest;
import group5.example.sportCenter.dto.UpdateClassRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;

@Service
@Transactional(readOnly = true)
public class CourseClassService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CourseClassRepository courseClassRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRegistrationRepository courseRegistrationRepository;
    private final CoachRepository coachRepository;
    private final CoachSportRepository coachSportRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CoachScheduleConflictService coachScheduleConflicts;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CourseClassService(
            CourseClassRepository courseClassRepository,
            CourseOfferingRepository courseOfferingRepository,
            CourseRegistrationRepository courseRegistrationRepository,
            CoachRepository coachRepository,
            CoachSportRepository coachSportRepository,
            ApplicationEventPublisher events,
            ClassSessionRepository classSessionRepository,
            CoachScheduleConflictService coachScheduleConflicts,
            Clock clock) {

        this.courseClassRepository = courseClassRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRegistrationRepository = courseRegistrationRepository;
        this.coachRepository = coachRepository;
        this.coachSportRepository = coachSportRepository;
        this.events = events;
        this.classSessionRepository = classSessionRepository;
        this.coachScheduleConflicts = coachScheduleConflicts;
        this.clock = clock;
    }

    @Transactional
    public CourseClassResponse createClass(
            CreateClassRequest request) {

        CourseOffering offering =
                courseOfferingRepository
                        .findById(request.getCourseOfferingId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Course offering not found with id: "
                                                + request.getCourseOfferingId()));

        Coach coach = coachRepository
                .findByIdForBookingUpdate(request.getCoachId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Coach not found with id: "
                                        + request.getCoachId()));

        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            throw new IllegalArgumentException(
                    "Inactive coach cannot be assigned to a class");
        }

        if (!"OPEN".equalsIgnoreCase(offering.getStatus())) {
            throw new IllegalArgumentException(
                    "Class can only be created for an open course offering");
        }

        Integer sportId =
                offering
                        .getCourse()
                        .getSport()
                        .getId();

        boolean coachCanTeachSport =
                coachSportRepository
                        .existsByCoachIdAndSportId(
                                coach.getId(),
                                sportId);

        if (!coachCanTeachSport) {
            throw new IllegalArgumentException(
                    "Coach is not qualified for this sport");
        }

        CourseClass courseClass = new CourseClass();

        courseClass.setCourseOffering(offering);
        courseClass.setCoach(coach);
        courseClass.setName(request.getName());
        courseClass.setCapacity(request.getCapacity());
        courseClass.setStatus("ACTIVE");

        CourseClass created = courseClassRepository.save(courseClass);
        CourseClassResponse response = toResponse(created);
        events.publishEvent(new NotificationBusinessEvents.CourseCoachAssigned(created.getId(), coach.getId()));
        return response;
    }

        public List<CourseClassResponse> getAllClasses() {

        return courseClassRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CourseClassResponse getClassById(Integer id) {

        CourseClass courseClass =
                courseClassRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Class not found with id: " + id));

        return toResponse(courseClass);
    }

    public List<CourseClassResponse> getClassesByOffering(
            Integer offeringId) {

        if (!courseOfferingRepository.existsById(offeringId)) {
            throw new ResourceNotFoundException(
                    "Course offering not found with id: "
                            + offeringId);
        }

        return courseClassRepository
                .findByCourseOfferingId(offeringId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

        @Transactional
        public CourseClassResponse updateClass(
            Integer id,
            UpdateClassRequest request) {

        CourseClass courseClass =
                courseClassRepository.findByIdForScheduleUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Class not found with id: " + id));

        // Registration creation takes this same CourseClass row lock before counting seats.
        // Use its deadline-aware occupancy rule so a concurrent registration cannot race
        // this capacity change into an overbooked class.
        long occupiedSeats = courseRegistrationRepository.countOccupiedSeats(
                courseClass.getId(), CourseRegistrationStatus.CONFIRMED,
                CourseRegistrationStatus.PENDING_PAYMENT,
                LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        if (request.getCapacity() < occupiedSeats) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capacity cannot be reduced below the " + occupiedSeats
                            + " seats currently occupied by confirmed registrations and unexpired payment reservations");
        }

        Integer previousCoachId = courseClass.getCoach().getId();

        Coach coach =
                coachRepository.findByIdForBookingUpdate(request.getCoachId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Coach not found with id: "
                                                + request.getCoachId()));

        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            throw new IllegalArgumentException(
                    "Inactive coach cannot be assigned to a class");
        }

        Integer sportId =
                courseClass
                        .getCourseOffering()
                        .getCourse()
                        .getSport()
                        .getId();

        if (!coachSportRepository
                .existsByCoachIdAndSportId(
                        coach.getId(),
                        sportId)) {

            throw new IllegalArgumentException(
                    "Coach is not qualified for this sport");
        }

        if (!Objects.equals(previousCoachId, coach.getId())) {
            LocalDateTime now = LocalDateTime.now(clock);
            List<group5.example.sportCenter.entity.ClassSession> relevantSessions = classSessionRepository
                    .findByCourseClassId(courseClass.getId()).stream()
                    .filter(session -> !"CANCELLED".equalsIgnoreCase(session.getStatus()))
                    .filter(session -> session.getEndTime().isAfter(now))
                    .toList();
            for (int i = 0; i < relevantSessions.size(); i++) {
                var session = relevantSessions.get(i);
                for (int j = i + 1; j < relevantSessions.size(); j++) {
                    var other = relevantSessions.get(j);
                    if (session.getStartTime().isBefore(other.getEndTime())
                            && session.getEndTime().isAfter(other.getStartTime())) {
                        throw new IllegalArgumentException(
                                "The CourseClass contains overlapping sessions and cannot be reassigned to this coach");
                    }
                }
                coachScheduleConflicts.requireNoConflict(coach.getId(), session.getStartTime(),
                        session.getEndTime(), session.getId());
            }
        }

        courseClass.setCoach(coach);
        courseClass.setName(request.getName());
        courseClass.setCapacity(request.getCapacity());
        courseClass.setStatus(CatalogValues.status(request.getStatus(), "CourseClass", List.of("ACTIVE", "INACTIVE")));

        CourseClass updated = courseClassRepository.save(courseClass);
        CourseClassResponse response = toResponse(updated);
        if (!Objects.equals(previousCoachId, coach.getId())) {
            events.publishEvent(new NotificationBusinessEvents.CourseCoachAssigned(updated.getId(), coach.getId()));
        }
        return response;
    }

        private CourseClassResponse toResponse(
            CourseClass courseClass) {

        CourseOffering offering =
                courseClass.getCourseOffering();

        return new CourseClassResponse(
                courseClass.getId(),

                offering.getId(),
                offering.getName(),

                offering.getCourse().getId(),
                offering.getCourse().getName(),

                offering.getCourse().getSport().getId(),
                offering.getCourse().getSport().getName(),

                courseClass.getCoach().getId(),
                courseClass.getCoach().getUser().getName(),

                courseClass.getName(),
                courseClass.getCapacity(),
                courseClass.getStatus()
        );
    }
}
