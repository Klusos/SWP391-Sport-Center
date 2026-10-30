package group5.example.sportCenter.dto;

import java.math.BigDecimal;

/** Aggregate of the authenticated member's pending payments whose resource is still payable. */
public record ActionablePendingPaymentSummaryResponse(long count, BigDecimal totalAmount) {
}
