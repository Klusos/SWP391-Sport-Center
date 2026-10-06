package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMembershipPlanRequest {

    @NotBlank(message = "Plan name is required")
    @Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0")
    private Integer durationMonths;

    @NotNull(message = "Course discount is required")
    @DecimalMin(value = "0.0", message = "Course discount cannot be negative")
    @DecimalMax(value = "100.0", message = "Course discount cannot exceed 100")
    private BigDecimal courseDiscountPercent;

    @NotNull(message = "Personal training discount is required")
    @DecimalMin(value = "0.0", message = "Personal training discount cannot be negative")
    @DecimalMax(value = "100.0", message = "Personal training discount cannot exceed 100")
    private BigDecimal personalTrainingDiscountPercent;

    @NotNull(message = "Personal training plan permission is required")
    private Boolean allowPersonalTrainingPlan;

    @NotNull(message = "One-on-one coaching permission is required")
    private Boolean allowOneOnOneCoaching;
}