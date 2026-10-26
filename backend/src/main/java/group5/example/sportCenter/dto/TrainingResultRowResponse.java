package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

/** One confirmed member in a session result sheet; result fields are null until recorded. */
public record TrainingResultRowResponse(
        Integer memberId,
        String memberName,
        String attendanceStatus,
        Integer resultId,
        Integer score,
        String notes,
        Integer recordedById,
        LocalDateTime recordedAt) {
}
