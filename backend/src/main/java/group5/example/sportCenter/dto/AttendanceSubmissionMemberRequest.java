package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceSubmissionMemberRequest(
        @NotNull Integer memberId,
        @NotNull AttendanceStatus status,
        @Size(max = 500) String note) {
}
