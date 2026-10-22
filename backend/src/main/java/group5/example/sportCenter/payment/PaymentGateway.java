package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng thanh toán online. Chọn bằng app.payment.provider:
 * "mock" (MockPaymentGateway, chỉ demo/test) hoặc "vnpay" (VnpayPaymentGateway, VNPay sandbox).
 */
public interface PaymentGateway {

    /** True only for a real provider adapter allowed to issue persisted renewal checkout attempts. */
    default boolean supportsRenewalCheckout() { return false; }

    /** Provider checkout lifetime in minutes; meaningful only when supportsRenewalCheckout is true. */
    default int renewalCheckoutExpiryMinutes() {
        throw new UnsupportedOperationException("This gateway does not support renewal checkout");
    }

    /** True only for adapters that can issue a URL pinned to an Upgrade draft's absolute expiry. */
    default boolean supportsUpgradeCheckout() { return false; }

    default String createUpgradePaymentUrl(String transactionReference, BigDecimal amount,
            String clientIp, LocalDateTime createdAt, LocalDateTime expiresAt) {
        throw new UnsupportedOperationException("This gateway does not support Upgrade checkout");
    }

    /** Tạo URL để trình duyệt chuyển sang trang thanh toán của cổng. */
    String createPaymentUrl(String transactionCode, BigDecimal amount, String clientIp);

    /** Renewal URL variant pins signed provider timestamps to the persisted attempt timestamps. */
    default String createRenewalPaymentUrl(String transactionReference, BigDecimal amount,
            String clientIp, LocalDateTime createdAt) {
        return createPaymentUrl(transactionReference, amount, clientIp);
    }

    /**
     * Kiểm tra callback có đúng do cổng thanh toán gửi (chữ ký hợp lệ) và đọc kết quả.
     * Trả về rỗng nếu chữ ký sai hoặc thiếu tham số.
     */
    Optional<GatewayCallback> verifyCallback(Map<String, String> params);
}
