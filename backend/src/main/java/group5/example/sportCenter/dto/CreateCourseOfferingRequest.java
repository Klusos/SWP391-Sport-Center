package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCourseOfferingRequest {

    @NotNull(message = "Course ID is required")
    private Integer courseId;

    @NotBlank(message = "Offering name is required")
    @Size(max = 150)
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(
        value = "0.0",
        inclusive = true,
        message = "Price cannot be negative"
    )
    private BigDecimal price;

    @Min(
        value = 1,
        message = "Planned sessions must be at least 1"
    )
    private Integer plannedSessions;

    @NotNull(message = "Session duration is required")
    @Min(value = 1, message = "Session duration must be at least 1 minute")
    private Integer sessionDuration;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Registration deadline is required")
    private LocalDateTime registrationDeadline;

    @NotNull(message = "Cancellation deadline is required")
    private LocalDateTime cancellationDeadline;
}