package group5.example.sportCenter.service;

import java.math.BigDecimal;

/** Evidence supplied only after the gateway adapter has verified a signed success response. */
public record VerifiedRenewalCapture(
        String transactionReference,
        String providerTransactionId,
        BigDecimal reportedAmount,
        String responseCode) {
}
