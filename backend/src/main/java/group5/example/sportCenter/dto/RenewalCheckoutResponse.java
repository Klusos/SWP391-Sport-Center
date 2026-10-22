package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RenewalCheckoutResponse(
        Integer paymentId,
        Integer checkoutAttemptId,
        String attemptReference,
        BigDecimal amount,
        LocalDateTime expiresAt,
        String checkoutUrl) {
}
