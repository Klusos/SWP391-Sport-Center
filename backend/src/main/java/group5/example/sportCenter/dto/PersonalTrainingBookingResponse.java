package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PersonalTrainingBookingResponse(
        Integer id,
        Integer memberId,
        String memberName,
        Integer coachId,
        String coachName,
        Integer sportId,
        String sportName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        BigDecimal originalPrice,
        BigDecimal discount,
        BigDecimal finalPrice,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paymentExpiresAt) {
}
