package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordTrainingResultRequest(
        @NotNull @Min(1) @Max(10) Integer score,
        @Size(max = 1000) String notes) {
}
