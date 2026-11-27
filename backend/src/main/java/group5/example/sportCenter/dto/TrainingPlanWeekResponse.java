package group5.example.sportCenter.dto;

import java.time.LocalDate;
import java.util.List;

public record TrainingPlanWeekResponse(
        Integer id,
        Integer weekNumber,
        String title,
        String goal,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean outsideOffering,
        List<TrainingExerciseResponse> exercises) {
}
