package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMembershipCancellationRequest(
        @NotNull(message = "Membership ID is required") Integer membershipId,
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}
