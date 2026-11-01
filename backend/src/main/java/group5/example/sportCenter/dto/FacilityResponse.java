package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class FacilityResponse {

    private Integer id;

    private Integer sportId;
    private String sportName;
    private List<FacilitySportResponse> sports;

    private String name;
    private String type;
    private String status;
    private String imageUrl;
}
