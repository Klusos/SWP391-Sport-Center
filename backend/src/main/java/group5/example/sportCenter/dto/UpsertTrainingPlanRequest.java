package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertTrainingPlanRequest(
        @NotBlank @Size(max = 500) String goal,
        @Size(max = 2000) String description) {
}
