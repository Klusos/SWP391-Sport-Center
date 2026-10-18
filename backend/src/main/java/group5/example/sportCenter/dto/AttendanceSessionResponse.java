package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record AttendanceSessionResponse(
        Integer classSessionId,
        Integer courseClassId,
        String courseClassName,
        Integer courseOfferingId,
        String courseOfferingName,
        Integer courseId,
        String courseName,
        Integer sportId,
        String sportName,
        Integer assignedCoachId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String sessionStatus) {
}
