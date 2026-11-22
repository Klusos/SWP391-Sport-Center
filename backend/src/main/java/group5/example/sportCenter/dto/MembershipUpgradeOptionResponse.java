package group5.example.sportCenter.dto;

import java.math.BigDecimal;

/** Một gói có thể nâng lên, kèm số tiền phải trả cho số ngày còn lại của gói hiện tại. */
public record MembershipUpgradeOptionResponse(
        Integer planId,
        String planName,
        String planDescription,
        BigDecimal planPrice,
        Integer durationMonths,
        BigDecimal courseDiscountPercent,
        BigDecimal personalTrainingDiscountPercent,
        Boolean allowPersonalTrainingPlan,
        Boolean allowOneOnOneCoaching,
        BigDecimal appliedCredit,
        BigDecimal amount) {
}
