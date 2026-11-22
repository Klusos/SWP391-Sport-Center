package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Verified gateway callback data. Verification does not imply Renewal settlement eligibility. */
public record GatewayCallback(
        String transactionCode,
        boolean success,
        BigDecimal amount,
        String failureReason,
        String verifiedPaymentTime,
        String providerTransactionReference,
        String currency,
        boolean providerSignatureVerified,
        LocalDateTime parsedVerifiedPaidAt,
        PaidTimeStatus paidTimeStatus) {

    public enum PaidTimeStatus { VALID, MISSING, MALFORMED, NOT_SIGNATURE_VERIFIED }

    /** Compatibility constructor for existing/mock gateway consumers. */
    public GatewayCallback(String transactionCode, boolean success, BigDecimal amount,
            String failureReason, String verifiedPaymentTime) {
        this(transactionCode, success, amount, failureReason, verifiedPaymentTime,
                null, null, false, null,
                verifiedPaymentTime == null || verifiedPaymentTime.isBlank()
                        ? PaidTimeStatus.MISSING : PaidTimeStatus.NOT_SIGNATURE_VERIFIED);
    }

    /** A verified callback is Renewal-eligible only when the required signed collection facts validate. */
    public boolean isRenewalCollectionEligible() {
        return providerSignatureVerified && success && amount != null
                && providerTransactionReference != null
                && providerTransactionReference.matches("[0-9]+")
                && "VND".equals(currency)
                && paidTimeStatus == PaidTimeStatus.VALID
                && parsedVerifiedPaidAt != null;
    }
}
