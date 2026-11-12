package group5.example.sportCenter.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class JwtRevocationFingerprint {
    private static final String DOMAIN = "SportCenter:JWT-REVOCATION:v1:";

    private JwtRevocationFingerprint() { }

    public static boolean isValidJti(String jti) {
        if (jti == null) return false;
        try {
            UUID value = UUID.fromString(jti);
            return value.toString().equalsIgnoreCase(jti)
                    && value.version() == 4
                    && value.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static byte[] fromJti(String jti) {
        if (!isValidJti(jti)) throw new IllegalArgumentException("A valid JWT identifier is required");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((DOMAIN + jti).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
