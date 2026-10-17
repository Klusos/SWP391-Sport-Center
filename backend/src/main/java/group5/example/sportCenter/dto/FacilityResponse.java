package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FacilityResponse {

    private Integer id;

    private Integer sportId;
    private String sportName;

    private String name;
    private String type;
    private String status;
    private String imageUrl;
}
