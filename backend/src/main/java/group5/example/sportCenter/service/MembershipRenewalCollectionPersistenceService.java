package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalPaidTimeEvidenceType;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Commits one trusted Renewal collection fact before the independent settlement transaction. */
@Service
public class MembershipRenewalCollectionPersistenceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserRepository users;
    private final MembershipRenewalIntentRepository intents;
    private final PaymentRepository payments;
    private final Clock clock;

    public MembershipRenewalCollectionPersistenceService(UserRepository users,
            MembershipRenewalIntentRepository intents, PaymentRepository payments, Clock clock) {
        this.users = users;
        this.intents = intents;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewalCollectionCaptureResult recordFirstCollection(RenewalSettlementEvidence evidence) {
        if (evidence == null || !evidence.verified()
                || evidence.collectionOutcome() != RenewalSettlementEvidence.CollectionOutcome.COLLECTED) {
            return RenewalCollectionCaptureResult.of(
                    evidence != null && evidence.verified()
                            ? RenewalCollectionCaptureResult.Status.NOT_COLLECTED
                            : RenewalCollectionCaptureResult.Status.INVALID,
                    null, "Only a verified collected Renewal Payment can be persisted");
        }
        if (evidence.paymentId() == null || evidence.intentId() == null || evidence.method() == null
                || evidence.verificationProvenance() == null || evidence.verificationProvenance().isBlank()) {
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.INVALID,
                    null, "Verified collection identity and provenance are required");
        }

        Payment candidate = payments.findById(evidence.paymentId()).orElse(null);
        if (candidate == null || candidate.getRenewalIntent() == null
                || candidate.getPurpose() != PaymentPurpose.MEMBERSHIP) {
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.INVALID,
                    null, "Collection is not linked to a Renewal Membership Payment");
        }
        Integer memberId = candidate.getMember().getId();
        Integer relationalIntentId = candidate.getRenewalIntent().getId();

        // Every writer follows Member -> Intent -> Payment, including this independent commit.
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("Renewal Payment member no longer exists"));
        var intent = intents.findByIdForUpdate(relationalIntentId)
                .orElseThrow(() -> new IllegalStateException("Renewal Intent no longer exists"));
        Payment payment = payments.findByIdForUpdate(evidence.paymentId())
                .orElseThrow(() -> new IllegalStateException("Renewal Payment no longer exists"));

        if (payment.getRenewalIntent() == null || !intent.getId().equals(payment.getRenewalIntent().getId())) {
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.INVALID,
                    null, "Payment's relational Renewal Intent is inconsistent");
        }
        if (payment.getMethod() != evidence.method()) {
            markConflict(payment);
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                    null, "Collected evidence method conflicts with the Payment attempt; manual handling is required");
        }

        User counterActor = null;
        if (payment.getMethod() == PaymentMethod.COUNTER) {
            if (evidence.counterActorId() == null) {
                return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.INVALID,
                        null, "COUNTER collection requires an authenticated staff identity");
            }
            counterActor = users.findById(evidence.counterActorId()).orElse(null);
            if (counterActor == null || !"ACTIVE".equalsIgnoreCase(counterActor.getStatus())
                    || !("ADMIN".equalsIgnoreCase(counterActor.getRole())
                            || "RECEPTIONIST".equalsIgnoreCase(counterActor.getRole()))) {
                return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.INVALID,
                        null, "COUNTER collection actor is not an active authorized staff member");
            }
        }

        RenewalSettlementEvidence normalized = normalizeCounterTime(evidence, payment);
        if (!evidence.intentId().equals(relationalIntentId)) {
            if (!payment.hasRecordedRenewalCollection()) {
                persistFirstFacts(payment, normalized, counterActor, false);
            }
            markConflict(payment);
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                    evidenceFromPayment(payment, normalized),
                    "Collection was reported against a different Intent identity; manual handling is required");
        }
        if (payment.hasRecordedRenewalCollection()) {
            if (payment.getRenewalCollectionConflictDetectedAt() != null) {
                return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                        normalized, "A prior collection conflict requires manual handling");
            }
            if (sameCollection(payment, normalized, counterActor)) {
                return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.IDEMPOTENT,
                        evidenceFromPayment(payment, normalized), "The same collection was already recorded");
            }
            markConflict(payment);
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                    normalized, "A different collection was reported for this Payment; first facts were preserved");
        }

        if (payment.getMethod() == PaymentMethod.ONLINE
                && normalized.providerTransactionReference() != null
                && payments.existsByRenewalProviderReferenceOnOtherPayment(
                        normalized.providerTransactionReference(), payment.getId())) {
            markConflict(payment);
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                    normalized, "Provider transaction reference is already attached to another Renewal Payment");
        }

        if (payment.getPaidAt() != null && !payment.getPaidAt().equals(normalized.verifiedPaidAt())) {
            persistFirstFacts(payment, normalized, counterActor, false);
            markConflict(payment);
            return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.CONFLICT,
                    normalized, "Existing Payment time evidence conflicts with the first Renewal collection");
        }

        persistFirstFacts(payment, normalized, counterActor, true);
        payments.saveAndFlush(payment);

        return RenewalCollectionCaptureResult.of(RenewalCollectionCaptureResult.Status.RECORDED,
                evidenceFromPayment(payment, normalized), "First verified collection was committed");
    }

    /** Recovery for a concurrent provider-reference unique-index loser, in a fresh lock-ordered transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markConcurrentProviderReferenceConflict(Integer paymentId) {
        Payment candidate = payments.findById(paymentId).orElse(null);
        if (candidate == null || candidate.getRenewalIntent() == null) return;
        Integer memberId = candidate.getMember().getId();
        Integer intentId = candidate.getRenewalIntent().getId();
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("Renewal Payment member no longer exists"));
        intents.findByIdForUpdate(intentId)
                .orElseThrow(() -> new IllegalStateException("Renewal Intent no longer exists"));
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Renewal Payment no longer exists"));
        markConflict(payment);
    }

    private void persistFirstFacts(Payment payment, RenewalSettlementEvidence evidence, User counterActor,
            boolean setPaidAt) {
        payment.setRenewalProviderTransactionReference(evidence.providerTransactionReference());
        payment.setRenewalVerifiedCollectionAmount(evidence.verifiedCollectionAmount());
        payment.setRenewalVerifiedCollectionCurrency(evidence.verifiedCollectionCurrency());
        payment.setRenewalPaidTimeEvidenceType(toEntityType(evidence.paidAtEvidence()));
        payment.setRenewalCollectionVerifiedAt(now());
        if (setPaidAt && evidence.verifiedPaidAt() != null) payment.setPaidAt(evidence.verifiedPaidAt());
        if (counterActor != null) payment.setProcessedBy(counterActor);
    }

    private RenewalSettlementEvidence normalizeCounterTime(RenewalSettlementEvidence evidence, Payment payment) {
        if (payment.getMethod() != PaymentMethod.COUNTER) return evidence;
        if (payment.hasRecordedRenewalCollection()) {
            return evidenceFromPayment(payment, evidence);
        }
        return new RenewalSettlementEvidence(evidence.intentId(), evidence.paymentId(), evidence.method(),
                evidence.collectionOutcome(), evidence.verified(), now(),
                RenewalSettlementEvidence.PaidAtEvidence.SERVER_CONFIRMATION_TIME,
                evidence.verificationProvenance(), evidence.verificationReference(), evidence.validationIssue(),
                evidence.verifiedCollectionAmount(), evidence.verifiedCollectionCurrency(), null,
                evidence.counterActorId());
    }

    private boolean sameCollection(Payment payment, RenewalSettlementEvidence evidence, User counterActor) {
        boolean coreFactsMatch = equalMoney(payment.getRenewalVerifiedCollectionAmount(), evidence.verifiedCollectionAmount())
                && equalText(payment.getRenewalVerifiedCollectionCurrency(), evidence.verifiedCollectionCurrency())
                && payment.getRenewalPaidTimeEvidenceType() == toEntityType(evidence.paidAtEvidence());
        if (!coreFactsMatch) return false;
        if (payment.getMethod() == PaymentMethod.COUNTER) {
            // A repeat staff action for the same Payment is idempotent. The first actor remains in processedBy;
            // this six-column model cannot distinguish a second physical cash collection without a receipt ID.
            return counterActor != null && payment.getProcessedBy() != null;
        }
        return equalText(payment.getRenewalProviderTransactionReference(), evidence.providerTransactionReference())
                && equalTime(payment.getPaidAt(), evidence.verifiedPaidAt());
    }

    private void markConflict(Payment payment) {
        if (payment.getRenewalCollectionConflictDetectedAt() == null) {
            payment.setRenewalCollectionConflictDetectedAt(now());
        }
        if (payment.getRenewalSettlementAcceptedAt() == null && payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        }
        payments.saveAndFlush(payment);
    }

    private LocalDateTime now() {
        // Payment.paidAt is datetime2(6). Normalize before both persisting the first
        // COUNTER collection and returning its evidence so settlement compares the
        // exact value that SQL Server can durably represent.
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE)).truncatedTo(ChronoUnit.MICROS);
    }

    private RenewalSettlementEvidence evidenceFromPayment(Payment payment, RenewalSettlementEvidence source) {
        RenewalSettlementEvidence.PaidAtEvidence paidAtEvidence = fromEntityType(payment.getRenewalPaidTimeEvidenceType());
        String provenance = payment.getMethod() == PaymentMethod.COUNTER
                ? "COUNTER_SERVER_CONFIRMATION" : "VNPAY_SIGNATURE_VERIFIED";
        String reference = payment.getRenewalProviderTransactionReference() != null
                ? payment.getRenewalProviderTransactionReference() : payment.getTransactionCode();
        return new RenewalSettlementEvidence(payment.getRenewalIntent().getId(), payment.getId(), payment.getMethod(),
                RenewalSettlementEvidence.CollectionOutcome.COLLECTED, true, payment.getPaidAt(), paidAtEvidence,
                provenance, reference, null, payment.getRenewalVerifiedCollectionAmount(),
                payment.getRenewalVerifiedCollectionCurrency(), payment.getRenewalProviderTransactionReference(),
                payment.getMethod() == PaymentMethod.COUNTER && payment.getProcessedBy() != null
                        ? payment.getProcessedBy().getId() : source.counterActorId());
    }

    private RenewalPaidTimeEvidenceType toEntityType(RenewalSettlementEvidence.PaidAtEvidence evidence) {
        if (evidence == null) return null;
        return switch (evidence) {
            case VERIFIED_PROVIDER_TIME -> RenewalPaidTimeEvidenceType.PROVIDER_VERIFIED;
            case SERVER_CONFIRMATION_TIME -> RenewalPaidTimeEvidenceType.SERVER_CONFIRMATION;
            case MISSING -> RenewalPaidTimeEvidenceType.MISSING;
            case MALFORMED -> RenewalPaidTimeEvidenceType.MALFORMED;
            case NOT_APPLICABLE -> null;
        };
    }

    private RenewalSettlementEvidence.PaidAtEvidence fromEntityType(RenewalPaidTimeEvidenceType evidence) {
        if (evidence == null) return RenewalSettlementEvidence.PaidAtEvidence.MALFORMED;
        return switch (evidence) {
            case PROVIDER_VERIFIED -> RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME;
            case SERVER_CONFIRMATION -> RenewalSettlementEvidence.PaidAtEvidence.SERVER_CONFIRMATION_TIME;
            case MISSING -> RenewalSettlementEvidence.PaidAtEvidence.MISSING;
            case MALFORMED -> RenewalSettlementEvidence.PaidAtEvidence.MALFORMED;
        };
    }

    private boolean equalMoney(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean equalText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean equalTime(LocalDateTime left, LocalDateTime right) {
        return left == null ? right == null : left.equals(right);
    }
}
