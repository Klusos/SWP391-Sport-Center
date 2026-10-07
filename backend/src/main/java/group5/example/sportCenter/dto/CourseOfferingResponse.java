package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CourseOfferingResponse {

    private Integer id;

    private Integer courseId;
    private String courseName;

    private Integer sportId;
    private String sportName;

    private String name;
    private BigDecimal price;

    private Integer plannedSessions;
    private Integer sessionDuration;
    
    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime registrationDeadline;
    private LocalDateTime cancellationDeadline;

    private String status;
}