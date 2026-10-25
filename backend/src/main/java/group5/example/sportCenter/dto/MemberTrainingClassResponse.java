package group5.example.sportCenter.dto;

import java.util.List;

/** Member view of one confirmed class: plan, attendance summary, session results and evaluations. */
public record MemberTrainingClassResponse(
        TrainingClassResponse courseClass,
        TrainingPlanResponse plan,
        long sessionsMarked,
        long sessionsPresent,
        Double averageScore,
        List<TrainingResultResponse> results,
        List<ProgressEvaluationResponse> evaluations) {
}
