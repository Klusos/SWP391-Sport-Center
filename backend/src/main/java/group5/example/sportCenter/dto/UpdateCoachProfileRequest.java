package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCoachProfileRequest {
    @Size(max = 1000, message = "Biography cannot exceed 1000 characters")
    private String bio;
}
