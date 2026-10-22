package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * VNPay (API v2.1.0) — dùng với môi trường sandbox: https://sandbox.vnpayment.vn.
 * Bật bằng app.payment.provider=vnpay; TMN code và hash secret lấy từ email đăng ký sandbox.
 * Chữ ký: HMAC-SHA512 trên các tham số vnp_* (bỏ giá trị rỗng) sắp xếp theo tên,
 * nối dạng key=URLEncoder(value) bằng '&'.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "vnpay")
public class VnpayPaymentGateway implements PaymentGateway {

    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final String TXN_PREFIX = "TXN-";

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final int expireMinutes;
    private final Clock clock;

    public VnpayPaymentGateway(
            @Value("${app.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${app.payment.vnpay.hash-secret:}") String hashSecret,
            @Value("${app.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${app.payment.vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}") String returnUrl,
            @Value("${app.payment.vnpay.expire-minutes:15}") int expireMinutes,
            Clock clock) {
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "app.payment.vnpay.tmn-code and app.payment.vnpay.hash-secret are required when provider=vnpay");
        }
        this.tmnCode = tmnCode.trim();
        this.hashSecret = hashSecret.trim();
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
        this.expireMinutes = expireMinutes;
        this.clock = clock;
    }

    @Override
    public String createPaymentUrl(String transactionCode, BigDecimal amount, String clientIp) {
        return createUrl(transactionCode, amount, clientIp, LocalDateTime.now(clock.withZone(VNPAY_ZONE)));
    }

    @Override
    public String createRenewalPaymentUrl(String transactionReference, BigDecimal amount,
            String clientIp, LocalDateTime createdAt) {
        return createUrl(transactionReference, amount, clientIp, createdAt);
    }

    @Override
    public boolean supportsUpgradeCheckout() { return true; }

    @Override
    public String createUpgradePaymentUrl(String transactionReference, BigDecimal amount,
            String clientIp, LocalDateTime createdAt, LocalDateTime expiresAt) {
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Upgrade checkout expiry must be after its creation time");
        }
        if (transactionReference == null || !transactionReference.matches("UGP[0-9A-F]{32}")) {
            throw new IllegalArgumentException("Upgrade checkout reference is malformed");
        }
        return createUrl(transactionReference, amount, clientIp, createdAt, expiresAt);
    }

    private String createUrl(String transactionCode, BigDecimal amount, String clientIp, LocalDateTime now) {
        return createUrl(transactionCode, amount, clientIp, now, now.plusMinutes(expireMinutes));
    }

    private String createUrl(String transactionCode, BigDecimal amount, String clientIp,
            LocalDateTime now, LocalDateTime expiresAt) {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        // VNPay nhận số tiền VND nhân 100 (không có phần thập phân).
        params.put("vnp_Amount", amount.movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", toTxnRef(transactionCode));
        params.put("vnp_OrderInfo", "Thanh toan giao dich " + transactionCode);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", now.format(VNPAY_TIME));
        params.put("vnp_ExpireDate", expiresAt.format(VNPAY_TIME));

        String query = encode(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    @Override
    public boolean supportsRenewalCheckout() { return true; }

    @Override
    public int renewalCheckoutExpiryMinutes() { return expireMinutes; }

    @Override
    public Optional<GatewayCallback> verifyCallback(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return Optional.empty();
        }
        SortedMap<String, String> signed = params.entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_"))
                .filter(e -> !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType"))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        String expectedHash = hmacSha512(encode(signed));
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        if (!tmnCode.equals(signed.get("vnp_TmnCode")) || signed.get("vnp_TxnRef") == null) {
            return Optional.empty();
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(signed.get("vnp_Amount")).movePointLeft(2);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String responseCode = signed.get("vnp_ResponseCode");
        String transactionStatus = signed.get("vnp_TransactionStatus");
        if (responseCode == null || !responseCode.matches("[0-9]{2}")
                || transactionStatus == null || !transactionStatus.matches("[0-9]{2}")) {
            return Optional.empty();
        }
        boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);
        String failureReason = success ? null
                : "VNPay payment failed (response code " + responseCode + ", status " + transactionStatus + ")";
        String rawReference = signed.get("vnp_TxnRef");
        if (rawReference != null && rawReference.startsWith("UGP")
                && (!rawReference.matches("UGP[0-9A-F]{32}") || !"VND".equals(signed.get("vnp_CurrCode")))) {
            return Optional.empty();
        }
        String providerTransactionId = signed.get("vnp_TransactionNo");
        if (providerTransactionId == null || providerTransactionId.isBlank() || providerTransactionId.length() > 100) {
            return Optional.empty();
        }
        // UGP references identify a persisted Upgrade checkout attempt. Do not map them
        // to a parent Payment transaction code; the IPN dispatcher resolves the attempt.
        String transactionCode = rawReference != null && rawReference.matches("UGP[0-9A-Fa-f]{32}")
                ? rawReference.toUpperCase(Locale.ROOT) : fromTxnRef(rawReference);
        VerifiedUpgradeCapture verifiedCapture = null;
        VerifiedUpgradeFailure verifiedFailure = null;
        String payDate = signed.get("vnp_PayDate");
        if (success) {
            java.time.Instant capturedAt = null;
            if (payDate != null && payDate.matches("[0-9]{14}")) {
                try {
                    var localCaptureTime = LocalDateTime.parse(payDate,
                            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT));
                    capturedAt = localCaptureTime.atZone(VNPAY_ZONE).toInstant();
                } catch (RuntimeException ignored) {
                    // A signed but malformed/missing provider time is not usable as capture proof.
                }
            }
            verifiedCapture = new VerifiedUpgradeCapture(transactionCode, amount, providerTransactionId,
                    responseCode, capturedAt);
        } else if (rawReference != null && rawReference.matches("UGP[0-9A-Fa-f]{32}")) {
            verifiedFailure = new VerifiedUpgradeFailure(transactionCode, amount, providerTransactionId,
                    responseCode, transactionStatus);
        }
        return Optional.of(new GatewayCallback(transactionCode, success, amount, failureReason,
                providerTransactionId, responseCode, transactionStatus, rawReference, verifiedCapture, verifiedFailure));
    }

    /** vnp_TxnRef chỉ nên chứa chữ và số: "TXN-ABC123" -> "TXNABC123". */
    private static String toTxnRef(String transactionCode) {
        return transactionCode.replace("-", "");
    }

    private static String fromTxnRef(String txnRef) {
        String prefix = TXN_PREFIX.replace("-", "");
        if (txnRef.startsWith(prefix) && !txnRef.contains("-"))
            return TXN_PREFIX + txnRef.substring(prefix.length());
        if (txnRef.matches("UPG[A-F0-9]{16}")) return "UPG-" + txnRef.substring(3);
        return txnRef;
    }

    private static String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || clientIp.equals("0:0:0:0:0:0:0:1") || clientIp.equals("::1")) {
            return "127.0.0.1";
        }
        return clientIp;
    }

    private static String encode(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign VNPay request", e);
        }
    }
}
