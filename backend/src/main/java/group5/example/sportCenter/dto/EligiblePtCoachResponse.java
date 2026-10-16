package group5.example.sportCenter.dto;

import java.math.BigDecimal;

public record EligiblePtCoachResponse(
        Integer coachId,
        String coachName,
        Integer sportId,
        String sportName,
        BigDecimal originalPrice,
        BigDecimal discount,
        BigDecimal finalPrice,
        Integer durationMinutes) {
}
