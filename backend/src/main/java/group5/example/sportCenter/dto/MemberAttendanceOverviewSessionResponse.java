package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record MemberAttendanceOverviewSessionResponse(
        Integer classSessionId, LocalDateTime startTime, LocalDateTime endTime, String sessionStatus,
        Integer attendanceId, String attendanceStatus, boolean validForStatistics) {
}
