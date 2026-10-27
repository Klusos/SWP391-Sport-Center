package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record ProgressEvaluationResponse(
        Integer id,
        Integer classId,
        String className,
        Integer memberId,
        String memberName,
        Integer rating,
        String comment,
        Integer evaluatedById,
        String evaluatedByName,
        LocalDateTime evaluatedAt) {
}
