package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CourseClassResponse;
import group5.example.sportCenter.dto.CreateClassRequest;
import group5.example.sportCenter.dto.UpdateClassRequest;
import group5.example.sportCenter.service.CourseClassService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/classes")
public class CourseClassController {

    private final CourseClassService courseClassService;

    public CourseClassController(
            CourseClassService courseClassService) {

        this.courseClassService = courseClassService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseClassResponse createClass(
            @Valid @RequestBody CreateClassRequest request) {

        return courseClassService.createClass(request);
    }

    @GetMapping
    public List<CourseClassResponse> getAllClasses() {
        return courseClassService.getAllClasses();
    }

    @GetMapping("/{id}")
    public CourseClassResponse getClassById(
            @PathVariable Integer id) {

        return courseClassService.getClassById(id);
    }

    @GetMapping("/offering/{offeringId}")
    public List<CourseClassResponse> getClassesByOffering(
            @PathVariable Integer offeringId) {

        return courseClassService
                .getClassesByOffering(offeringId);
    }

    @PutMapping("/{id}")
    public CourseClassResponse updateClass(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateClassRequest request) {

        return courseClassService
                .updateClass(id, request);
    }
}