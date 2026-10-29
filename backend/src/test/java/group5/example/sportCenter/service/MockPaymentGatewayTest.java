package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.MockPaymentGateway;

class MockPaymentGatewayTest {

    private static final String CALLBACK_URL = "http://localhost:8080/api/payments/online/callback";
    private static final String TEST_SECRET = "local-test-secret";

    @Test
    void simulatorIsDisabledByDefaultEvenInDemoProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, false, environment, Clock.systemUTC());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gateway.createPaymentUrl("TXN-1", BigDecimal.ONE, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(gateway.verifyCallback(callback("TXN-1", "true", "", "anything")).isEmpty());
    }

    @Test
    void simulatorCanOnlyCreateVerifiableLocalDemoCallbackWhenExplicitlyEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment, Clock.systemUTC());

        String url = gateway.createPaymentUrl("TXN-2", BigDecimal.TEN, null);
        Map<String, String> params = queryParams(url);
        String signature = params.get("signature");

        assertTrue(url.startsWith(CALLBACK_URL));
        GatewayCallback verified = gateway.verifyCallback(callback("TXN-2", "true", params.get("paymentTime"), signature)).orElseThrow();
        assertEquals("TXN-2", verified.transactionCode());
        assertTrue(verified.success());
        assertEquals(params.get("paymentTime"), verified.verifiedPaymentTime());
        assertTrue(gateway.verifyCallback(callback("TXN-2", "false", params.get("paymentTime"), signature)).isEmpty());
    }

    @Test
    void simulatorCannotBeEnabledInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment, Clock.systemUTC());

        assertThrows(ResponseStatusException.class,
                () -> gateway.createPaymentUrl("TXN-3", BigDecimal.ONE, null));
        assertTrue(gateway.verifyCallback(callback("TXN-3", "true", "", "anything")).isEmpty());
    }

    @Test
    void enabledSimulatorRejectsNonLoopbackCallbackUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");

        assertThrows(IllegalArgumentException.class,
                () -> new MockPaymentGateway(TEST_SECRET, "https://payments.example.com/callback", true, environment,
                        Clock.systemUTC()));
    }

    private static Map<String, String> callback(String transactionCode, String success, String paymentTime, String signature) {
        return Map.of("transactionCode", transactionCode, "success", success,
                "paymentTime", paymentTime, "signature", signature);
    }

    private static Map<String, String> queryParams(String url) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String pair : java.net.URI.create(url).getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            result.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return result;
    }
}
