package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFacilityRequest {

    @NotBlank(message = "Facility name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Facility type is required")
    @Size(max = 50)
    private String type;

    @NotBlank(message = "Status is required")
    @Size(max = 30)
    private String status;

    // Optional for legacy clients; when omitted, retain the current assignments.
    private Integer sportId;
    private List<@NotNull(message = "Sport IDs cannot contain null") Integer> sportIds;

    @AssertTrue(message = "Sport IDs must be supplied together and contain at least one Sport")
    public boolean isSportSelectionValid() {
        return sportIds == null || !sportIds.isEmpty();
    }
}
