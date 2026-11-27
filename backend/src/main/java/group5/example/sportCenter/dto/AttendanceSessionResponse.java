package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;

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
        String coachName,
        LocalDate offeringStartDate,
        LocalDate offeringEndDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String sessionStatus,
        String attendanceCompleteness,
        int eligibleStudentCount,
        int recordedAttendanceCount,
        boolean attendanceTimeEligible) {
}
