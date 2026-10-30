package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record IndividualTrainingPlanRevisionResponse(
        Integer id,
        Integer revisionNumber,
        String goal,
        String description,
        Integer revisedById,
        String revisedByName,
        LocalDateTime createdAt,
        List<IndividualTrainingWeekResponse> weeks) {
}
