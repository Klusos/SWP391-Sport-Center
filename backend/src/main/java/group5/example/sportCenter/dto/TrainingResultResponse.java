package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record TrainingResultResponse(
        Integer id,
        Integer sessionId,
        Integer classId,
        String className,
        LocalDateTime sessionStartTime,
        LocalDateTime sessionEndTime,
        Integer memberId,
        String memberName,
        Integer score,
        String notes,
        Integer recordedById,
        String recordedByName,
        LocalDateTime recordedAt) {
}
