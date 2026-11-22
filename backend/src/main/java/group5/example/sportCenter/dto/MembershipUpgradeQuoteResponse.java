package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Safe presentation of the immutable Upgrade quote attached to a Payment. */
public record MembershipUpgradeQuoteResponse(
        Integer sourceMembershipId,
        LocalDate sourceStartDate,
        LocalDate sourceEndDate,
        LocalDate valuationDate,
        Integer totalDays,
        Integer unusedDays,
        String targetPlanName,
        String targetPlanDescription,
        BigDecimal targetPrice,
        Integer targetDurationMonths,
        BigDecimal courseDiscountPercent,
        BigDecimal personalTrainingDiscountPercent,
        Boolean allowPersonalTrainingPlan,
        Boolean allowOneOnOneCoaching,
        BigDecimal appliedCredit,
        BigDecimal payable,
        LocalDateTime expiresAt) {
}
