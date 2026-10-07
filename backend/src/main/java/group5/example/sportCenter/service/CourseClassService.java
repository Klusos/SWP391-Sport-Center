package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CourseClassResponse;
import group5.example.sportCenter.dto.CreateClassRequest;
import group5.example.sportCenter.dto.UpdateClassRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;

@Service
public class CourseClassService {

    private final CourseClassRepository courseClassRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CoachRepository coachRepository;
    private final CoachSportRepository coachSportRepository;

    public CourseClassService(
            CourseClassRepository courseClassRepository,
            CourseOfferingRepository courseOfferingRepository,
            CoachRepository coachRepository,
            CoachSportRepository coachSportRepository) {

        this.courseClassRepository = courseClassRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.coachRepository = coachRepository;
        this.coachSportRepository = coachSportRepository;
    }

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
                .findById(request.getCoachId())
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

        return toResponse(
                courseClassRepository.save(courseClass));
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

        public CourseClassResponse updateClass(
            Integer id,
            UpdateClassRequest request) {

        CourseClass courseClass =
                courseClassRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Class not found with id: " + id));

        Coach coach =
                coachRepository.findById(request.getCoachId())
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

        courseClass.setCoach(coach);
        courseClass.setName(request.getName());
        courseClass.setCapacity(request.getCapacity());
        courseClass.setStatus(request.getStatus());

        return toResponse(
                courseClassRepository.save(courseClass));
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