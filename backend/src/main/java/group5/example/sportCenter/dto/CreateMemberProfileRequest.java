package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMemberProfileRequest {

    @NotNull(message = "Member ID is required")
    private Integer memberId;

    @DecimalMin(value = "50.00", message = "Height must be at least 50 cm")
    @DecimalMax(value = "250.00", message = "Height must not exceed 250 cm")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal height;

    @DecimalMin(value = "10.00", message = "Weight must be at least 10 kg")
    @DecimalMax(value = "500.00", message = "Weight must not exceed 500 kg")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal weight;

    @Size(max = 1000)
    private String goal;
}
