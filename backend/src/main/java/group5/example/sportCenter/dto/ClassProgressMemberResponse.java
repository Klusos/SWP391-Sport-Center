package group5.example.sportCenter.dto;

import java.util.List;

/** Coach view of one confirmed member's progress in a class. */
public record ClassProgressMemberResponse(
        Integer memberId,
        String memberName,
        long sessionsMarked,
        long sessionsPresent,
        long resultsRecorded,
        Double averageScore,
        ProgressEvaluationResponse latestEvaluation,
        List<ProgressEvaluationResponse> evaluationHistory) {
}
