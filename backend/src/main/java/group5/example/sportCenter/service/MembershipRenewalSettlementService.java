package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalPaidTimeEvidenceType;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

/**
 * Internal settlement engine. Only a future trusted payment adapter or internal test should call
 * this service; it is deliberately not exposed as a controller, callback, scheduler, or counter flow.
 */
@Service
public class MembershipRenewalSettlementService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository users;
    private final MembershipRenewalIntentRepository intents;
    private final PaymentRepository payments;
    private final MembershipRepository memberships;
    private final Clock clock;
    private final NotificationService notifications;

    private final EntityManager entityManager;

    public MembershipRenewalSettlementService(UserRepository users,
            MembershipRenewalIntentRepository intents, PaymentRepository payments,
            MembershipRepository memberships, Clock clock, EntityManager entityManager,
            NotificationService notifications) {
        this.users = users;
        this.intents = intents;
        this.payments = payments;
        this.memberships = memberships;
        this.clock = clock;
        this.notifications = notifications;
        this.entityManager = entityManager;
    }

    /** Applies verified evidence atomically. A provider callback arrival time is never used as paidAt. */
    @Transactional
    public RenewalSettlementResult settle(RenewalSettlementEvidence evidence) {
        if (evidence == null || evidence.paymentId() == null || evidence.intentId() == null) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, null,
                    "Renewal Intent and Payment identities are required");
        }

        PendingAutoRenewalLockRow metadata = payments.findPaymentLockRow(evidence.paymentId()).orElse(null);
        if (metadata == null) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.CONFLICT, null, "Payment not found");
        }
        Payment candidate = payments.findById(evidence.paymentId()).orElse(null);
        if (candidate == null || candidate.getRenewalIntent() == null) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.LEGACY_PAYMENT_REJECTED, candidate,
                    "Legacy Renewal Payment cannot settle through the Intent engine");
        }
        Integer relationIntentId = candidate.getRenewalIntent().getId();

        // Stable serialization order shared with Renewal attempt, Purchase and Upgrade creation.
        users.findByIdForBookingUpdate(metadata.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = intents.findByIdForUpdate(relationIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
        Payment payment = payments.findByIdForUpdate(evidence.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        // The initial unlocked read is only used to discover relational lock keys. If another
        // settlement committed while this transaction waited for Member/Intent, JPA may return
        // that pre-lock managed instance here. Refresh under the acquired Payment lock so the
        // winner/idempotency checks observe the committed settlement state.
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        if (!evidence.intentId().equals(relationIntentId)) {
            return verifiedCollected(evidence) && hasTrustedProvenance(evidence)
                    ? reconciliation(payment, evidence,
                            "Payment is not relationally linked to the specified Renewal Intent")
                    : RenewalSettlementResult.of(RenewalSettlementOutcome.CONFLICT, payment,
                            "Payment is not relationally linked to the specified Renewal Intent");
        }
        if (payment.getRenewalIntent() == null || !intent.getId().equals(payment.getRenewalIntent().getId())) {
            return verifiedCollected(evidence) && hasTrustedProvenance(evidence)
                    ? reconciliation(payment, evidence, "Payment/Intent relationship changed or is inconsistent")
                    : RenewalSettlementResult.of(RenewalSettlementOutcome.CONFLICT, payment,
                            "Payment/Intent relationship changed or is inconsistent");
        }

        if (isAcceptedWinner(intent, payment)) {
            Membership successor = memberships.findByRenewalIntent_Id(intent.getId()).orElse(null);
            if (successor == null) {
                return RenewalSettlementResult.of(RenewalSettlementOutcome.CONFLICT, payment,
                        "Accepted settlement exists without its successor Membership");
            }
            notifySettled(intent);
            return new RenewalSettlementResult(RenewalSettlementOutcome.ALREADY_ACCEPTED,
                    payment, successor, "This Payment already won settlement");
        }
        if (payment.getRenewalCollectionConflictDetectedAt() != null) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, payment,
                    "Conflicting collections are recorded for this Payment");
        }
        if (intent.getIntentStatus() == RenewalIntentStatus.SETTLED
                || payments.existsByRenewalIntent_IdAndRenewalSettlementAcceptedAtIsNotNull(intent.getId())) {
            if (verifiedCollected(evidence) && hasTrustedProvenance(evidence)) {
                payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.DUPLICATE_SETTLEMENT);
            }
            return reconciliation(payment, evidence, "A different attempt already settled this Intent");
        }

        Membership sourceRef = intent.getSourceMembership();
        if (sourceRef == null || sourceRef.getId() == null) {
            return reconciliation(payment, evidence, "Renewal Intent has no source Membership");
        }
        Membership source = memberships.findByIdForRenewalUpdate(sourceRef.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Source Membership not found"));

        if (!evidenceVerified(evidence)) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment,
                    evidence.validationIssue() == null ? "Settlement evidence is not verified" : evidence.validationIssue());
        }
        if (evidence.collectionOutcome() == null
                || evidence.collectionOutcome() == RenewalSettlementEvidence.CollectionOutcome.UNKNOWN) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment,
                    "Collection outcome is unknown");
        }
        if (!hasTrustedProvenance(evidence)) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment,
                    "Verified evidence requires provenance and a reference");
        }
        if (evidence.method() == null || evidence.method() != payment.getMethod()) {
            return evidence.collectionOutcome() == RenewalSettlementEvidence.CollectionOutcome.COLLECTED
                    ? reconciliation(payment, evidence, "Verified payment method does not match the attempt")
                    : RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment,
                            "Verified payment method does not match the attempt");
        }
        if (payment.hasRecordedRenewalCollection()) {
            boolean amountMatches = payment.getRenewalVerifiedCollectionAmount() != null
                    && payment.getAmount() != null
                    && payment.getRenewalVerifiedCollectionAmount().compareTo(payment.getAmount()) == 0
                    && evidence.verifiedCollectionAmount() != null
                    && payment.getRenewalVerifiedCollectionAmount().compareTo(evidence.verifiedCollectionAmount()) == 0;
            boolean currencyMatches = "VND".equals(payment.getRenewalVerifiedCollectionCurrency())
                    && "VND".equals(evidence.verifiedCollectionCurrency());
            boolean providerReferenceMatches = payment.getMethod() == PaymentMethod.COUNTER
                    || (payment.getRenewalProviderTransactionReference() != null
                            && payment.getRenewalProviderTransactionReference().matches("[0-9]+")
                            && payment.getRenewalProviderTransactionReference()
                                    .equals(evidence.providerTransactionReference()));
            boolean timeEvidenceMatches = payment.getRenewalPaidTimeEvidenceType() == toPaidTimeEvidenceType(
                    evidence.paidAtEvidence());
            boolean counterActorMatches = payment.getMethod() != PaymentMethod.COUNTER
                    || (payment.getProcessedBy() != null && evidence.counterActorId() != null
                            && payment.getProcessedBy().getId().equals(evidence.counterActorId()));
            if (!amountMatches || !currencyMatches || !providerReferenceMatches || !timeEvidenceMatches
                    || !counterActorMatches || !equalNullable(payment.getPaidAt(), evidence.verifiedPaidAt())) {
                return reconciliation(payment, evidence,
                        "Settlement evidence does not match the durably recorded first collection");
            }
        }
        if (evidence.collectionOutcome() == RenewalSettlementEvidence.CollectionOutcome.NOT_COLLECTED) {
            if (payment.getStatus() == PaymentStatus.PENDING && payment.getPaidAt() == null) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.PROVIDER_FAILED);
                payments.saveAndFlush(payment);
            }
            return RenewalSettlementResult.of(RenewalSettlementOutcome.PAYMENT_NOT_COLLECTED, payment,
                    "Verified evidence reports no collection");
        }
        // Once collection is verified, every rejection below preserves that evidence for reconciliation.
        if (evidence.verifiedPaidAt() == null
                || evidence.paidAtEvidence() == RenewalSettlementEvidence.PaidAtEvidence.MISSING
                || evidence.paidAtEvidence() == RenewalSettlementEvidence.PaidAtEvidence.MALFORMED) {
            return reconciliation(payment, evidence, "Verified collection has no usable paidAt timestamp");
        }
        if ((payment.getMethod() == PaymentMethod.ONLINE
                    && evidence.paidAtEvidence() != RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME)
                || (payment.getMethod() == PaymentMethod.COUNTER
                    && evidence.paidAtEvidence() != RenewalSettlementEvidence.PaidAtEvidence.SERVER_CONFIRMATION_TIME)) {
            return reconciliation(payment, evidence, "Paid-time provenance is not valid for the attempt method");
        }
        if (payment.getPaidAt() != null && !payment.getPaidAt().equals(evidence.verifiedPaidAt())) {
            return reconciliation(payment, evidence, "Stored and newly verified paidAt evidence conflict");
        }
        if (payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || payment.getAmount() == null || intent.getQuotedAmount() == null
                || payment.getAmount().compareTo(intent.getQuotedAmount()) != 0
                || intent.getQuotedAmount().signum() <= 0
                || intent.getPlanPriceSnapshot() == null
                || intent.getQuotedAmount().compareTo(intent.getPlanPriceSnapshot()) != 0
                || payment.getReferenceId() == null || intent.getTargetPlan() == null
                || !payment.getReferenceId().equals(intent.getTargetPlan().getId())) {
            return reconciliation(payment, evidence, "Payment does not match the frozen Renewal quote");
        }
        if (payment.getAttemptValidUntil() == null || payment.getCreatedAt() == null
                || !payment.getAttemptValidUntil().isAfter(payment.getCreatedAt())
                || intent.getValidUntil() == null || intent.getValidFrom() == null
                || !intent.getValidUntil().isAfter(intent.getValidFrom())) {
            return reconciliation(payment, evidence, "Payment or Intent deadline is missing or invalid");
        }
        LocalDateTime expectedAttemptDeadline = payment.getMethod() == PaymentMethod.COUNTER
                ? intent.getValidUntil()
                : payment.getCreatedAt().plusMinutes(15).isBefore(intent.getValidUntil())
                        ? payment.getCreatedAt().plusMinutes(15) : intent.getValidUntil();
        if (payment.getCreatedAt().isBefore(intent.getValidFrom())
                || !payment.getCreatedAt().isBefore(intent.getValidUntil())
                || !expectedAttemptDeadline.equals(payment.getAttemptValidUntil())) {
            return reconciliation(payment, evidence, "Attempt deadline does not match the frozen Renewal policy");
        }
        if (evidence.verifiedPaidAt().isBefore(payment.getCreatedAt())) {
            return reconciliation(payment, evidence, "Verified paidAt precedes the Payment attempt");
        }
        if (intent.getIntentStatus() != RenewalIntentStatus.OPEN) {
            if (payment.getRenewalAttemptCloseReason() == null) {
                payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.INTENT_EXPIRED);
            }
            return reconciliation(payment, evidence, "Renewal Intent was closed before settlement",
                    RenewalSettlementOutcome.INTENT_CLOSED);
        }
        if (payment.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.MEMBER_CANCELLED) {
            return reconciliation(payment, evidence, "Member cancellation was recorded before settlement",
                    RenewalSettlementOutcome.ATTEMPT_CANCELLED);
        }
        if (!evidence.verifiedPaidAt().isBefore(payment.getAttemptValidUntil())
                || !evidence.verifiedPaidAt().isBefore(intent.getValidUntil())) {
            if (payment.getRenewalAttemptCloseReason() == null) {
                payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.LATE_PROVIDER_PAYMENT);
            }
            return reconciliation(payment, evidence, "Verified collection occurred at or after its exclusive deadline",
                    RenewalSettlementOutcome.PAYMENT_LATE);
        }
        if (payment.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.INTENT_EXPIRED
                || payment.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.LATE_PROVIDER_PAYMENT
                || payment.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.DUPLICATE_SETTLEMENT) {
            return reconciliation(payment, evidence, "Attempt has a disqualifying terminal close reason");
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                && !(payment.getStatus() == PaymentStatus.CANCELLED
                    && payment.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.ATTEMPT_EXPIRED)) {
            return reconciliation(payment, evidence, "Attempt status is not settleable");
        }
        if (!Boolean.TRUE.equals(intent.getAutoRenewOptInSnapshot())
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !source.getId().equals(intent.getSourceMembership().getId())
                || !metadata.memberId().equals(source.getMember().getId())
                || !metadata.memberId().equals(payment.getMember().getId())
                || source.getPlan() == null || intent.getTargetPlan() == null
                || !source.getPlan().getId().equals(intent.getTargetPlan().getId())
                || source.getStartDate() == null || source.getEndDate() == null
                || !source.getEndDate().isAfter(source.getStartDate())
                || !source.getEndDate().equals(intent.getSourceEndDateSnapshot())
                || !source.getEndDate().equals(intent.getSuccessorStartDate())
                || intent.getDurationMonthsSnapshot() == null || intent.getDurationMonthsSnapshot() <= 0
                || intent.getPlanNameSnapshot() == null || intent.getPlanPriceSnapshot() == null
                || intent.getCourseDiscountPercentSnapshot() == null
                || intent.getPtDiscountPercentSnapshot() == null
                || intent.getAllowPtPlanSnapshot() == null || intent.getAllowOneOnOneSnapshot() == null
                || !source.getEndDate().plusMonths(intent.getDurationMonthsSnapshot())
                        .equals(intent.getSuccessorEndDate())
                || !("ACTIVE".equalsIgnoreCase(source.getStatus()) || "EXPIRED".equalsIgnoreCase(source.getStatus()))) {
            return reconciliation(payment, evidence, "Source period or frozen successor terms are inconsistent");
        }
        if (intent.getSettledPayment() != null || memberships.findByRenewalIntent_Id(intent.getId()).isPresent()) {
            return reconciliation(payment, evidence, "A successor or accepted settlement already exists");
        }
        if (payments.existsConflictingPendingMembershipOrUpgrade(metadata.memberId(), payment.getId())) {
            return reconciliation(payment, evidence, "A conflicting Purchase or Upgrade Payment is pending");
        }
        if (hasDifferentActivePeriod(metadata.memberId(), source)) {
            return reconciliation(payment, evidence, "Another active/future Membership conflicts with this successor");
        }

        LocalDateTime paidAt = evidence.verifiedPaidAt();
        LocalDateTime acceptedAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        persistEvidence(payment, evidence, paidAt);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRenewalAttemptCloseReason(null);
        payments.saveAndFlush(payment);

        intent.setIntentStatus(RenewalIntentStatus.SETTLED);
        intent.setSettledPayment(payment);
        intent.setSettledAt(acceptedAt);
        intents.saveAndFlush(intent);

        // This is the schema's accepted-winner marker; it is written only after Intent settlement exists.
        payment.setRenewalSettlementAcceptedAt(acceptedAt);
        payments.saveAndFlush(payment);

        Membership successor = createSuccessor(source, intent, payment);
        Membership saved = memberships.saveAndFlush(successor);
        notifySettled(intent);
        return new RenewalSettlementResult(RenewalSettlementOutcome.ACCEPTED, payment, saved,
                "Verified Renewal Payment accepted");
    }

    private boolean hasDifferentActivePeriod(Integer memberId, Membership source) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return memberships.findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                        memberId, "ACTIVE", today, today).stream().anyMatch(m -> !m.getId().equals(source.getId()))
                || memberships.existsByMemberIdAndStatusAndStartDateGreaterThan(memberId, "ACTIVE", today);
    }

    private Membership createSuccessor(Membership source, MembershipRenewalIntent intent, Payment payment) {
        Membership result = new Membership();
        result.setMember(source.getMember());
        result.setPlan(intent.getTargetPlan());
        result.setAutoRenew(Boolean.TRUE.equals(intent.getAutoRenewOptInSnapshot()));
        result.setStatus("ACTIVE"); // Existing periods use ACTIVE; date bounds determine whether it is effective.
        result.setStartDate(intent.getSuccessorStartDate());
        result.setEndDate(intent.getSuccessorEndDate());
        result.setPurchasePayment(payment);
        result.setRenewalIntent(intent);
        result.setBenefitSnapshotStatus("COMPLETE");
        result.setBenefitSnapshotSource("RENEWAL");
        result.setPlanNameSnapshot(intent.getPlanNameSnapshot());
        result.setPlanDescriptionSnapshot(intent.getPlanDescriptionSnapshot());
        result.setPlanPriceSnapshot(intent.getPlanPriceSnapshot());
        result.setPlanDurationMonthsSnapshot(intent.getDurationMonthsSnapshot());
        result.setCourseDiscountPercentSnapshot(intent.getCourseDiscountPercentSnapshot());
        result.setPtDiscountPercentSnapshot(intent.getPtDiscountPercentSnapshot());
        result.setAllowPtPlanSnapshot(intent.getAllowPtPlanSnapshot());
        result.setAllowOneOnOneSnapshot(intent.getAllowOneOnOneSnapshot());
        result.setPaidValueBasisStatus("VERIFIED");
        result.setPaidValueBasisAmount(payment.getAmount());
        result.setPaidValueBasisSource("RENEWAL_PAYMENT");
        return result;
    }

    private boolean isAcceptedWinner(MembershipRenewalIntent intent, Payment payment) {
        return intent.getIntentStatus() == RenewalIntentStatus.SETTLED
                && intent.getSettledPayment() != null
                && intent.getSettledPayment().getId().equals(payment.getId())
                && payment.getRenewalSettlementAcceptedAt() != null
                && payment.getStatus() == PaymentStatus.SUCCESS;
    }

    private boolean evidenceVerified(RenewalSettlementEvidence evidence) {
        return evidence.verified() && evidence.collectionOutcome() != null;
    }

    private boolean verifiedCollected(RenewalSettlementEvidence evidence) {
        return evidenceVerified(evidence)
                && evidence.collectionOutcome() == RenewalSettlementEvidence.CollectionOutcome.COLLECTED;
    }

    private boolean hasTrustedProvenance(RenewalSettlementEvidence evidence) {
        return evidence.method() != null && evidence.verificationProvenance() != null
                && !evidence.verificationProvenance().isBlank()
                && evidence.verificationReference() != null && !evidence.verificationReference().isBlank();
    }

    private RenewalSettlementResult reconciliation(Payment payment, RenewalSettlementEvidence evidence, String detail) {
        return reconciliation(payment, evidence, detail, RenewalSettlementOutcome.RECONCILIATION_REQUIRED);
    }

    private RenewalSettlementResult reconciliation(Payment payment, RenewalSettlementEvidence evidence,
            String detail, RenewalSettlementOutcome outcome) {
        if (payment == null || evidence == null || !evidence.verified()
                || evidence.collectionOutcome() != RenewalSettlementEvidence.CollectionOutcome.COLLECTED) {
            return RenewalSettlementResult.of(RenewalSettlementOutcome.INVALID_EVIDENCE, payment, detail);
        }
        persistEvidence(payment, evidence, evidence.verifiedPaidAt());
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        }
        payments.saveAndFlush(payment);
        if (payment.getRenewalIntent() != null) {
            notifications.renewalReconciliationRequired(payment.getId(), payment.getMember().getId());
        }
        return RenewalSettlementResult.of(outcome, payment, detail);
    }

    private void notifySettled(MembershipRenewalIntent intent) {
        Integer memberId = intent.getSourceMembership().getMember().getId();
        notifications.createForUserOnce(memberId, "Renewal completed",
                "Renewal Intent #" + intent.getId() + " was paid successfully. The successor Membership starts on "
                        + intent.getSuccessorStartDate() + ".");
    }

    private void persistEvidence(Payment payment, RenewalSettlementEvidence evidence, LocalDateTime paidAt) {
        if (payment.hasRecordedRenewalCollection()) return;
        if (paidAt != null && payment.getPaidAt() == null) payment.setPaidAt(paidAt);
        payment.setRenewalProviderTransactionReference(evidence.providerTransactionReference());
        payment.setRenewalVerifiedCollectionAmount(evidence.verifiedCollectionAmount());
        payment.setRenewalVerifiedCollectionCurrency(evidence.verifiedCollectionCurrency());
        payment.setRenewalPaidTimeEvidenceType(toPaidTimeEvidenceType(evidence.paidAtEvidence()));
        payment.setRenewalCollectionVerifiedAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
    }

    private RenewalPaidTimeEvidenceType toPaidTimeEvidenceType(RenewalSettlementEvidence.PaidAtEvidence evidence) {
        if (evidence == null) return null;
        return switch (evidence) {
            case VERIFIED_PROVIDER_TIME -> RenewalPaidTimeEvidenceType.PROVIDER_VERIFIED;
            case SERVER_CONFIRMATION_TIME -> RenewalPaidTimeEvidenceType.SERVER_CONFIRMATION;
            case MISSING -> RenewalPaidTimeEvidenceType.MISSING;
            case MALFORMED -> RenewalPaidTimeEvidenceType.MALFORMED;
            case NOT_APPLICABLE -> null;
        };
    }

    private boolean equalNullable(LocalDateTime left, LocalDateTime right) {
        return left == null ? right == null : left.equals(right);
    }
}
