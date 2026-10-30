package group5.example.sportCenter.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IndividualTrainingPlanRequest(
        @NotBlank @Size(max = 500) String goal,
        @Size(max = 2000) String description,
        @NotEmpty @Size(max = 52) List<@NotNull @Valid IndividualTrainingWeekRequest> weeks) {
}
