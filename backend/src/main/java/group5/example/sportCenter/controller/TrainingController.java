package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.ClassProgressMemberResponse;
import group5.example.sportCenter.dto.MemberTrainingClassResponse;
import group5.example.sportCenter.dto.ProgressEvaluationRequest;
import group5.example.sportCenter.dto.ProgressEvaluationResponse;
import group5.example.sportCenter.dto.RecordTrainingResultRequest;
import group5.example.sportCenter.dto.TrainingClassResponse;
import group5.example.sportCenter.dto.TrainingExerciseRequest;
import group5.example.sportCenter.dto.TrainingExerciseResponse;
import group5.example.sportCenter.dto.TrainingPlanResponse;
import group5.example.sportCenter.dto.IndividualTrainingPlanRequest;
import group5.example.sportCenter.dto.IndividualTrainingPlanResponse;
import group5.example.sportCenter.dto.IndividualTrainingPlanRevisionResponse;
import group5.example.sportCenter.service.IndividualTrainingPlanService;
import group5.example.sportCenter.dto.TrainingResultResponse;
import group5.example.sportCenter.dto.TrainingResultSheetResponse;
import group5.example.sportCenter.dto.TrainingSessionResponse;
import group5.example.sportCenter.dto.UpsertTrainingPlanRequest;
import group5.example.sportCenter.service.TrainingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;
    private final IndividualTrainingPlanService individualTrainingPlans;

    public TrainingController(TrainingService trainingService, IndividualTrainingPlanService individualTrainingPlans) {
        this.trainingService = trainingService;
        this.individualTrainingPlans = individualTrainingPlans;
    }

    @GetMapping("/classes")
    public List<TrainingClassResponse> classes(@AuthenticationPrincipal Jwt jwt) {
        return trainingService.classes(jwt);
    }

    @GetMapping("/classes/{classId}/plan")
    public TrainingPlanResponse plan(@PathVariable Integer classId, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.plan(classId, jwt);
    }

    @PutMapping("/classes/{classId}/plan")
    public TrainingPlanResponse upsertPlan(@PathVariable Integer classId,
            @Valid @RequestBody UpsertTrainingPlanRequest request, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.upsertPlan(classId, request, jwt);
    }

    @PostMapping("/classes/{classId}/plan/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingExerciseResponse addExercise(@PathVariable Integer classId,
            @Valid @RequestBody TrainingExerciseRequest request, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.addExercise(classId, request, jwt);
    }

    @PutMapping("/exercises/{exerciseId}")
    public TrainingExerciseResponse updateExercise(@PathVariable Integer exerciseId,
            @Valid @RequestBody TrainingExerciseRequest request, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.updateExercise(exerciseId, request, jwt);
    }

    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable Integer exerciseId, @AuthenticationPrincipal Jwt jwt) {
        trainingService.deleteExercise(exerciseId, jwt);
    }

    @GetMapping("/classes/{classId}/sessions")
    public List<TrainingSessionResponse> classSessions(@PathVariable Integer classId, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.classSessions(classId, jwt);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public TrainingSessionResponse completeSession(@PathVariable Integer sessionId, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.completeSession(sessionId, jwt);
    }

    @GetMapping("/sessions/{sessionId}/results")
    public TrainingResultSheetResponse resultSheet(@PathVariable Integer sessionId, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.resultSheet(sessionId, jwt);
    }

    @PutMapping("/sessions/{sessionId}/results/{memberId}")
    public TrainingResultResponse recordResult(@PathVariable Integer sessionId, @PathVariable Integer memberId,
            @Valid @RequestBody RecordTrainingResultRequest request, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.recordResult(sessionId, memberId, request, jwt);
    }

    @GetMapping("/classes/{classId}/progress")
    public List<ClassProgressMemberResponse> classProgress(@PathVariable Integer classId,
            @AuthenticationPrincipal Jwt jwt) {
        return trainingService.classProgress(classId, jwt);
    }

    @PostMapping("/classes/{classId}/members/{memberId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressEvaluationResponse evaluate(@PathVariable Integer classId, @PathVariable Integer memberId,
            @Valid @RequestBody ProgressEvaluationRequest request, @AuthenticationPrincipal Jwt jwt) {
        return trainingService.evaluate(classId, memberId, request, jwt);
    }

    @GetMapping("/me")
    public List<MemberTrainingClassResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return trainingService.mine(jwt);
    }

    @PostMapping("/classes/{classId}/members/{memberId}/individual-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public IndividualTrainingPlanResponse createIndividualPlan(@PathVariable Integer classId,
            @PathVariable Integer memberId, @Valid @RequestBody IndividualTrainingPlanRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.create(classId, memberId, request, jwt);
    }

    @GetMapping("/classes/{classId}/individual-plans")
    public List<IndividualTrainingPlanResponse> individualPlansForClass(@PathVariable Integer classId,
            @AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.forClass(classId, jwt);
    }

    @GetMapping("/me/individual-plans")
    public List<IndividualTrainingPlanResponse> myIndividualPlans(@AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.mine(jwt);
    }

    @GetMapping("/individual-plans/{planId}")
    public IndividualTrainingPlanResponse individualPlan(@PathVariable Integer planId,
            @AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.detail(planId, jwt);
    }

    @PutMapping("/individual-plans/{planId}")
    public IndividualTrainingPlanResponse reviseIndividualPlan(@PathVariable Integer planId,
            @Valid @RequestBody IndividualTrainingPlanRequest request, @AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.revise(planId, request, jwt);
    }

    @GetMapping("/individual-plans/{planId}/revisions")
    public List<IndividualTrainingPlanRevisionResponse> individualPlanHistory(@PathVariable Integer planId,
            @AuthenticationPrincipal Jwt jwt) {
        return individualTrainingPlans.history(planId, jwt);
    }
}
