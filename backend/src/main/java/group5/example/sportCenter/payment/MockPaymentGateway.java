package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo simulator only. It never contacts or verifies settlement with a payment provider.
 * It is disabled unless explicitly enabled in a demo/test profile.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final Profiles ALLOWED_PROFILES =
            Profiles.of("demo", "dev", "development", "test", "phase1");

    private final String secret;
    private final String callbackBaseUrl;
    private final boolean enabled;

    public MockPaymentGateway(
            @Value("${app.payment.callback-secret:}") String secret,
            @Value("${app.payment.callback-base-url:http://localhost:8080/api/payments/online/callback}")
            String callbackBaseUrl,
            @Value("${app.payment.mock-gateway.enabled:false}") boolean enabled,
            Environment environment) {
        this.enabled = enabled && environment.acceptsProfiles(ALLOWED_PROFILES)
                && !environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (this.enabled && (secret == null || secret.isBlank())) {
            throw new IllegalArgumentException("app.payment.callback-secret must not be blank");
        }
        this.secret = secret == null ? "" : secret;
        this.callbackBaseUrl = callbackBaseUrl;
        if (this.enabled) {
            validateLocalCallbackUrl(callbackBaseUrl);
        }
    }

    @Override
    public String createPaymentUrl(String transactionCode, BigDecimal amount, String clientIp) {
        requireEnabled();
        // Demo only: opening this local URL simulates success; it is not proof of real settlement.
        return callbackBaseUrl + "?transactionCode=" + transactionCode
                + "&success=true&signature=" + sign(transactionCode, true);
    }

    /** Callback dạng ?transactionCode=...&success=true|false&signature=... */
    @Override
    public Optional<GatewayCallback> verifyCallback(Map<String, String> params) {
        String transactionCode = params.get("transactionCode");
        String successParam = params.get("success");
        String signature = params.get("signature");
        if (!enabled || transactionCode == null || signature == null
                || !("true".equals(successParam) || "false".equals(successParam))) {
            return Optional.empty();
        }
        boolean success = Boolean.parseBoolean(successParam);
        boolean valid = MessageDigest.isEqual(
                sign(transactionCode, success).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        return valid
                ? Optional.of(new GatewayCallback(transactionCode, success, null, null))
                : Optional.empty();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Simulated online payments are disabled outside an explicitly enabled demo/test profile");
        }
    }

    private void validateLocalCallbackUrl(String callbackUrl) {
        try {
            String host = URI.create(callbackUrl).getHost();
            if (host == null || !(host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"))) {
                throw new IllegalArgumentException("Mock payment callback URL must use a loopback host");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Mock payment callback URL must be a valid local URL", exception);
        }
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
