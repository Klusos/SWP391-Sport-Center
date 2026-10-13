package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record ConsultationResponse(
        Integer id,
        Integer memberId,
        String name,
        String phone,
        String email,
        Integer sportId,
        String sportName,
        Integer age,
        String experience,
        String note,
        String status,
        LocalDateTime createdAt,
        Integer handledBy,
        LocalDateTime handledAt,
        String resolutionNote,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt) {
}
