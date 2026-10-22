package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentCheckoutAttempt;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Transactional half of renewal capture processing. Evidence and activation commit separately. */
@Service
public class RenewalSettlementTransactions {
    private static final String COUNTER_EVIDENCE_PREFIX = "Verified renewal COUNTER receipt; settlement pending; priorStatus=";

    public record RecordedOnlineCapture(Integer attemptId, Integer paymentId, Integer appliedMembershipId,
            boolean conflictingEvidence, String reconciliationReason) {}

    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCheckoutAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    public RenewalSettlementTransactions(MembershipRepository membershipRepository,
            PaymentRepository paymentRepository, PaymentCheckoutAttemptRepository attemptRepository,
            UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
    }

    /** Durable attempt evidence commits before any eligibility or membership mutation is attempted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecordedOnlineCapture recordVerifiedOnlineCapture(VerifiedRenewalCapture capture, LocalDateTime observedAt) {
        var keys = attemptRepository.findRenewalLockKeysByTransactionReference(capture.transactionReference())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Renewal checkout attempt was not found"));
        if (keys.getSourceMembershipId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout attempt is not linked to a renewal");
        }
        LockContext context = lock(keys.getMemberId(), keys.getSourceMembershipId(), keys.getPaymentId());
        PaymentCheckoutAttempt attempt = attemptRepository.findByTransactionReferenceForUpdate(capture.transactionReference())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Renewal checkout attempt was not found"));
        verifyAttemptContext(attempt, context.payment());

        boolean conflictingEvidence = false;
        if (attempt.getVerifiedAt() == null) {
            attempt.setVerifiedAt(observedAt);
            attempt.setProviderTransactionId(capture.providerTransactionId());
            attempt.setProviderAmount(capture.reportedAmount());
            attempt.setProviderResponseCode(capture.responseCode());
            attempt.setStatus(CheckoutAttemptStatus.CAPTURED);
        } else if (!sameEvidence(attempt, capture)) {
            conflictingEvidence = true;
            attempt.setStatus(CheckoutAttemptStatus.RECONCILIATION_REQUIRED);
            attempt.setReconciliationReason("Repeated verified callback reported conflicting provider evidence");
        }
        attemptRepository.saveAndFlush(attempt);
        return new RecordedOnlineCapture(attempt.getId(), context.payment().getId(),
                attempt.getAppliedMembership() == null ? null : attempt.getAppliedMembership().getId(),
                conflictingEvidence, attempt.getReconciliationReason());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewalSettlementResult applyOnlineCapture(Integer attemptId) {
        PaymentCheckoutAttempt snapshot = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout attempt was not found"));
        Payment snapshotPayment = snapshot.getPayment();
        LockContext context = lock(snapshotPayment.getMember().getId(),
                snapshotPayment.getRenewalMembership().getId(), snapshotPayment.getId());
        PaymentCheckoutAttempt attempt = attemptRepository.findByTransactionReferenceForUpdate(snapshot.getTransactionReference())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout attempt was not found"));
        verifyAttemptContext(attempt, context.payment());

        // The durable winning link is authoritative for replay idempotency. Keep any
        // later reconciliation flag intact, but do not let it make the applied
        // Membership eligible for a second application.
        if (attempt.getAppliedMembership() != null) {
            return new RenewalSettlementResult(RenewalSettlementResult.Outcome.ALREADY_APPLIED,
                    context.payment().getId(), attempt.getId(), attempt.getAppliedMembership().getId(), null);
        }
        if (attempt.getVerifiedAt() == null || attempt.getProviderAmount() == null) {
            return reconcile(attempt, context.payment(), "Verified capture evidence is incomplete");
        }
        if (attempt.getProvider() == null || !"VNPAY".equals(attempt.getProvider().name())) {
            return reconcile(attempt, context.payment(), "Capture attempt provider is not supported for online renewal settlement");
        }
        if (attempt.getStatus() == CheckoutAttemptStatus.RECONCILIATION_REQUIRED) {
            return reconcile(attempt, context.payment(), safeReason(attempt.getReconciliationReason(), "Attempt evidence requires reconciliation"));
        }
        if (context.payment().getStatus() == PaymentStatus.SUCCESS) {
            PaymentCheckoutAttempt winner = attemptRepository
                    .findFirstByPayment_IdAndAppliedMembershipIsNotNull(context.payment().getId()).orElse(null);
            if (winner != null && winner.getId().equals(attempt.getId())) {
                return new RenewalSettlementResult(RenewalSettlementResult.Outcome.ALREADY_APPLIED,
                        context.payment().getId(), attempt.getId(), winner.getAppliedMembership().getId(), null);
            }
            return reconcile(attempt, context.payment(), "A different checkout attempt already settled this renewal; additional capture requires reconciliation");
        }
        if (context.payment().getStatus() != PaymentStatus.PENDING) {
            return reconcile(attempt, context.payment(), "Verified capture belongs to a cancelled or terminal renewal request");
        }
        if (context.payment().getMethod() != PaymentMethod.ONLINE) {
            return reconcile(attempt, context.payment(), "Verified online capture does not match the selected payment method");
        }

        String invalid = validateRenewal(context, attempt.getVerifiedAt(), attempt.getProviderAmount(), attempt.getAmount());
        if (invalid != null) return reconcile(attempt, context.payment(), invalid);

        Membership created = createNextMembership(context, attempt.getAmount());
        context.payment().setStatus(PaymentStatus.SUCCESS);
        context.payment().setPaidAt(attempt.getVerifiedAt());
        context.payment().setNote("Renewal settled from checkout attempt " + attempt.getTransactionReference());
        attempt.setAppliedMembership(created);
        attempt.setStatus(CheckoutAttemptStatus.CAPTURED);
        membershipRepository.save(created);
        paymentRepository.save(context.payment());
        attemptRepository.saveAndFlush(attempt);
        return new RenewalSettlementResult(RenewalSettlementResult.Outcome.APPLIED,
                context.payment().getId(), attempt.getId(), created.getId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCounterReceipt(Integer paymentId, Integer staffUserId, LocalDateTime receivedAt) {
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        if (snapshot.getRenewalMembership() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not a renewal request");
        }
        LockContext context = lock(snapshot.getMember().getId(), snapshot.getRenewalMembership().getId(), paymentId);
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff user was not found"));
        if (!"ADMIN".equalsIgnoreCase(staff.getRole()) && !"RECEPTIONIST".equalsIgnoreCase(staff.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can confirm COUNTER receipts");
        }
        if (!"ACTIVE".equalsIgnoreCase(staff.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive staff cannot confirm COUNTER receipts");
        }
        Payment payment = context.payment();
        if (payment.getStatus() == PaymentStatus.SUCCESS) return;
        if (payment.getMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Renewal payment method must be COUNTER");
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.CANCELLED
                && payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Renewal payment is not eligible for receipt confirmation");
        }
        if (isCounterEvidence(payment)) return;
        PaymentStatus priorStatus = payment.getStatus();
        if (payment.getPaidAt() == null) payment.setPaidAt(receivedAt);
        payment.setProcessedBy(staff);
        payment.setNote(COUNTER_EVIDENCE_PREFIX + priorStatus.name() + "; staffUserId=" + staffUserId);
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewalSettlementResult applyCounterReceipt(Integer paymentId) {
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        if (snapshot.getRenewalMembership() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not a renewal request");
        }
        LockContext context = lock(snapshot.getMember().getId(), snapshot.getRenewalMembership().getId(), paymentId);
        Payment payment = context.payment();
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return new RenewalSettlementResult(RenewalSettlementResult.Outcome.ALREADY_APPLIED,
                    paymentId, null, membershipRepository.findSuccessfulPaidPeriods(payment.getMember().getId()).stream()
                    .filter(m -> m.getSourcePayment() != null && m.getSourcePayment().getId().equals(paymentId))
                    .map(Membership::getId).findFirst().orElse(null), null);
        }
        if (!isCounterEvidence(payment)) {
            return counterReconcile(payment, "No durable confirmed COUNTER receipt evidence exists");
        }
        if (!payment.getNote().contains("priorStatus=PENDING;")) {
            return counterReconcile(payment, "COUNTER funds were received for a cancelled or terminal renewal request");
        }
        String invalid = validateRenewal(context, payment.getPaidAt(), payment.getAmount(), payment.getAmount());
        if (invalid != null) return counterReconcile(payment, invalid);

        Membership created = createNextMembership(context, payment.getAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setNote("Renewal COUNTER receipt settled; next membership created");
        membershipRepository.save(created);
        paymentRepository.saveAndFlush(payment);
        return new RenewalSettlementResult(RenewalSettlementResult.Outcome.APPLIED,
                paymentId, null, created.getId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewalSettlementResult recordOnlineReconciliation(Integer attemptId, String reason) {
        PaymentCheckoutAttempt snapshot = attemptRepository.findById(attemptId).orElseThrow();
        Payment p = snapshot.getPayment();
        LockContext context = lock(p.getMember().getId(), p.getRenewalMembership().getId(), p.getId());
        PaymentCheckoutAttempt attempt = attemptRepository.findByTransactionReferenceForUpdate(snapshot.getTransactionReference()).orElseThrow();
        return reconcile(attempt, context.payment(), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewalSettlementResult recordCounterReconciliation(Integer paymentId, String reason) {
        Payment snapshot = paymentRepository.findById(paymentId).orElseThrow();
        LockContext context = lock(snapshot.getMember().getId(), snapshot.getRenewalMembership().getId(), paymentId);
        return counterReconcile(context.payment(), reason);
    }

    private LockContext lock(Integer memberId, Integer sourceId, Integer paymentId) {
        User member = userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member was not found"));
        Membership source = membershipRepository.findByIdForRenewalUpdate(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source membership was not found"));
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        // Refresh after acquiring the member serialization lock. This avoids applying
        // a stale pre-wait snapshot under databases whose test isolation retains it.
        entityManager.refresh(source, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        List<PaymentCheckoutAttempt> attempts = attemptRepository
                .findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(paymentId);
        attempts.forEach(attempt -> entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE));
        return new LockContext(member, source, payment, attempts);
    }

    private void verifyAttemptContext(PaymentCheckoutAttempt attempt, Payment payment) {
        if (attempt.getPayment() == null || !attempt.getPayment().getId().equals(payment.getId())
                || payment.getRenewalMembership() == null
                || payment.getPurpose() != PaymentPurpose.MEMBERSHIP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout attempt is not linked to a renewal payment");
        }
    }

    private boolean sameEvidence(PaymentCheckoutAttempt attempt, VerifiedRenewalCapture capture) {
        return equalsNullable(attempt.getProviderTransactionId(), capture.providerTransactionId())
                && decimalEquals(attempt.getProviderAmount(), capture.reportedAmount())
                && equalsNullable(attempt.getProviderResponseCode(), capture.responseCode());
    }

    private String validateRenewal(LockContext context, LocalDateTime receivedAt, BigDecimal reportedAmount, BigDecimal attemptAmount) {
        Membership source = context.source();
        Payment payment = context.payment();
        if (receivedAt == null || source.getEndDate() == null
                || !receivedAt.isBefore(source.getEndDate().atStartOfDay())) {
            return "Verified renewal capture was at or after the source membership's exclusive end date";
        }
        if (!source.getMember().getId().equals(context.member().getId())
                || !payment.getMember().getId().equals(context.member().getId())
                || payment.getRenewalMembership() == null
                || !payment.getRenewalMembership().getId().equals(source.getId())
                || payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || !payment.getReferenceId().equals(source.getPlan().getId())) {
            return "Renewal payment ownership, source, purpose, or plan linkage is inconsistent";
        }
        if (payment.getPurchasedMonths() == null || payment.getPurchasedMonths() != 1
                || payment.getMonthlyPriceSnapshot() == null || payment.getMonthlyPriceSnapshot().signum() <= 0
                || payment.getAmount() == null
                || payment.getAmount().compareTo(payment.getMonthlyPriceSnapshot()) != 0
                || attemptAmount == null || attemptAmount.compareTo(payment.getMonthlyPriceSnapshot()) != 0
                || reportedAmount == null || reportedAmount.compareTo(payment.getMonthlyPriceSnapshot()) != 0) {
            return "Verified renewal capture amount or one-month price snapshot does not match";
        }
        if (payment.getRenewalAutoRenewChoice() == null) return "Member has not explicitly selected Auto Renew for the next period";
        if (source.getSourcePayment() == null || source.getSourcePayment().getStatus() != PaymentStatus.SUCCESS
                || source.getStartDate() == null || source.getStartDate().isAfter(receivedAt.toLocalDate())
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !"ACTIVE".equalsIgnoreCase(source.getStatus()) && !"EXPIRED".equalsIgnoreCase(source.getStatus())) {
            return "Source membership is not a paid renewal source eligible for settlement";
        }
        List<Membership> paidPeriods = membershipRepository.findSuccessfulPaidPeriods(context.member().getId());
        if (paidPeriods.isEmpty() || !paidPeriods.get(0).getId().equals(source.getId())) {
            return "Source membership is no longer the final paid period";
        }
        LocalDate nextEnd = source.getEndDate().plusMonths(1);
        if (membershipRepository.existsTimelineOverlap(context.member().getId(), source.getId(), source.getEndDate(), nextEnd)) {
            return "Another membership period overlaps the proposed renewal period";
        }
        return null;
    }

    private Membership createNextMembership(LockContext context, BigDecimal snapshotPrice) {
        Membership source = context.source();
        Membership created = new Membership();
        created.setMember(context.member());
        created.setPlan(source.getPlan());
        created.setStartDate(source.getEndDate());
        created.setEndDate(source.getEndDate().plusMonths(1));
        created.setStatus("ACTIVE");
        created.setAutoRenew(context.payment().getRenewalAutoRenewChoice());
        created.setPurchasedMonths(1);
        created.setPaidAmount(snapshotPrice);
        created.setSourcePayment(context.payment());
        return created;
    }

    private RenewalSettlementResult reconcile(PaymentCheckoutAttempt attempt, Payment payment, String reason) {
        attempt.setStatus(CheckoutAttemptStatus.RECONCILIATION_REQUIRED);
        attempt.setReconciliationReason(trim(reason));
        attemptRepository.save(attempt);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            if (attempt.getVerifiedAt() != null) payment.setPaidAt(attempt.getVerifiedAt());
            payment.setStatus(payment.getMethod() == null ? PaymentStatus.CANCELLED : PaymentStatus.REFUND_REQUIRED);
            payment.setNote(trim(reason));
            paymentRepository.save(payment);
        }
        return new RenewalSettlementResult(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                payment.getId(), attempt.getId(), null, reason);
    }

    private RenewalSettlementResult counterReconcile(Payment payment, String reason) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.setStatus(payment.getMethod() == null ? PaymentStatus.CANCELLED : PaymentStatus.REFUND_REQUIRED);
            payment.setNote(trim(reason));
            paymentRepository.save(payment);
        }
        return new RenewalSettlementResult(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                payment.getId(), null, null, reason);
    }

    private boolean isCounterEvidence(Payment payment) {
        return payment.getMethod() == PaymentMethod.COUNTER && payment.getPaidAt() != null
                && payment.getNote() != null && payment.getNote().startsWith(COUNTER_EVIDENCE_PREFIX);
    }

    private static boolean decimalEquals(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
    private static boolean equalsNullable(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private static String safeReason(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String trim(String value) { return value == null ? "Renewal capture requires manual reconciliation" : value.substring(0, Math.min(500, value.length())); }

    private record LockContext(User member, Membership source, Payment payment, List<PaymentCheckoutAttempt> attempts) { }
}
