package group5.example.sportCenter.dto;

import java.math.BigDecimal;

public record MembershipUpgradeOptionResponse(
        Integer targetPlanId,
        String targetPlanName,
        BigDecimal targetMonthlyPrice,
        boolean eligible,
        BigDecimal remainingCredit,
        BigDecimal upgradeAmount,
        String blocker) {}
