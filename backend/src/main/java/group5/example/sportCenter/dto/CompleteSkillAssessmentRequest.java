package group5.example.sportCenter.dto;

import jakarta.validation.constraints.*;

public record CompleteSkillAssessmentRequest(
        @NotBlank String assessedLevel,
        @Size(max = 1000) String notes) {}
