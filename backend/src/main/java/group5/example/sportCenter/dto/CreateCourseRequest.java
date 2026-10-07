package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCourseRequest {

    @NotNull(message = "Sport ID is required")
    private Integer sportId;

    @NotBlank(message = "Course name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @Min(value = 0, message = "Minimum age cannot be negative")
    private Integer targetAgeMin;

    @Min(value = 0, message = "Maximum age cannot be negative")
    private Integer targetAgeMax;

    @Size(max = 50)
    private String level;

    private String learningOutcome;
}