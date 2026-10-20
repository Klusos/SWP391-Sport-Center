package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StaffCreateCourseRegistrationRequest(
        @NotNull @Positive Integer memberId,
        @NotNull @Positive Integer classId) {
}
