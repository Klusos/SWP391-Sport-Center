package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record ConsultationResponse(
        Integer id,
        Integer memberId,
        String name,
        String phone,
        String email,
        Integer sportId,
        String sportName,
        Integer age,
        LocalDate dateOfBirth,
        String experience,
        String note,
        String status,
        LocalDateTime createdAt,
        Integer handledBy,
        String handledByName,
        LocalDateTime handledAt,
        String resolutionNote,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt) {
}
