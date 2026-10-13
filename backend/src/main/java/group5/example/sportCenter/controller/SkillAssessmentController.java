package group5.example.sportCenter.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import group5.example.sportCenter.dto.*;
import group5.example.sportCenter.service.SkillAssessmentService;

@RestController
@RequestMapping("/api/skill-assessments")
public class SkillAssessmentController {
    private final SkillAssessmentService service;
    public SkillAssessmentController(SkillAssessmentService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SkillAssessmentResponse create(@Valid @RequestBody CreateSkillAssessmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, jwt);
    }
    @GetMapping
    public List<SkillAssessmentResponse> all(@AuthenticationPrincipal Jwt jwt) { return service.all(jwt); }
    @GetMapping("/my-assignments")
    public List<SkillAssessmentResponse> myAssignments(@AuthenticationPrincipal Jwt jwt) { return service.myAssignments(jwt); }
    @GetMapping("/me")
    public List<SkillAssessmentResponse> mine(@AuthenticationPrincipal Jwt jwt) { return service.mine(jwt); }
    @GetMapping("/{id}")
    public SkillAssessmentResponse get(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) { return service.get(id, jwt); }
    @PatchMapping("/{id}/complete")
    public SkillAssessmentResponse complete(@PathVariable Integer id, @Valid @RequestBody CompleteSkillAssessmentRequest request,
            @AuthenticationPrincipal Jwt jwt) { return service.complete(id, request, jwt); }
    @PatchMapping("/{id}/cancel")
    public SkillAssessmentResponse cancel(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) { return service.cancel(id, jwt); }
}
