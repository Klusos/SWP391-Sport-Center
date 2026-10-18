package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkAttendanceRequest(
        @NotNull AttendanceStatus status,
        @Size(max = 500) String note,
        @Size(max = 500) String reason) {
}
