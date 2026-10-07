package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCoachStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;
}