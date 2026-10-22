package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MembershipUpgradePaymentResponse(
        Integer paymentId,
        String purpose,
        String status,
        String paymentMethod,
        Integer sourceMembershipId,
        Integer targetPlanId,
        BigDecimal amount,
        BigDecimal sourcePaidAmountSnapshot,
        BigDecimal targetMonthlyPriceSnapshot,
        BigDecimal remainingCreditSnapshot,
        LocalDate businessDate,
        Instant expiresAt,
        boolean checkoutIssued,
        boolean payable) {}
