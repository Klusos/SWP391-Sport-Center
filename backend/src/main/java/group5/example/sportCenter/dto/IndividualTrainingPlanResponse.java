package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record IndividualTrainingPlanResponse(
        Integer id,
        Integer classId,
        String className,
        Integer memberId,
        String memberName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        IndividualTrainingPlanRevisionResponse currentRevision) {
}
