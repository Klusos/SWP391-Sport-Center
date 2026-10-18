package group5.example.sportCenter.entity;

/**
 * REFUND_REQUIRED: tiền đã nhận nhưng không áp dụng được (PT booking hết hạn giữ chỗ,
 * đăng ký đã bị huỷ, hoặc đối tượng đã được thanh toán trước đó). Không tính vào doanh thu.
 */
public enum PaymentStatus { PENDING, SUCCESS, CANCELLED, REFUND_REQUIRED }
