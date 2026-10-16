package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CourseResponse;
import group5.example.sportCenter.service.CourseService;

@RestController
@RequestMapping("/api/public")
public class PublicCourseController {
    private final CourseService courses;

    public PublicCourseController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping("/sports/{sportId}/courses")
    public List<CourseResponse> coursesBySport(@PathVariable Integer sportId) {
        return courses.getPublicCoursesBySport(sportId);
    }

    @GetMapping("/courses/{id}")
    public CourseResponse course(@PathVariable Integer id) {
        return courses.getPublicCourseById(id);
    }
}
