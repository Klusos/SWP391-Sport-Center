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
import java.time.LocalDateTime;
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
        assertEquals("20261019120500", callback.verifiedPaymentTime());
        assertEquals("14000001", callback.providerTransactionReference());
        assertEquals("VND", callback.currency());
        assertTrue(callback.providerSignatureVerified());
        assertEquals(LocalDateTime.of(2026, 10, 19, 12, 5), callback.parsedVerifiedPaidAt());
        assertEquals(GatewayCallback.PaidTimeStatus.VALID, callback.paidTimeStatus());
        assertTrue(callback.isRenewalCollectionEligible());
    }

    @Test
    void renewalTransactionReferenceRoundTripsThroughVnpayAlphanumericReference() {
        String renewalTransactionCode = "TXN-REN-0123456789ABCDEF";
        Map<String, String> checkout = queryParams(
                gateway.createPaymentUrl(renewalTransactionCode, new BigDecimal("725000.00"), "127.0.0.1"));
        assertEquals("TXNREN0123456789ABCDEF", checkout.get("vnp_TxnRef"));

        Map<String, String> callbackParams = signedReturn("00", "00", "72500000");
        callbackParams.put("vnp_TxnRef", checkout.get("vnp_TxnRef"));
        GatewayCallback callback = gateway.verifyCallback(resign(callbackParams)).orElseThrow();
        assertEquals(renewalTransactionCode, callback.transactionCode());
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
    void signedReturnMayOmitOptionalPaymentDateButTimestampTamperingInvalidatesSignature() {
        Map<String, String> missingDate = signedReturn("00", "00", "50000000");
        missingDate.remove("vnp_PayDate");
        missingDate.remove("vnp_SecureHashType");
        missingDate.remove("vnp_SecureHash");
        missingDate.put("vnp_SecureHash", sign(missingDate));
        missingDate.put("vnp_SecureHashType", "HmacSHA512");
        GatewayCallback missing = gateway.verifyCallback(missingDate).orElseThrow();
        assertEquals(null, missing.verifiedPaymentTime());
        assertEquals(GatewayCallback.PaidTimeStatus.MISSING, missing.paidTimeStatus());
        assertFalse(missing.isRenewalCollectionEligible());

        Map<String, String> tamperedDate = signedReturn("00", "00", "50000000");
        tamperedDate.put("vnp_PayDate", "20261031000000");
        assertTrue(gateway.verifyCallback(tamperedDate).isEmpty());
    }

    @Test
    void missingOrMalformedProviderTransactionReferenceIsVerifiedButNotRenewalEligible() {
        Map<String, String> missingReference = signedReturn("00", "00", "50000000");
        missingReference.remove("vnp_TransactionNo");
        GatewayCallback missing = gateway.verifyCallback(resign(missingReference)).orElseThrow();
        assertTrue(missing.providerSignatureVerified());
        assertEquals(null, missing.providerTransactionReference());
        assertFalse(missing.isRenewalCollectionEligible());

        Map<String, String> malformedReference = signedReturn("00", "00", "50000000");
        malformedReference.put("vnp_TransactionNo", "not-a-provider-number");
        GatewayCallback malformed = gateway.verifyCallback(resign(malformedReference)).orElseThrow();
        assertTrue(malformed.providerSignatureVerified());
        assertFalse(malformed.isRenewalCollectionEligible());
    }

    @Test
    void signedCurrencyMismatchIsVerifiedButNotRenewalEligible() {
        Map<String, String> params = signedReturn("00", "00", "50000000");
        params.put("vnp_CurrCode", "USD");
        GatewayCallback callback = gateway.verifyCallback(resign(params)).orElseThrow();

        assertTrue(callback.providerSignatureVerified());
        assertEquals("USD", callback.currency());
        assertFalse(callback.isRenewalCollectionEligible());

        Map<String, String> missingCurrency = signedReturn("00", "00", "50000000");
        missingCurrency.remove("vnp_CurrCode");
        GatewayCallback missing = gateway.verifyCallback(resign(missingCurrency)).orElseThrow();
        assertTrue(missing.providerSignatureVerified());
        assertEquals(null, missing.currency());
        assertFalse(missing.isRenewalCollectionEligible());
    }

    @Test
    void malformedOrMissingSignedAmountRemainsVerifiedButCannotSettleRenewal() {
        Map<String, String> malformedAmount = signedReturn("00", "00", "50000000");
        malformedAmount.put("vnp_Amount", "not-an-amount");
        GatewayCallback malformed = gateway.verifyCallback(resign(malformedAmount)).orElseThrow();
        assertTrue(malformed.providerSignatureVerified());
        assertTrue(malformed.success());
        assertEquals(null, malformed.amount());
        assertFalse(malformed.isRenewalCollectionEligible());

        Map<String, String> missingAmount = signedReturn("00", "00", "50000000");
        missingAmount.remove("vnp_Amount");
        GatewayCallback missing = gateway.verifyCallback(resign(missingAmount)).orElseThrow();
        assertTrue(missing.providerSignatureVerified());
        assertTrue(missing.success());
        assertEquals(null, missing.amount());
        assertFalse(missing.isRenewalCollectionEligible());
    }

    @Test
    void malformedSignedProviderPaidTimeIsNotRenewalEligible() {
        Map<String, String> params = signedReturn("00", "00", "50000000");
        params.put("vnp_PayDate", "20261340129999");
        GatewayCallback callback = gateway.verifyCallback(resign(params)).orElseThrow();

        assertTrue(callback.providerSignatureVerified());
        assertEquals(GatewayCallback.PaidTimeStatus.MALFORMED, callback.paidTimeStatus());
        assertEquals(null, callback.parsedVerifiedPaidAt());
        assertFalse(callback.isRenewalCollectionEligible());
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
        params.put("vnp_CurrCode", "VND");
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

    private static Map<String, String> resign(Map<String, String> params) {
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");
        params.put("vnp_SecureHash", sign(params));
        return params;
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
