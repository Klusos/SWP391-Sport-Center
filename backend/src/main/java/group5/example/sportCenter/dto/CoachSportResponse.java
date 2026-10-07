package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CoachSportResponse {

    private Integer id;

    private Integer coachId;
    private String coachName;

    private Integer sportId;
    private String sportName;

    private Integer experienceYears;
    private String certification;
}