package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMembershipPlanRequest {

    @NotBlank(message = "Plan name is required")
    @Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "Monthly price is required")
    @DecimalMin(value = "0.01", message = "Monthly price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Monthly price must have at most 10 integer and 2 decimal digits")
    private BigDecimal monthlyPrice;

    private java.util.List<@Positive Integer> upgradeFromPlanIds;

    @NotNull(message = "Course discount is required")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    @Digits(integer = 3, fraction = 2, message = "Course discount must have at most 2 decimal places")
    private BigDecimal courseDiscountPercent;

    @NotNull(message = "Personal training discount is required")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    @Digits(integer = 3, fraction = 2, message = "Personal training discount must have at most 2 decimal places")
    private BigDecimal personalTrainingDiscountPercent;

    @NotNull(message = "Personal training plan permission is required")
    private Boolean allowPersonalTrainingPlan;

    @NotNull(message = "One-on-one coaching permission is required")
    private Boolean allowOneOnOneCoaching;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "(?i)^(ACTIVE|INACTIVE)$", message = "Status must be ACTIVE or INACTIVE")
    private String status;
}
