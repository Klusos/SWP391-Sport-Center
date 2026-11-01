package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, false, environment);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gateway.createPaymentUrl("TXN-1", BigDecimal.ONE, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(gateway.verifyCallback(callback("TXN-1", "true", "anything")).isEmpty());
    }

    @Test
    void simulatorCanOnlyCreateVerifiableLocalDemoCallbackWhenExplicitlyEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment);

        String url = gateway.createPaymentUrl("TXN-2", BigDecimal.TEN, null);
        String signature = url.substring(url.indexOf("signature=") + "signature=".length());

        assertTrue(url.startsWith(CALLBACK_URL));
        GatewayCallback verified = gateway.verifyCallback(callback("TXN-2", "true", signature)).orElseThrow();
        assertEquals("TXN-2", verified.transactionCode());
        assertTrue(verified.success());
        assertTrue(gateway.verifyCallback(callback("TXN-2", "false", signature)).isEmpty());
    }

    @Test
    void simulatorCannotBeEnabledInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment);

        assertThrows(ResponseStatusException.class,
                () -> gateway.createPaymentUrl("TXN-3", BigDecimal.ONE, null));
        assertTrue(gateway.verifyCallback(callback("TXN-3", "true", "anything")).isEmpty());
    }

    @Test
    void enabledSimulatorRejectsNonLoopbackCallbackUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");

        assertThrows(IllegalArgumentException.class,
                () -> new MockPaymentGateway(TEST_SECRET, "https://payments.example.com/callback", true, environment));
    }

    private static Map<String, String> callback(String transactionCode, String success, String signature) {
        return Map.of("transactionCode", transactionCode, "success", success, "signature", signature);
    }
}
