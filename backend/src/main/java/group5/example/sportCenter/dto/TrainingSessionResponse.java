package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record TrainingSessionResponse(
        Integer sessionId,
        Integer classId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        long presentCount,
        long resultCount,
        boolean canComplete,
        boolean canRecordResults) {
}
