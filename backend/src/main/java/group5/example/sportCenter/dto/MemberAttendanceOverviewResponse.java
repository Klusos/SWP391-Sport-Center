package group5.example.sportCenter.dto;

import java.time.LocalDate;
import java.util.List;

public record MemberAttendanceOverviewResponse(
        Integer courseClassId, String courseClassName, Integer courseId, String courseName,
        Integer courseOfferingId, String courseOfferingName, LocalDate offeringStartDate, LocalDate offeringEndDate,
        Integer sportId, String sportName, String coachName,
        int presentCount, int absentCount, int excusedCount, int recordedElapsedCount,
        int eligibleElapsedCount, int unrecordedElapsedCount, int futureSessionCount,
        List<MemberAttendanceOverviewSessionResponse> sessions) {
}
