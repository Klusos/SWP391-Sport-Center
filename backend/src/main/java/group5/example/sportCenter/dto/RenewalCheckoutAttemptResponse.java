package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

/** Minimal attempt state safe to expose to the owning member's payment history. */
public record RenewalCheckoutAttemptResponse(
        Integer attemptId,
        String status,
        LocalDateTime expiresAt) {
}
