package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng thanh toán online. Chọn bằng app.payment.provider:
 * "mock" (MockPaymentGateway, chỉ demo/test) hoặc "vnpay" (VnpayPaymentGateway, VNPay sandbox).
 */
public interface PaymentGateway {

    /** Tạo URL để trình duyệt chuyển sang trang thanh toán của cổng. */
    String createPaymentUrl(String transactionCode, BigDecimal amount, String clientIp);

    /**
     * Kiểm tra callback có đúng do cổng thanh toán gửi (chữ ký hợp lệ) và đọc kết quả.
     * Trả về rỗng nếu chữ ký sai hoặc thiếu tham số.
     */
    Optional<GatewayCallback> verifyCallback(Map<String, String> params);
}
