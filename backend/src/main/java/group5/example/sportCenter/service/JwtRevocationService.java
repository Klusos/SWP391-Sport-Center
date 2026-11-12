package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.RevokedJwtToken;
import group5.example.sportCenter.repository.RevokedJwtTokenRepository;
import group5.example.sportCenter.security.JwtRevocationFingerprint;

@Service
public class JwtRevocationService {
    private final RevokedJwtTokenRepository revokedTokens;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    @Autowired
    public JwtRevocationService(
            RevokedJwtTokenRepository revokedTokens,
            PlatformTransactionManager transactionManager) {
        this(revokedTokens, Clock.systemUTC(), transactionManager);
    }

    JwtRevocationService(
            RevokedJwtTokenRepository revokedTokens,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.revokedTokens = revokedTokens;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isRevoked(String jti) {
        byte[] fingerprint = JwtRevocationFingerprint.fromJti(jti);
        try {
            return revokedTokens.existsById(fingerprint);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication status could not be verified", exception);
        }
    }

    public void revoke(Jwt jwt) {
        String jti = jwt.getClaimAsString("jti");
        if (!JwtRevocationFingerprint.isValidJti(jti)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid JWT identifier is required");
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The JWT has expired");
        }

        byte[] fingerprint = JwtRevocationFingerprint.fromJti(jti);
        try {
            if (revokedTokens.existsById(fingerprint)) return;
            LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            insertTransaction.executeWithoutResult(status -> revokedTokens.save(new RevokedJwtToken(
                    fingerprint,
                    LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                    nowUtc)));
        } catch (DataIntegrityViolationException exception) {
            // TransactionTemplate has completed rollback before control reaches here.
            // Only the named fingerprint PK collision plus a confirmed matching row
            // represents another request winning the same revocation race.
            if (isFingerprintPrimaryKeyConflict(exception)) {
                try {
                    if (revokedTokens.existsById(fingerprint)) return;
                } catch (DataAccessException verificationFailure) {
                    throw unavailable("Token revocation could not be verified", verificationFailure);
                }
            }
            throw unavailable("Token revocation could not be persisted", exception);
        } catch (DataAccessException exception) {
            throw unavailable("Token revocation could not be persisted", exception);
        }
    }

    private boolean isFingerprintPrimaryKeyConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("pk_auth_revoked_token")) {
                    return true;
                }
            }
            if (cause instanceof SQLException sqlException) {
                String message = sqlException.getMessage();
                String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
                boolean expectedConstraint = normalizedMessage.contains("pk_auth_revoked_token")
                        || (normalizedMessage.contains("auth_revoked_token")
                                && normalizedMessage.contains("token_fingerprint")
                                && normalizedMessage.contains("primary key"));
                boolean duplicateKey = "23505".equals(sqlException.getSQLState())
                        || sqlException.getErrorCode() == 2627
                        || sqlException.getErrorCode() == 2601;
                if (duplicateKey && expectedConstraint) return true;
            }
        }
        return false;
    }

    private ResponseStatusException unavailable(String message, DataAccessException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
