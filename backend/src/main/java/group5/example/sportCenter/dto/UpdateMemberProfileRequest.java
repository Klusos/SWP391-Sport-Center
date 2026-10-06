package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberProfileRequest {

    @DecimalMin(
        value = "0.0",
        inclusive = false,
        message = "Height must be greater than 0"
    )
    private BigDecimal height;

    @DecimalMin(
        value = "0.0",
        inclusive = false,
        message = "Weight must be greater than 0"
    )
    private BigDecimal weight;

    private String goal;
}