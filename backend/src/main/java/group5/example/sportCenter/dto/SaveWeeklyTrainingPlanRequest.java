package group5.example.sportCenter.dto;

import java.util.List;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveWeeklyTrainingPlanRequest(
        @JsonProperty(value = "expectedUpdatedAt", required = true) LocalDateTime expectedUpdatedAt,
        @NotBlank @Size(max = 500) String goal,
        @Size(max = 2000) String description,
        @NotNull @Size(min = 1) List<@NotNull @Valid TrainingPlanWeekRequest> weeks,
        @NotNull List<@NotNull Integer> unassignedExerciseIds) {
}
