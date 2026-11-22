package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMembershipUpgradePathStatusRequest(@NotNull Boolean active) {
}
