package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gateway giả lập để demo/test: ký HMAC-SHA256 bằng app.payment.callback-secret. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final String secret;
    private final String callbackBaseUrl;

    public MockPaymentGateway(
            @Value("${app.payment.callback-secret}") String secret,
            @Value("${app.payment.callback-base-url:http://localhost:8080/api/payments/online/callback}")
            String callbackBaseUrl) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.payment.callback-secret must not be blank");
        }
        this.secret = secret;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    @Override
    public String createPaymentUrl(String transactionCode, BigDecimal amount) {
        // Demo: URL "thanh toán thành công". Cổng thật sẽ trả về trang thanh toán của họ.
        return callbackBaseUrl + "?transactionCode=" + transactionCode
                + "&success=true&signature=" + sign(transactionCode, true);
    }

    @Override
    public boolean verifyCallback(String transactionCode, boolean success, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(transactionCode, success).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String transactionCode, boolean success) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((transactionCode + "|" + success).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign payment callback", e);
        }
    }
}
