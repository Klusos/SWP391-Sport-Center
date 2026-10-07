package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CourseOfferingResponse;
import group5.example.sportCenter.dto.CreateCourseOfferingRequest;
import group5.example.sportCenter.dto.UpdateCourseOfferingRequest;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;

@Service
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;

    public CourseOfferingService(
            CourseOfferingRepository courseOfferingRepository,
            CourseRepository courseRepository) {

        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRepository = courseRepository;
    }

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

        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationDeadline(),
                request.getCancellationDeadline());

        CourseOffering offering = new CourseOffering();

        offering.setCourse(course);
        offering.setName(request.getName());
        offering.setPrice(request.getPrice());
        offering.setPlannedSessions(request.getPlannedSessions());
        offering.setSessionDuration(request.getSessionDuration());
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

    public CourseOfferingResponse updateOffering(
            Integer id,
            UpdateCourseOfferingRequest request) {

        CourseOffering offering =
                courseOfferingRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Course offering not found with id: "
                                                + id));

        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationDeadline(),
                request.getCancellationDeadline());

        offering.setName(request.getName());
        offering.setPrice(request.getPrice());
        offering.setPlannedSessions(
                request.getPlannedSessions());
        offering.setSessionDuration(
                request.getSessionDuration());
        offering.setStartDate(request.getStartDate());
        offering.setEndDate(request.getEndDate());
        offering.setRegistrationDeadline(
                request.getRegistrationDeadline());
        offering.setCancellationDeadline(
                request.getCancellationDeadline());
        offering.setStatus(request.getStatus());

        return toResponse(
                courseOfferingRepository.save(offering));
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime registrationDeadline,
            LocalDateTime cancellationDeadline) {

        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        LocalDateTime startDateTime =
                startDate.atStartOfDay();

        if (registrationDeadline.isAfter(startDateTime)) {
            throw new IllegalArgumentException(
                    "Registration deadline cannot be after course start date");
        }

        if (cancellationDeadline.isAfter(startDateTime)) {
            throw new IllegalArgumentException(
                    "Cancellation deadline cannot be after course start date");
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