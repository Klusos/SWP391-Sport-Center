package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

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
                () -> gateway.createPaymentUrl("TXN-1", BigDecimal.ONE));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(gateway.verifyCallback("TXN-1", true, "anything"));
    }

    @Test
    void simulatorCanOnlyCreateVerifiableLocalDemoCallbackWhenExplicitlyEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment);

        String url = gateway.createPaymentUrl("TXN-2", BigDecimal.TEN);
        String signature = url.substring(url.indexOf("signature=") + "signature=".length());

        assertTrue(url.startsWith(CALLBACK_URL));
        assertTrue(gateway.verifyCallback("TXN-2", true, signature));
        assertFalse(gateway.verifyCallback("TXN-2", false, signature));
    }

    @Test
    void simulatorCannotBeEnabledInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        MockPaymentGateway gateway = new MockPaymentGateway(TEST_SECRET, CALLBACK_URL, true, environment);

        assertThrows(ResponseStatusException.class,
                () -> gateway.createPaymentUrl("TXN-3", BigDecimal.ONE));
        assertFalse(gateway.verifyCallback("TXN-3", true, "anything"));
    }

    @Test
    void enabledSimulatorRejectsNonLoopbackCallbackUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");

        assertThrows(IllegalArgumentException.class,
                () -> new MockPaymentGateway(TEST_SECRET, "https://payments.example.com/callback", true, environment));
    }
}
