package group5.example.sportCenter.payment;

import java.math.BigDecimal;

/** Signed non-success VNPay outcome; trusted only after gateway signature and merchant checks. */
public final class VerifiedUpgradeFailure {
    private final String attemptReference;
    private final BigDecimal amount;
    private final String providerTransactionId;
    private final String responseCode;
    private final String transactionStatus;

    VerifiedUpgradeFailure(String attemptReference, BigDecimal amount, String providerTransactionId,
            String responseCode, String transactionStatus) {
        this.attemptReference=attemptReference; this.amount=amount; this.providerTransactionId=providerTransactionId;
        this.responseCode=responseCode; this.transactionStatus=transactionStatus;
    }
    public String attemptReference() { return attemptReference; }
    public BigDecimal amount() { return amount; }
    public String providerTransactionId() { return providerTransactionId; }
    public String responseCode() { return responseCode; }
    public String transactionStatus() { return transactionStatus; }
}
