package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFacilityRequest {

    @NotNull(message = "Sport ID is required")
    private Integer sportId;

    @NotBlank(message = "Facility name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Facility type is required")
    @Size(max = 50)
    private String type;
}