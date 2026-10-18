package group5.example.sportCenter.payment;

import java.math.BigDecimal;

/** Cổng thanh toán online. Thay MockPaymentGateway bằng VNPay/MoMo/PayOS khi tích hợp thật. */
public interface PaymentGateway {

    String createPaymentUrl(String transactionCode, BigDecimal amount);

    /** Kiểm tra callback có đúng do cổng thanh toán gửi hay không. */
    boolean verifyCallback(String transactionCode, boolean success, String signature);
}
