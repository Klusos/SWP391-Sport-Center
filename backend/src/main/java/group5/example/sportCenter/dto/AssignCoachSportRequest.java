package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignCoachSportRequest {

    @NotNull(message = "Sport ID is required")
    private Integer sportId;

    @NotNull(message = "Experience years is required")
    @Min(value = 0, message = "Experience years cannot be negative")
    private Integer experienceYears;

    @Size(max = 255, message = "Certification cannot exceed 255 characters")
    private String certification;
}