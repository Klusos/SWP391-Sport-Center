package group5.example.sportCenter.payment;

import java.math.BigDecimal;

/**
 * Kết quả callback đã được cổng thanh toán ký hợp lệ.
 *
 * @param amount        số tiền cổng báo đã thanh toán; null nếu cổng không gửi (mock)
 * @param failureReason ghi chú khi thất bại (vd. mã lỗi của VNPay); null nếu không có
 */
public record GatewayCallback(String transactionCode, boolean success, BigDecimal amount, String failureReason,
        String providerTransactionId, String responseCode, String transactionStatus, String rawTransactionReference,
        VerifiedUpgradeCapture verifiedUpgradeCapture, VerifiedUpgradeFailure verifiedUpgradeFailure) {

    /** Compatibility constructor for non-VNPay adapters and existing test fixtures. */
    public GatewayCallback(String transactionCode, boolean success, BigDecimal amount, String failureReason) {
        this(transactionCode, success, amount, failureReason, null, null, null, transactionCode, null, null);
    }

    public GatewayCallback(String transactionCode, boolean success, BigDecimal amount, String failureReason,
            String providerTransactionId, String responseCode, String transactionStatus, String rawTransactionReference) {
        this(transactionCode, success, amount, failureReason, providerTransactionId, responseCode,
                transactionStatus, rawTransactionReference, null, null);
    }
}
