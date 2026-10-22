package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * C2.1 service contract for verified online captures and trusted counter receipts.
 * It deliberately does not create checkout URLs or expose a controller endpoint.
 */
@Service
public class RenewalSettlementService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RenewalSettlementTransactions transactions;
    private final Clock clock;

    public RenewalSettlementService(RenewalSettlementTransactions transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Call only after signature, merchant, and provider success validation. */
    public RenewalSettlementResult settleVerifiedOnlineCapture(VerifiedRenewalCapture capture) {
        LocalDateTime observedAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        RenewalSettlementTransactions.RecordedOnlineCapture recorded =
                transactions.recordVerifiedOnlineCapture(capture, observedAt);
        if (recorded.conflictingEvidence()) {
            return new RenewalSettlementResult(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                    recorded.paymentId(), recorded.attemptId(), recorded.appliedMembershipId(),
                    recorded.reconciliationReason());
        }
        try {
            return transactions.applyOnlineCapture(recorded.attemptId());
        } catch (DataIntegrityViolationException exception) {
            return transactions.recordOnlineReconciliation(recorded.attemptId(),
                    "A database uniqueness or relationship constraint prevented renewal application; manual reconciliation required");
        }
    }

    /**
     * Called by a future authorized COUNTER handler only after staff confirms receipt.
     * The actor is checked here as a second service-layer authorization boundary.
     */
    public RenewalSettlementResult settleConfirmedCounterReceipt(Integer paymentId, Integer staffUserId) {
        LocalDateTime receivedAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        transactions.recordCounterReceipt(paymentId, staffUserId, receivedAt);
        try {
            return transactions.applyCounterReceipt(paymentId);
        } catch (DataIntegrityViolationException exception) {
            return transactions.recordCounterReconciliation(paymentId,
                    "A database uniqueness or relationship constraint prevented renewal application; manual reconciliation required");
        }
    }
}
