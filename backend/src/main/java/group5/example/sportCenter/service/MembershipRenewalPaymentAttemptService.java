package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.exception.ResourceNotFoundException;

/** Internal Renewal Payment attempt lifecycle. It does not call a provider or settle Memberships. */
@Service
public class MembershipRenewalPaymentAttemptService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.time.Duration ONLINE_VALIDITY = java.time.Duration.ofMinutes(15);

    private final MembershipRenewalIntentRepository intents;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final Clock clock;

    public MembershipRenewalPaymentAttemptService(
            MembershipRenewalIntentRepository intents,
            PaymentRepository payments,
            UserRepository users,
            Clock clock) {
        this.intents = intents;
        this.payments = payments;
        this.users = users;
        this.clock = clock;
    }

    /** Creates an attempt from the immutable Intent quote; no checkout or collection is initiated. */
    @Transactional
    public Payment createAttempt(Integer intentId, PaymentMethod method) {
        if (method != PaymentMethod.ONLINE && method != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Renewal payment method");
        }
        MembershipRenewalIntent initial = intents.findById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
        Integer memberId = initial.getSourceMembership().getMember().getId();
        var member = users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Renewal payment requires an ACTIVE MEMBER account");
        }

        // Global order: Member -> Intent -> Payment rows. Purchase/Upgrade use the same Member lock.
        MembershipRenewalIntent intent = intents.findByIdForUpdate(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
        LocalDateTime createdAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        validateOpenIntent(intent, memberId, createdAt);

        List<Payment> existingAttempts = payments.findByRenewalIntentIdForUpdate(intentId);
        for (Payment existing : existingAttempts) {
            if (hasCollectedOrAcceptedEvidence(existing)
                    || existing.getPaidAt() != null
                    || existing.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.INTENT_EXPIRED
                    || existing.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.LATE_PROVIDER_PAYMENT
                    || existing.getRenewalAttemptCloseReason() == RenewalAttemptCloseReason.DUPLICATE_SETTLEMENT) {
                throw conflict("A Renewal payment has collection or settlement evidence requiring reconciliation");
            }
            if (existing.getStatus() == PaymentStatus.PENDING) {
                if (existing.getPaidAt() != null) {
                    throw conflict("A pending Renewal payment has provider payment evidence requiring reconciliation");
                }
                if (existing.getAttemptValidUntil() == null || createdAt.isBefore(existing.getAttemptValidUntil())) {
                    throw conflict("A live Renewal payment attempt already exists");
                }
                existing.setStatus(PaymentStatus.CANCELLED);
                existing.setRenewalAttemptCloseReason(createdAt.isBefore(intent.getValidUntil())
                        ? RenewalAttemptCloseReason.ATTEMPT_EXPIRED
                        : RenewalAttemptCloseReason.INTENT_EXPIRED);
            }
        }

        if (intent.getQuoteCapturedAt() == null || intent.getPlanNameSnapshot() == null
                || intent.getPlanPriceSnapshot() == null || intent.getCourseDiscountPercentSnapshot() == null
                || intent.getPtDiscountPercentSnapshot() == null || intent.getAllowPtPlanSnapshot() == null
                || intent.getAllowOneOnOneSnapshot() == null || intent.getQuotedAmount() == null
                || intent.getQuotedAmount().compareTo(intent.getPlanPriceSnapshot()) != 0
                || intent.getTargetPlan() == null
                || intent.getTargetPlan().getId() == null
                || intent.getDurationMonthsSnapshot() == null
                || intent.getDurationMonthsSnapshot() <= 0) {
            throw conflict("Renewal Intent quote is incomplete or inconsistent");
        }

        LocalDateTime deadline = method == PaymentMethod.COUNTER
                ? intent.getValidUntil()
                : createdAt.plus(ONLINE_VALIDITY).isBefore(intent.getValidUntil())
                        ? createdAt.plus(ONLINE_VALIDITY) : intent.getValidUntil();
        if (deadline == null || !createdAt.isBefore(deadline)) {
            throw conflict("Renewal Intent deadline has passed");
        }

        Payment attempt = new Payment();
        attempt.setMember(intent.getSourceMembership().getMember());
        attempt.setAmount(intent.getQuotedAmount());
        attempt.setMethod(method);
        attempt.setPurpose(PaymentPurpose.MEMBERSHIP);
        attempt.setReferenceId(intent.getTargetPlan().getId());
        attempt.setAutoRenew(null);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setCreatedAt(createdAt);
        attempt.setAttemptValidUntil(deadline);
        attempt.setRenewalIntent(intent);
        attempt.setTransactionCode("TXN-REN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        return payments.saveAndFlush(attempt);
    }

    /** Records a verified provider failure without changing the Intent or creating another attempt. */
    @Transactional
    public Payment recordProviderFailure(Integer paymentId) {
        Payment candidate = candidatePayment(paymentId);
        Integer memberId = candidate.getMember().getId();
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = lockIntent(candidate);
        Payment payment = lockPayment(paymentId);
        requireRenewalAttempt(payment, intent);
        if (payment.getStatus() != PaymentStatus.PENDING) return payment;

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (payment.getPaidAt() != null) {
            throw conflict("Provider payment evidence must be reconciled, not recorded as a failure");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(intent.getIntentStatus() == RenewalIntentStatus.CANCELLED
                ? RenewalAttemptCloseReason.MEMBER_CANCELLED
                : intent.getIntentStatus() == RenewalIntentStatus.EXPIRED || !now.isBefore(intent.getValidUntil())
                        ? RenewalAttemptCloseReason.INTENT_EXPIRED
                        : now.isBefore(payment.getAttemptValidUntil())
                                ? RenewalAttemptCloseReason.PROVIDER_FAILED
                                : RenewalAttemptCloseReason.ATTEMPT_EXPIRED);
        return payments.saveAndFlush(payment);
    }

    /** Records the Member's explicit cancellation under the common Member -> Intent -> Payment lock order. */
    @Transactional
    public Payment cancelAttempt(Integer paymentId, Integer actorId) {
        Payment candidate = candidatePayment(paymentId);
        Integer memberId = candidate.getMember().getId();
        User member = users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = lockIntent(candidate);
        Payment payment = lockPayment(paymentId);
        requireRenewalAttempt(payment, intent);

        User actor = users.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot cancel a Renewal attempt");
        }
        if (!"MEMBER".equalsIgnoreCase(actor.getRole()) || !member.getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the owning Member can cancel a Renewal attempt");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw conflict("Renewal payment attempt is no longer pending");
        }
        if (payment.hasRecordedRenewalCollection() || payment.getPaidAt() != null) {
            throw conflict("A Renewal attempt with collection evidence requires reconciliation, not cancellation");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
        return payments.saveAndFlush(payment);
    }

    /** Closes an elapsed pending attempt while retaining its provider timestamps and other evidence. */
    @Transactional
    public Payment expireAttempt(Integer paymentId) {
        Payment candidate = candidatePayment(paymentId);
        Integer memberId = candidate.getMember().getId();
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = lockIntent(candidate);
        Payment payment = lockPayment(paymentId);
        requireRenewalAttempt(payment, intent);
        if (payment.getStatus() != PaymentStatus.PENDING) return payment;

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (payment.getPaidAt() != null) {
            throw conflict("Provider payment evidence must be reconciled, not expired");
        }
        if (payment.getAttemptValidUntil() == null || now.isBefore(payment.getAttemptValidUntil())) {
            throw conflict("Renewal payment attempt deadline has not elapsed");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(intent.getIntentStatus() == RenewalIntentStatus.CANCELLED
                ? RenewalAttemptCloseReason.MEMBER_CANCELLED
                : intent.getIntentStatus() == RenewalIntentStatus.EXPIRED || !now.isBefore(intent.getValidUntil())
                        ? RenewalAttemptCloseReason.INTENT_EXPIRED
                        : RenewalAttemptCloseReason.ATTEMPT_EXPIRED);
        return payments.saveAndFlush(payment);
    }

    private void validateOpenIntent(MembershipRenewalIntent intent, Integer memberId, LocalDateTime now) {
        if (intent.getIntentStatus() != RenewalIntentStatus.OPEN) {
            throw conflict("Renewal Intent is not open");
        }
        if (intent.getSourceMembership() == null || intent.getSourceMembership().getMember() == null
                || !memberId.equals(intent.getSourceMembership().getMember().getId())) {
            throw conflict("Renewal Intent member ownership is invalid");
        }
        if (!"ACTIVE".equalsIgnoreCase(intent.getSourceMembership().getStatus())
                || !Boolean.TRUE.equals(intent.getSourceMembership().getAutoRenew())
                || !Boolean.TRUE.equals(intent.getAutoRenewOptInSnapshot())
                || intent.getSourceMembership().getStartDate() == null
                || intent.getSourceMembership().getStartDate().isAfter(now.toLocalDate())
                || intent.getSourceEndDateSnapshot() == null
                || !intent.getSourceEndDateSnapshot().equals(intent.getSourceMembership().getEndDate())) {
            throw conflict("Renewal source Membership is no longer eligible for an attempt");
        }
        if (intent.getValidFrom() == null || now.isBefore(intent.getValidFrom())
                || intent.getValidUntil() == null || !now.isBefore(intent.getValidUntil())) {
            throw conflict("Renewal Intent deadline has passed");
        }
    }

    private boolean hasCollectedOrAcceptedEvidence(Payment payment) {
        return payment.hasRecordedRenewalCollection()
                || payment.getRenewalCollectionConflictDetectedAt() != null
                || payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getRenewalSettlementAcceptedAt() != null;
    }

    private Payment candidatePayment(Integer paymentId) {
        PendingAutoRenewalLockRow row = payments.findPaymentLockRow(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Payment candidate = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (candidate.getRenewalIntent() == null || row.purpose() != PaymentPurpose.MEMBERSHIP) {
            throw conflict("Payment is not a Renewal attempt");
        }
        return candidate;
    }

    private MembershipRenewalIntent lockIntent(Payment payment) {
        return intents.findByIdForUpdate(payment.getRenewalIntent().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
    }

    private Payment lockPayment(Integer paymentId) {
        return payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private void requireRenewalAttempt(Payment payment, MembershipRenewalIntent intent) {
        if (!PaymentPurpose.MEMBERSHIP.equals(payment.getPurpose()) || payment.getRenewalIntent() == null
                || !intent.getId().equals(payment.getRenewalIntent().getId())) {
            throw conflict("Payment is not linked to this Renewal Intent");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
