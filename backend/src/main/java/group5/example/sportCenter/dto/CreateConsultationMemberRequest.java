package group5.example.sportCenter.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Credentials supplied by staff for the existing BCrypt-based member setup flow. */
public record CreateConsultationMemberRequest(
        @NotBlank @Size(min = 8) String password,
        @NotNull @Past LocalDate dateOfBirth) {

    @Override
    public String toString() {
        return "CreateConsultationMemberRequest[password=<redacted>, dateOfBirth=" + dateOfBirth + "]";
    }
}
