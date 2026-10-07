package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CourseOfferingResponse;
import group5.example.sportCenter.dto.CreateCourseOfferingRequest;
import group5.example.sportCenter.dto.UpdateCourseOfferingRequest;
import group5.example.sportCenter.service.CourseOfferingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/course-offerings")
public class CourseOfferingController {

    private final CourseOfferingService courseOfferingService;

    public CourseOfferingController(
            CourseOfferingService courseOfferingService) {

        this.courseOfferingService = courseOfferingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseOfferingResponse createOffering(
            @Valid @RequestBody CreateCourseOfferingRequest request) {

        return courseOfferingService.createOffering(request);
    }

    @GetMapping
    public List<CourseOfferingResponse> getAllOfferings() {
        return courseOfferingService.getAllOfferings();
    }

    @GetMapping("/{id}")
    public CourseOfferingResponse getOfferingById(
            @PathVariable Integer id) {

        return courseOfferingService.getOfferingById(id);
    }

    @GetMapping("/course/{courseId}")
    public List<CourseOfferingResponse> getOfferingsByCourse(
            @PathVariable Integer courseId) {

        return courseOfferingService
                .getOfferingsByCourse(courseId);
    }

    @PutMapping("/{id}")
    public CourseOfferingResponse updateOffering(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCourseOfferingRequest request) {

        return courseOfferingService
                .updateOffering(id, request);
    }
}