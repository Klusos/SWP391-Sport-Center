package group5.example.sportCenter.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.CourseResponse;
import group5.example.sportCenter.dto.CreateCourseRequest;
import group5.example.sportCenter.dto.UpdateCourseRequest;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.SportRepository;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final SportRepository sportRepository;

    public CourseService(
            CourseRepository courseRepository,
            SportRepository sportRepository) {

        this.courseRepository = courseRepository;
        this.sportRepository = sportRepository;
    }

    @Transactional
    public CourseResponse createCourse(
            CreateCourseRequest request) {

        Sport sport = sportRepository.findById(request.getSportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sport not found with id: "
                                        + request.getSportId()));

        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot create course for an inactive sport");
        }

        validateAgeRange(
                request.getTargetAgeMin(),
                request.getTargetAgeMax());

        Course course = new Course();

        course.setSport(sport);
        course.setName(request.getName());
        course.setDescription(request.getDescription());
        course.setTargetAgeMin(request.getTargetAgeMin());
        course.setTargetAgeMax(request.getTargetAgeMax());
        course.setLevel(CatalogValues.courseLevel(request.getLevel()));
        course.setLearningOutcome(request.getLearningOutcome());
        course.setStatus("ACTIVE");

        return toResponse(courseRepository.save(course));
    }

    public List<CourseResponse> getAllCourses() {

        return courseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CourseResponse getCourseById(Integer id) {

        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found with id: " + id));

        return toResponse(course);
    }

    public List<CourseResponse> getCoursesBySport(
            Integer sportId) {

        if (!sportRepository.existsById(sportId)) {
            throw new ResourceNotFoundException(
                    "Sport not found with id: " + sportId);
        }

        return courseRepository.findBySportId(sportId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CourseResponse updateCourse(
            Integer id,
            UpdateCourseRequest request) {

        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found with id: " + id));

        validateAgeRange(
                request.getTargetAgeMin(),
                request.getTargetAgeMax());

        course.setName(request.getName());
        course.setDescription(request.getDescription());
        course.setTargetAgeMin(request.getTargetAgeMin());
        course.setTargetAgeMax(request.getTargetAgeMax());
        course.setLevel(CatalogValues.courseLevel(request.getLevel()));
        course.setLearningOutcome(request.getLearningOutcome());
        course.setStatus(CatalogValues.status(request.getStatus(), "Course", List.of("ACTIVE", "INACTIVE")));

        return toResponse(courseRepository.save(course));
    }

    private void validateAgeRange(
            Integer minAge,
            Integer maxAge) {

        if (minAge != null
                && maxAge != null
                && minAge > maxAge) {

            throw new IllegalArgumentException(
                    "Minimum age cannot be greater than maximum age");
        }
    }

    private CourseResponse toResponse(Course course) {

        return new CourseResponse(
                course.getId(),

                course.getSport().getId(),
                course.getSport().getName(),

                course.getName(),
                course.getDescription(),

                course.getTargetAgeMin(),
                course.getTargetAgeMax(),

                course.getLevel(),
                course.getLearningOutcome(),

                course.getStatus()
        );
    }
}
