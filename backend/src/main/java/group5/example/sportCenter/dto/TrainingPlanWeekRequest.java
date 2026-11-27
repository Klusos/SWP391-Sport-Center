package group5.example.sportCenter.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TrainingPlanWeekRequest(
        Integer id,
        @NotNull @Min(1) Integer weekNumber,
        @Size(max = 150) String title,
        @Size(max = 500) String goal,
        @Size(max = 1000) String description,
        @NotNull @Size(max = 100) List<@NotNull @Valid TrainingPlanWeekExerciseRequest> exercises) {
}
