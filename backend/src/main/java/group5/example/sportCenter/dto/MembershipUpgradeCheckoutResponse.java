package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MembershipUpgradeCheckoutResponse(Integer paymentId, Integer attemptId,
        String attemptReference, BigDecimal amount, Instant expiresAt, String checkoutUrl) {}
