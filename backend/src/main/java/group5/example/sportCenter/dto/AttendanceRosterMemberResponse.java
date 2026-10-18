package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record AttendanceRosterMemberResponse(
        Integer memberId,
        Integer attendanceId,
        String memberName,
        String attendanceStatus,
        Integer markedById,
        LocalDateTime markedAt,
        String note) {
}
