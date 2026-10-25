package group5.example.sportCenter.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassSessionGenerationRequest {
    @NotNull(message = "CourseClass ID is required")
    private Integer courseClassId;

    @NotEmpty(message = "Select at least one weekday")
    @Size(max = 7, message = "Select no more than seven weekdays")
    private List<DayOfWeek> weekdays;

    @NotNull(message = "Daily start time is required")
    private LocalTime startTime;

    private Integer facilityId;
    private LocalDate firstEligibleDate;

    @Min(value = 1, message = "Requested session count must be positive")
    private Integer requestedSessionCount;
}
