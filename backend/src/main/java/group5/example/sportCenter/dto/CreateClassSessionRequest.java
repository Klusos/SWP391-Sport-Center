package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateClassSessionRequest {

    @NotNull(message = "Class ID is required")
    private Integer classId;

    private Integer facilityId;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @Pattern(regexp = "(?i)(SCHEDULED|COMPLETED|CANCELLED)", message = "Status must be SCHEDULED, COMPLETED, or CANCELLED")
    private String status;

    @AssertTrue(message = "End time must be after start time")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
