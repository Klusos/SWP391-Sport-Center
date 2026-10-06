package group5.example.sportCenter.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMembershipRequest {

    @NotNull(message = "Member ID is required")
    private Integer memberId;

    @NotNull(message = "Membership plan ID is required")
    private Integer planId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Auto renew option is required")
    private Boolean autoRenew;
}