package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AttendanceRosterResponse(
        Integer classSessionId,
        Integer courseClassId,
        String courseClassName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String sessionStatus,
        boolean markingAllowed,
        List<AttendanceRosterMemberResponse> members) {
}
