package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrainingExerciseRequest(
        @NotBlank @Size(max = 150) String name,
        @Min(1) @Max(100) Integer sets,
        @Min(1) @Max(1000) Integer reps,
        @Min(1) @Max(240) Integer durationMinutes,
        @Size(max = 500) String notes,
        @Min(0) @Max(1000) Integer sortOrder) {
}
