package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record MemberAttendanceResponse(
        Integer attendanceId,
        Integer classSessionId,
        Integer courseClassId,
        String courseClassName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String sessionStatus,
        Integer courseOfferingId,
        String courseOfferingName,
        Integer courseId,
        String courseName,
        String status,
        Integer markedById,
        LocalDateTime markedAt,
        String note,
        boolean validForStatistics) {
}
