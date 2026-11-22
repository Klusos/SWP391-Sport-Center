package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemberRenewalIntentResponse(
        Integer intentId,
        Integer sourceMembershipId,
        String planName,
        String planDescription,
        BigDecimal quotedAmount,
        Integer durationMonths,
        BigDecimal courseDiscountPercent,
        BigDecimal personalTrainingDiscountPercent,
        Boolean allowPersonalTrainingPlan,
        Boolean allowOneOnOneCoaching,
        LocalDate successorStartDate,
        LocalDate successorEndDate,
        LocalDateTime createdAt,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String status,
        String latestResult,
        Integer successorMembershipId,
        List<MemberRenewalPaymentAttemptResponse> attempts) {
}
