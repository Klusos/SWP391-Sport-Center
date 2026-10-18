package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record AttendanceResponse(
        Integer id,
        Integer classSessionId,
        Integer courseClassId,
        String courseClassName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String sessionStatus,
        Integer memberId,
        String memberName,
        String status,
        Integer markedById,
        LocalDateTime markedAt,
        String note,
        boolean validForStatistics) {
}
