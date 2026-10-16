package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStaffStatusRequest {
    @NotBlank
    @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    private String status;
}
