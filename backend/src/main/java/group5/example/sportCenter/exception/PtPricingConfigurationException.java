package group5.example.sportCenter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A safe, actionable server configuration error that does not expose internal details. */
public class PtPricingConfigurationException extends ResponseStatusException {
    public PtPricingConfigurationException(String safeReason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, safeReason);
    }
}
