package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMembershipUpgradePathRequest(
        @NotNull @Positive Integer fromPlanId,
        @NotNull @Positive Integer toPlanId) {
}
