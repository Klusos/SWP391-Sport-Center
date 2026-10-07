package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CourseResponse;
import group5.example.sportCenter.dto.CreateCourseRequest;
import group5.example.sportCenter.dto.UpdateCourseRequest;
import group5.example.sportCenter.service.CourseService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @Valid @RequestBody CreateCourseRequest request) {

        return courseService.createCourse(request);
    }

    @GetMapping
    public List<CourseResponse> getAllCourses() {
        return courseService.getAllCourses();
    }

    @GetMapping("/{id}")
    public CourseResponse getCourseById(
            @PathVariable Integer id) {

        return courseService.getCourseById(id);
    }

    @GetMapping("/sport/{sportId}")
    public List<CourseResponse> getCoursesBySport(
            @PathVariable Integer sportId) {

        return courseService.getCoursesBySport(sportId);
    }

    @PutMapping("/{id}")
    public CourseResponse updateCourse(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCourseRequest request) {

        return courseService.updateCourse(id, request);
    }
}