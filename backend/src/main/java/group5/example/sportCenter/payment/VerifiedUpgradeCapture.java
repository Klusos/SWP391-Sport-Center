package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Capture evidence created only by a PaymentGateway adapter after its signature and
 * merchant checks succeed. Its package-private constructor prevents HTTP/controller
 * code from manufacturing a successful capture.
 */
public final class VerifiedUpgradeCapture {
    private final String transactionCode;
    private final BigDecimal amount;
    private final String providerTransactionId;
    private final String responseCode;
    private final Instant capturedAt;

    VerifiedUpgradeCapture(String transactionCode, BigDecimal amount, String providerTransactionId,
            String responseCode, Instant capturedAt) {
        this.transactionCode = transactionCode;
        this.amount = amount;
        this.providerTransactionId = providerTransactionId;
        this.responseCode = responseCode;
        this.capturedAt = capturedAt;
    }

    public String transactionCode() { return transactionCode; }
    public BigDecimal amount() { return amount; }
    public String providerTransactionId() { return providerTransactionId; }
    public String responseCode() { return responseCode; }
    public Instant capturedAt() { return capturedAt; }
}
