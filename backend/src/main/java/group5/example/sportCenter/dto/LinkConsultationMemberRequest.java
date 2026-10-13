package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LinkConsultationMemberRequest(@NotNull @Positive Integer memberId) {
}
