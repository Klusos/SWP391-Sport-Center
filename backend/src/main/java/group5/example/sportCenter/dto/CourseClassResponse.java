package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CourseClassResponse {

    private Integer id;

    private Integer courseOfferingId;
    private String courseOfferingName;

    private Integer courseId;
    private String courseName;

    private Integer sportId;
    private String sportName;

    private Integer coachId;
    private String coachName;

    private String name;
    private Integer capacity;
    private String status;
}