package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateClassSessionStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "(?i)(SCHEDULED|COMPLETED|CANCELLED)", message = "Status must be SCHEDULED, COMPLETED, or CANCELLED")
    private String status;
}
