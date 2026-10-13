package group5.example.sportCenter.dto;

import jakarta.validation.constraints.*;

public record CreateSkillAssessmentRequest(
        @NotNull @Positive Integer memberId,
        @NotNull @Positive Integer sportId,
        @NotNull @Positive Integer assignedCoachId,
        @Size(max = 1000) String notes,
        @Positive Integer consultationRequestId) {

    public CreateSkillAssessmentRequest(
            Integer memberId, Integer sportId, Integer assignedCoachId, String notes) {
        this(memberId, sportId, assignedCoachId, notes, null);
    }
}
