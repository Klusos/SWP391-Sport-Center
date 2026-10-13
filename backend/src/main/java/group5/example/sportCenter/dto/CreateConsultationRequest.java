package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateConsultationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        @NotNull @Positive Integer sportId,
        @Min(1) @Max(120) Integer age,
        String experience,
        String note) {
}
