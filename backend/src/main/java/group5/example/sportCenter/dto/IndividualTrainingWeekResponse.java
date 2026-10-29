package group5.example.sportCenter.dto;

import java.util.List;

public record IndividualTrainingWeekResponse(
        Integer weekNumber,
        String title,
        String description,
        List<TrainingExerciseResponse> exercises) {
}
