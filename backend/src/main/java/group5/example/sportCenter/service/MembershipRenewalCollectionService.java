package group5.example.sportCenter.service;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.repository.PaymentRepository;

/** Internal two-transaction coordinator. It is not connected to callbacks, APIs, or a scheduler. */
@Service
public class MembershipRenewalCollectionService {
    static final String PROVIDER_REFERENCE_INDEX = "ux_payment_renewal_provider_transaction_reference";
    private final MembershipRenewalCollectionPersistenceService persistence;
    private final MembershipRenewalSettlementService settlement;
    private final PaymentRepository payments;
    private final NotificationService notifications;

    public MembershipRenewalCollectionService(MembershipRenewalCollectionPersistenceService persistence,
            MembershipRenewalSettlementService settlement, PaymentRepository payments,
            NotificationService notifications) {
        this.persistence = persistence;
        this.settlement = settlement;
        this.payments = payments;
        this.notifications = notifications;
    }

    /** Commits collection evidence, then invokes settlement after the evidence transaction has ended. */
    public RenewalSettlementResult recordAndSettle(RenewalSettlementEvidence evidence) {
        requireNoCallerTransaction();
        RenewalCollectionCaptureResult captured;
        try {
            captured = persistence.recordFirstCollection(evidence);
        } catch (RuntimeException persistenceFailure) {
            if (isProviderReferenceUniqueViolation(persistenceFailure)
                    && evidence != null && evidence.method() == PaymentMethod.ONLINE
                    && evidence.providerTransactionReference() != null) {
                persistence.markConcurrentProviderReferenceConflict(evidence.paymentId());
                return RenewalSettlementResult.of(RenewalSettlementOutcome.RECONCILIATION_REQUIRED,
                        findPayment(evidence),
                        "A duplicate VNPay provider transaction reference requires manual handling");
            }
            throw persistenceFailure;
        }
        return switch (captured.status()) {
            case RECORDED, IDEMPOTENT -> settlement.settle(captured.evidence());
            case CONFLICT -> {
                Payment payment = findPayment(evidence);
                if (payment != null && payment.getRenewalIntent() != null) {
                    notifications.renewalReconciliationRequired(payment.getId(), payment.getMember().getId());
                }
                yield RenewalSettlementResult.of(RenewalSettlementOutcome.RECONCILIATION_REQUIRED,
                        payment, captured.detail());
            }
            case NOT_COLLECTED -> RenewalSettlementResult.of(RenewalSettlementOutcome.PAYMENT_NOT_COLLECTED,
                    findPayment(evidence), captured.detail());
            case INVALID -> RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE,
                    findPayment(evidence), captured.detail());
        };
    }

    /**
     * Crash recovery entry point: a repeated trusted delivery can replay the committed Payment facts without
     * creating another collection or requiring a scheduler/event ledger.
     */
    public RenewalSettlementResult resumeRecordedSettlement(Integer paymentId) {
        requireNoCallerTransaction();
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null || payment.getRenewalIntent() == null || !payment.hasRecordedRenewalCollection()) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment,
                    "No durable Renewal collection exists for this Payment");
        }
        if (payment.getRenewalCollectionConflictDetectedAt() != null) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, payment,
                    "A collection conflict is pending manual handling");
        }
        return settlement.settle(evidenceFrom(payment));
    }

    private RenewalSettlementEvidence evidenceFrom(Payment payment) {
        if (payment.getRenewalPaidTimeEvidenceType() == null
                || payment.getRenewalVerifiedCollectionAmount() == null
                || payment.getRenewalVerifiedCollectionCurrency() == null
                || payment.getMethod() == null) {
            return RenewalSettlementEvidence.invalid(payment.getRenewalIntent().getId(), payment.getId(),
                    payment.getMethod(), "Recorded Renewal collection is incomplete");
        }
        RenewalSettlementEvidence.PaidAtEvidence timeEvidence = switch (payment.getRenewalPaidTimeEvidenceType()) {
            case PROVIDER_VERIFIED -> RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME;
            case SERVER_CONFIRMATION -> RenewalSettlementEvidence.PaidAtEvidence.SERVER_CONFIRMATION_TIME;
            case MISSING -> RenewalSettlementEvidence.PaidAtEvidence.MISSING;
            case MALFORMED -> RenewalSettlementEvidence.PaidAtEvidence.MALFORMED;
        };
        String provenance = payment.getMethod() == PaymentMethod.ONLINE
                ? "VNPAY_SIGNATURE_VERIFIED" : "COUNTER_SERVER_CONFIRMATION";
        String reference = payment.getRenewalProviderTransactionReference() != null
                ? payment.getRenewalProviderTransactionReference() : payment.getTransactionCode();
        return new RenewalSettlementEvidence(payment.getRenewalIntent().getId(), payment.getId(), payment.getMethod(),
                RenewalSettlementEvidence.CollectionOutcome.COLLECTED, true, payment.getPaidAt(), timeEvidence,
                provenance, reference, null, payment.getRenewalVerifiedCollectionAmount(),
                payment.getRenewalVerifiedCollectionCurrency(), payment.getRenewalProviderTransactionReference(),
                payment.getProcessedBy() == null ? null : payment.getProcessedBy().getId());
    }

    private Payment findPayment(RenewalSettlementEvidence evidence) {
        return evidence == null || evidence.paymentId() == null
                ? null : payments.findById(evidence.paymentId()).orElse(null);
    }

    private void requireNoCallerTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Renewal collection orchestration must start outside an existing transaction to preserve commit boundaries");
        }
    }

    static boolean isProviderReferenceUniqueViolation(Throwable failure) {
        if (failure == null) return false;
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof ConstraintViolationException constraint
                    && PROVIDER_REFERENCE_INDEX.equalsIgnoreCase(constraint.getConstraintName())) {
                return true;
            }
            if (current instanceof SQLException sql
                    && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)
                    && sql.getMessage() != null
                    && sql.getMessage().toLowerCase(java.util.Locale.ROOT)
                            .contains(PROVIDER_REFERENCE_INDEX.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            if (current.getCause() != null) pending.add(current.getCause());
            for (Throwable suppressed : current.getSuppressed()) pending.add(suppressed);
            if (current instanceof JDBCException jdbc && jdbc.getSQLException() != null) {
                pending.add(jdbc.getSQLException());
            }
            if (current instanceof TransactionSystemException transactionFailure) {
                if (transactionFailure.getOriginalException() != null) pending.add(transactionFailure.getOriginalException());
                if (transactionFailure.getApplicationException() != null) pending.add(transactionFailure.getApplicationException());
            }
            if (current instanceof SQLException sql && sql.getNextException() != null) {
                pending.add(sql.getNextException());
            }
        }
        return false;
    }
}
