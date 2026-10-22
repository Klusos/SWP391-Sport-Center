package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;

public record MembershipPlanUpgradeRequest(
        @NotNull Integer fromPlanId,
        @NotNull Integer toPlanId) {}
