package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberRenewalPaymentAttemptResponse(
        Integer paymentId,
        String method,
        String status,
        BigDecimal amount,
        LocalDateTime createdAt,
        LocalDateTime deadline,
        String closeReason,
        boolean collectionRecorded,
        boolean settlementAccepted,
        boolean manualReconciliationRequired) {
}
