package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFacilityRequest {

    // sportId remains accepted for older clients; new clients send sportIds.
    private Integer sportId;

    private List<@NotNull(message = "Sport IDs cannot contain null") Integer> sportIds;

    @NotBlank(message = "Facility name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Facility type is required")
    @Size(max = 50)
    private String type;

    @AssertTrue(message = "At least one Sport ID is required")
    public boolean isSportSelectionValid() {
        return sportId != null || (sportIds != null && !sportIds.isEmpty());
    }
}
