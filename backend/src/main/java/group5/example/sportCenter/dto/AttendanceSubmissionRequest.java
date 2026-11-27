package group5.example.sportCenter.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record AttendanceSubmissionRequest(
        @NotEmpty List<@Valid AttendanceSubmissionMemberRequest> members) {
}
