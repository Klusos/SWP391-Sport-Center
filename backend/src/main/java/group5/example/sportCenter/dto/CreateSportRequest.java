package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSportRequest {

    @NotBlank(message = "Sport name is required")
    @Size(max = 100, message = "Sport name cannot exceed 100 characters")
    private String name;

    private String description;
}