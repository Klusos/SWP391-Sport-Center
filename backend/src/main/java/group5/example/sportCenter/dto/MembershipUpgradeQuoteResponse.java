package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MembershipUpgradeQuoteResponse(
        LocalDate businessDate,
        String businessTimeZone,
        String currency,
        String prorationMethod,
        String roundingConvention,
        Integer currentEffectiveMembershipCount,
        Integer sourceMembershipId,
        Integer sourcePlanId,
        String sourcePlanName,
        BigDecimal sourceMonthlyPrice,
        LocalDate sourceStartDate,
        LocalDate sourceEndDate,
        Boolean sourceAutoRenew,
        Integer originalDurationDays,
        Integer remainingDays,
        BigDecimal actualPaidAmount,
        BigDecimal remainingCredit,
        boolean eligible,
        List<String> blockers,
        List<MembershipUpgradeOptionResponse> upgradeOptions) {}
