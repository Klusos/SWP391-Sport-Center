package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PtBookingOutcomeRequest(
        @NotBlank @Pattern(regexp = "COMPLETED|NO_SHOW") String outcome,
        @Size(max = 300) String reason) { }
