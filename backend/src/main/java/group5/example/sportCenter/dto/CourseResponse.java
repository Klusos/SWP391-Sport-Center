package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CourseResponse {

    private Integer id;

    private Integer sportId;
    private String sportName;

    private String name;
    private String description;

    private Integer targetAgeMin;
    private Integer targetAgeMax;

    private String level;
    private String learningOutcome;

    private String status;
}