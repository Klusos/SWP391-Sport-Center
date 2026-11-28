package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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
    @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer and 2 decimal digits")
    private BigDecimal price;

    @NotNull(message = "Planned sessions are required")
    @Min(value = 1, message = "Số buổi dự kiến phải từ 1 đến 60")
    @Max(value = 60, message = "Số buổi dự kiến phải từ 1 đến 60")
    private BigDecimal plannedSessions;

    @NotNull(message = "Session duration is required")
    @Min(value = 30, message = "Thời lượng buổi học phải từ 30 đến 180 phút")
    @Max(value = 180, message = "Thời lượng buổi học phải từ 30 đến 180 phút")
    private BigDecimal sessionDuration;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Registration deadline is required")
    private LocalDateTime registrationDeadline;

    @NotNull(message = "Cancellation deadline is required")
    private LocalDateTime cancellationDeadline;
}
