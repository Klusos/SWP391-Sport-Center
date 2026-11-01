package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.VnpayPaymentGateway;

class VnpayPaymentGatewayTest {

    private static final String TMN_CODE = "TESTTMN1";
    private static final String HASH_SECRET = "TESTHASHSECRET0123456789";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL = "http://localhost:8080/api/payments/vnpay/return";
    // 2026-10-19 05:00:00 UTC = 12:00:00 giờ Việt Nam.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T05:00:00Z"), ZoneOffset.UTC);

    private final VnpayPaymentGateway gateway =
            new VnpayPaymentGateway(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, 15, CLOCK);

    @Test
    void paymentUrlContainsRequiredVnpayParametersAndValidSignature() {
        String url = gateway.createPaymentUrl("TXN-ABC123", new BigDecimal("500000.00"), "0:0:0:0:0:0:0:1");
        Map<String, String> params = queryParams(url);

        assertTrue(url.startsWith(PAY_URL + "?"));
        assertEquals("2.1.0", params.get("vnp_Version"));
        assertEquals("pay", params.get("vnp_Command"));
        assertEquals(TMN_CODE, params.get("vnp_TmnCode"));
        assertEquals("50000000", params.get("vnp_Amount"));
        assertEquals("VND", params.get("vnp_CurrCode"));
        assertEquals("TXNABC123", params.get("vnp_TxnRef"));
        assertEquals(RETURN_URL, params.get("vnp_ReturnUrl"));
        assertEquals("127.0.0.1", params.get("vnp_IpAddr"));
        assertEquals("20261019120000", params.get("vnp_CreateDate"));
        assertEquals("20261019121500", params.get("vnp_ExpireDate"));

        String received = params.remove("vnp_SecureHash");
        assertEquals(sign(params), received);
    }

    @Test
    void successfulReturnIsVerifiedAndMappedBackToTransactionCode() {
        GatewayCallback callback = gateway.verifyCallback(signedReturn("00", "00", "50000000")).orElseThrow();

        assertEquals("TXN-ABC123", callback.transactionCode());
        assertTrue(callback.success());
        assertEquals(0, new BigDecimal("500000").compareTo(callback.amount()));
    }

    @Test
    void cancelledByCustomerIsVerifiedAsFailure() {
        // 24 = khách hàng huỷ giao dịch trên trang VNPay.
        GatewayCallback callback = gateway.verifyCallback(signedReturn("24", "02", "50000000")).orElseThrow();

        assertFalse(callback.success());
        assertTrue(callback.failureReason().contains("24"));
    }

    @Test
    void tamperedOrUnsignedReturnIsRejected() {
        Map<String, String> tampered = signedReturn("00", "00", "50000000");
        tampered.put("vnp_Amount", "100");
        assertTrue(gateway.verifyCallback(tampered).isEmpty());

        Map<String, String> unsigned = signedReturn("00", "00", "50000000");
        unsigned.remove("vnp_SecureHash");
        assertTrue(gateway.verifyCallback(unsigned).isEmpty());
    }

    @Test
    void returnSignedForAnotherMerchantIsRejected() {
        Map<String, String> params = signedReturn("00", "00", "50000000");
        params.remove("vnp_SecureHash");
        params.put("vnp_TmnCode", "OTHERTMN");
        params.put("vnp_SecureHash", sign(params));

        assertTrue(gateway.verifyCallback(params).isEmpty());
    }

    @Test
    void missingCredentialsFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new VnpayPaymentGateway("", HASH_SECRET, PAY_URL, RETURN_URL, 15, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new VnpayPaymentGateway(TMN_CODE, " ", PAY_URL, RETURN_URL, 15, CLOCK));
    }

    /** Mô phỏng tham số VNPay gửi về Return URL / IPN, ký độc lập theo đặc tả VNPay. */
    private static Map<String, String> signedReturn(String responseCode, String transactionStatus, String amount) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_BankTranNo", "VNP14000001");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan giao dich TXN-ABC123");
        params.put("vnp_PayDate", "20261019120500");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "14000001");
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TxnRef", "TXNABC123");
        params.put("vnp_SecureHash", sign(params));
        params.put("vnp_SecureHashType", "HmacSHA512");
        return params;
    }

    private static String sign(Map<String, String> params) {
        String data = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new TreeMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            params.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.US_ASCII));
        }
        return params;
    }
}
