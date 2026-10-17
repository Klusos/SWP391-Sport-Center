package group5.example.sportCenter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CourseResponse;
import group5.example.sportCenter.service.CourseService;
import group5.example.sportCenter.dto.PublicCoachResponse;
import group5.example.sportCenter.dto.PublicFacilityResponse;
import group5.example.sportCenter.service.CoachService;
import group5.example.sportCenter.service.FacilityService;
import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicCourseController {
    private final CourseService courses;
    private final CoachService coaches;
    private final FacilityService facilities;

    public PublicCourseController(CourseService courses, CoachService coaches, FacilityService facilities) {
        this.courses = courses;
        this.coaches = coaches;
        this.facilities = facilities;
    }

    @GetMapping("/sports/{sportId}/courses")
    public List<CourseResponse> coursesBySport(@PathVariable Integer sportId) {
        return courses.getPublicCoursesBySport(sportId);
    }

    @GetMapping("/courses/{id}")
    public CourseResponse course(@PathVariable Integer id) {
        return courses.getPublicCourseById(id);
    }

    @GetMapping("/coaches")
    public List<PublicCoachResponse> coaches() { return coaches.getPublicActiveCoaches(); }

    @GetMapping("/facilities")
    public List<PublicFacilityResponse> facilities() { return facilities.getPublicActiveFacilities(); }
}
