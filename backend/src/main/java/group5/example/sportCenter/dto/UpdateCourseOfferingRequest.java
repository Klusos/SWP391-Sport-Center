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
public class UpdateCourseOfferingRequest {

    @NotBlank(message = "Offering name is required")
    @Size(max = 150)
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @Min(value = 1)
    private Integer plannedSessions;

    @NotNull(message = "Session duration is required")
    @Min(value = 1, message = "Session duration must be at least 1 minute")
    private Integer sessionDuration;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private LocalDateTime registrationDeadline;

    @NotNull
    private LocalDateTime cancellationDeadline;

    @NotBlank(message = "Status is required")
    private String status;
}