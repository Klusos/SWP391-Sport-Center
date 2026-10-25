package group5.example.sportCenter.payment;

import java.math.BigDecimal;

/**
 * Kết quả callback đã được cổng thanh toán ký hợp lệ.
 *
 * @param amount        số tiền cổng báo đã thanh toán; null nếu cổng không gửi (mock)
 * @param failureReason ghi chú khi thất bại (vd. mã lỗi của VNPay); null nếu không có
 */
public record GatewayCallback(String transactionCode, boolean success, BigDecimal amount, String failureReason) {
}
