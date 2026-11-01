package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.ClassSessionResponse;
import group5.example.sportCenter.dto.CourseRegistrationResponse;
import group5.example.sportCenter.dto.CreateCourseRegistrationRequest;
import group5.example.sportCenter.service.CourseRegistrationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/course-registrations")
public class CourseRegistrationController {

    private final CourseRegistrationService service;

    public CourseRegistrationController(CourseRegistrationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseRegistrationResponse register(@Valid @RequestBody CreateCourseRegistrationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.register(request, jwt);
    }

    @GetMapping("/me")
    public List<CourseRegistrationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(jwt);
    }

    @GetMapping("/me/class-sessions")
    public List<ClassSessionResponse> myCourseSessions(@AuthenticationPrincipal Jwt jwt) {
        return service.myCourseSessions(jwt);
    }

    @PostMapping("/{id}/cancel")
    public CourseRegistrationResponse cancel(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt);
    }

    @GetMapping
    public List<CourseRegistrationResponse> all(@AuthenticationPrincipal Jwt jwt) {
        return service.all(jwt);
    }
}
