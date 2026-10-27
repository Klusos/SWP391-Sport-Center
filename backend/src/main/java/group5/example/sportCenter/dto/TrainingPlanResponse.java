package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrainingPlanResponse(
        Integer id,
        Integer classId,
        String goal,
        String description,
        Integer updatedById,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TrainingExerciseResponse> exercises) {
}
