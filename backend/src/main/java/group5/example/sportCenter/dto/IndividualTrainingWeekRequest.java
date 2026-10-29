package group5.example.sportCenter.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IndividualTrainingWeekRequest(
        @NotNull @Min(1) @Max(52) Integer weekNumber,
        @Size(max = 150) String title,
        @Size(max = 1000) String description,
        @NotNull @Size(max = 30) List<@NotNull @Valid TrainingExerciseRequest> exercises) {
}
