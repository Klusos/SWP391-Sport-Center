package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipUpgradeCaptureEvidence;
import group5.example.sportCenter.entity.MembershipUpgradeCheckoutAttempt;
import group5.example.sportCenter.entity.MembershipUpgradeCounterReceipt;
import group5.example.sportCenter.entity.MembershipUpgradePaymentEvidence;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.payment.VerifiedUpgradeCapture;
import group5.example.sportCenter.payment.VerifiedUpgradeFailure;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCaptureEvidenceRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCheckoutAttemptRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCounterReceiptRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/** Each capture is durably recorded in its own transaction before entitlement mutation begins. */
@Service
public class MembershipUpgradeSettlementTransactions {
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final MembershipUpgradePaymentEvidenceRepository upgradeEvidence;
    private final MembershipUpgradeCaptureEvidenceRepository captureEvidence;
    private final MembershipUpgradeCheckoutAttemptRepository checkoutAttempts;
    private final MembershipUpgradeCounterReceiptRepository counterReceipts;
    private final MembershipPlanRepository plans;
    private final MembershipPlanUpgradeRepository paths;
    @PersistenceContext private EntityManager entityManager;

    public MembershipUpgradeSettlementTransactions(UserRepository users, MembershipRepository memberships,
            PaymentRepository payments, MembershipUpgradePaymentEvidenceRepository upgradeEvidence,
            MembershipUpgradeCaptureEvidenceRepository captureEvidence,
            MembershipUpgradeCheckoutAttemptRepository checkoutAttempts,
            MembershipUpgradeCounterReceiptRepository counterReceipts, MembershipPlanRepository plans,
            MembershipPlanUpgradeRepository paths) {
        this.users = users;
        this.memberships = memberships;
        this.payments = payments;
        this.upgradeEvidence = upgradeEvidence;
        this.captureEvidence = captureEvidence;
        this.checkoutAttempts = checkoutAttempts;
        this.counterReceipts = counterReceipts;
        this.plans = plans;
        this.paths = paths;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult recordCapture(VerifiedUpgradeCapture capture, String key, Instant receivedAt) {
        MembershipUpgradeCheckoutAttempt attemptSnapshot = checkoutAttempts.findByTransactionReference(capture.transactionCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade checkout attempt was not found"));
        Payment snapshot = attemptSnapshot.getPayment();
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade Payment was not found");
        MembershipUpgradePaymentEvidence evidenceSnapshot = upgradeEvidence.findByPayment_Id(snapshot.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        users.findByIdForBookingUpdate(snapshot.getMember().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member was not found"));
        Membership source = memberships.findByIdForUpgradeUpdate(evidenceSnapshot.getSourceMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade source Membership is missing"));
        Payment payment = payments.findByIdForUpdate(snapshot.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        entityManager.refresh(source, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradePaymentEvidence evidence = upgradeEvidence.findByPaymentIdForUpdate(payment.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        entityManager.refresh(evidence, LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradeCheckoutAttempt attempt = checkoutAttempts.findByReferenceForUpdate(capture.transactionCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade checkout attempt was not found"));
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
        List<MembershipUpgradeCheckoutAttempt> allAttempts=checkoutAttempts.findByPaymentIdForUpdate(payment.getId());

        var prior = captureEvidence.findByEvidenceKey(key).orElse(null);
        if (prior != null) {
            if (payment.getStatus() == PaymentStatus.SUCCESS && evidence.getAppliedMembership() != null) {
                return result(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED, payment, evidence.getAppliedMembership(), null);
            }
            if ("RECONCILIATION_REQUIRED".equals(prior.getOutcome())) {
                return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, payment, null, prior.getReconciliationReason());
            }
            return result(MembershipUpgradeSettlementResult.Outcome.APPLIED, payment, null, null);
        }

        MembershipUpgradeCaptureEvidence event = new MembershipUpgradeCaptureEvidence();
        event.setUpgradeEvidence(evidence);
        event.setCheckoutAttempt(attempt);
        event.setEvidenceKey(key);
        event.setProviderTransactionId(capture.providerTransactionId());
        event.setTransactionCode(capture.transactionCode());
        event.setProviderResponseCode(capture.responseCode());
        event.setReportedAmount(capture.amount());
        event.setCapturedAtUtc(capture.capturedAt());
        event.setReceivedAtUtc(receivedAt);
        event.setOutcome("RECORDED");

        String invalid = null;
        if (attempt.getProvider() != group5.example.sportCenter.entity.CheckoutProvider.VNPAY
                || payment.getMethod() != group5.example.sportCenter.entity.PaymentMethod.ONLINE
                || attempt.getStatus() == group5.example.sportCenter.entity.CheckoutAttemptStatus.CAPTURED
                || payment.getPurpose() != PaymentPurpose.UPGRADE || !payment.getMember().getId().equals(source.getMember().getId())
                || !payment.getReferenceId().equals(evidence.getTargetPlan().getId())) {
            invalid = "Verified capture does not match Upgrade purpose, owner, or target";
        } else if (capture.capturedAt() == null) {
            invalid = "Verified provider capture time is unavailable; captured funds require reconciliation";
        } else if (capture.amount() == null || payment.getAmount() == null
                || capture.amount().compareTo(payment.getAmount()) != 0
                || evidence.getPayableAmount().compareTo(payment.getAmount()) != 0) {
            invalid = "Verified capture amount differs from the immutable Upgrade amount";
        // VNPay PayDate/ExpireDate have second precision. Treat the provider's expiry second
        // as expired at its start rather than accepting a timestamp bucket that straddles expiry.
        } else if (capture.capturedAt().isBefore(evidence.getCreatedAtUtc())
                || !capture.capturedAt().isBefore(evidence.getExpiresAtUtc().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                || !capture.capturedAt().isBefore(attempt.getExpiresAtUtc().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
            invalid = "Provider capture was outside the Upgrade Payment validity window";
        } else if (!"00".equals(capture.responseCode())) {
            invalid = "Verified provider outcome is not a successful capture";
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.SUCCESS
                && evidence.getAppliedMembership() == null) {
            invalid = "Capture conflicts with a terminal or already-settled Upgrade Payment";
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS && evidence.getAppliedMembership() != null) {
            event.setOutcome("RECONCILIATION_REQUIRED");
            event.setReconciliationReason("A distinct successful provider event arrived after Upgrade was already applied");
            captureEvidence.saveAndFlush(event);
            return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, payment, evidence.getAppliedMembership(),
                    event.getReconciliationReason());
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            event.setOutcome("RECONCILIATION_REQUIRED");
            event.setReconciliationReason("Provider capture conflicts with an already-successful Upgrade Payment lacking an applied Membership link");
            captureEvidence.saveAndFlush(event);
            return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, payment, null,
                    event.getReconciliationReason());
        }
        if (!captureEvidence.findByPaymentIdForUpdate(payment.getId()).isEmpty()) {
            invalid = "A distinct provider capture was already recorded for this Upgrade Payment";
        }
        if(allAttempts.stream().anyMatch(other -> !other.getId().equals(attempt.getId())
                && (other.getStatus()==group5.example.sportCenter.entity.CheckoutAttemptStatus.URL_ISSUED
                    || other.getStatus()==group5.example.sportCenter.entity.CheckoutAttemptStatus.CAPTURED
                    || other.getStatus()==group5.example.sportCenter.entity.CheckoutAttemptStatus.RECONCILIATION_REQUIRED)))
            invalid="A competing Upgrade checkout attempt remains unresolved";
        var providerOwner = checkoutAttempts.findByProviderTransactionId(capture.providerTransactionId()).orElse(null);
        if (providerOwner != null && !providerOwner.getId().equals(attempt.getId())) {
            invalid = "Provider transaction ID is already claimed by another Upgrade attempt";
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            invalid = "Upgrade Payment is not in a capturable state";
        }

        event.setOutcome(invalid == null ? "RECORDED" : "RECONCILIATION_REQUIRED");
        event.setReconciliationReason(invalid);
        captureEvidence.saveAndFlush(event);
        if (attempt.getProviderTransactionId() != null
                && !attempt.getProviderTransactionId().equals(capture.providerTransactionId())) {
            invalid = "A different provider transaction ID was already recorded for this attempt";
            event.setOutcome("RECONCILIATION_REQUIRED"); event.setReconciliationReason(invalid);
            captureEvidence.saveAndFlush(event);
        } else if (attempt.getProviderTransactionId() == null && providerOwner == null) {
            attempt.setProviderTransactionId(capture.providerTransactionId());
        }
        attempt.setStatus(invalid == null ? group5.example.sportCenter.entity.CheckoutAttemptStatus.CAPTURED
                : group5.example.sportCenter.entity.CheckoutAttemptStatus.RECONCILIATION_REQUIRED);
        checkoutAttempts.save(attempt);
        // A verified success means funds may have moved. Never leave it represented as unpaid.
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setPaidAt(capture.capturedAt() == null ? null : LocalDateTime.ofInstant(capture.capturedAt(), java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        payment.setNote(invalid == null ? "Verified Upgrade capture recorded; application pending" : invalid);
        payments.saveAndFlush(payment);
        return result(invalid == null ? MembershipUpgradeSettlementResult.Outcome.APPLIED
                : MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, payment, null, invalid);
    }

    /** Preserves a conflicting provider transaction identifier after the uniqueness backstop rejects it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult recordProviderCollision(VerifiedUpgradeCapture capture, String key, Instant receivedAt) {
        MembershipUpgradeCheckoutAttempt attempt=checkoutAttempts.findByTransactionReference(capture.transactionCode()).orElse(null);
        if(attempt==null) return new MembershipUpgradeSettlementResult(MembershipUpgradeSettlementResult.Outcome.NOT_APPLICABLE,null,null,"Unknown Upgrade attempt");
        Payment snapshot=attempt.getPayment(); MembershipUpgradePaymentEvidence snapEvidence=upgradeEvidence.findByPayment_Id(snapshot.getId()).orElseThrow();
        users.findByIdForBookingUpdate(snapshot.getMember().getId()).orElseThrow();
        memberships.findByIdForUpgradeUpdate(snapEvidence.getSourceMembership().getId()).orElseThrow();
        Payment payment=payments.findByIdForUpdate(snapshot.getId()).orElseThrow();
        attempt=checkoutAttempts.findByReferenceForUpdate(capture.transactionCode()).orElseThrow();
        MembershipUpgradePaymentEvidence evidence=upgradeEvidence.findByPaymentIdForUpdate(payment.getId()).orElseThrow();
        if(captureEvidence.findByEvidenceKey(key).isPresent()) return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,"Provider transaction collision requires reconciliation");
        MembershipUpgradeCaptureEvidence event=new MembershipUpgradeCaptureEvidence();
        event.setUpgradeEvidence(evidence); event.setCheckoutAttempt(attempt); event.setEvidenceKey(key);
        event.setTransactionCode(capture.transactionCode()); event.setProviderTransactionId(capture.providerTransactionId());
        event.setProviderResponseCode(capture.responseCode()); event.setReportedAmount(capture.amount());
        event.setCapturedAtUtc(capture.capturedAt()); event.setReceivedAtUtc(receivedAt);
        var providerOwner=checkoutAttempts.findByProviderTransactionId(capture.providerTransactionId()).orElse(null);
        event.setOutcome("RECONCILIATION_REQUIRED");
        event.setReconciliationReason(providerOwner!=null && !providerOwner.getId().equals(attempt.getId())
                ? "Provider transaction ID is already claimed by another Upgrade attempt"
                : "A database uniqueness constraint prevented recording this Upgrade capture");
        captureEvidence.saveAndFlush(event);
        attempt.setStatus(group5.example.sportCenter.entity.CheckoutAttemptStatus.RECONCILIATION_REQUIRED); checkoutAttempts.save(attempt);
        if(payment.getStatus()!=PaymentStatus.SUCCESS) { payment.setStatus(PaymentStatus.REFUND_REQUIRED); payment.setPaidAt(capture.capturedAt()==null?null:LocalDateTime.ofInstant(capture.capturedAt(),java.time.ZoneId.of("Asia/Ho_Chi_Minh"))); payment.setNote(event.getReconciliationReason()); payments.saveAndFlush(payment); }
        return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,event.getReconciliationReason());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult recordFailure(VerifiedUpgradeFailure failure, String key, Instant receivedAt) {
        MembershipUpgradeCheckoutAttempt snapshotAttempt=checkoutAttempts.findByTransactionReference(failure.attemptReference()).orElse(null);
        if (snapshotAttempt==null) return new MembershipUpgradeSettlementResult(
                MembershipUpgradeSettlementResult.Outcome.NOT_APPLICABLE, null, null, "Unknown Upgrade attempt");
        Payment snapshot=snapshotAttempt.getPayment();
        MembershipUpgradePaymentEvidence snapshotEvidence=upgradeEvidence.findByPayment_Id(snapshot.getId()).orElseThrow();
        users.findByIdForBookingUpdate(snapshot.getMember().getId()).orElseThrow();
        memberships.findByIdForUpgradeUpdate(snapshotEvidence.getSourceMembership().getId()).orElseThrow();
        Payment payment=payments.findByIdForUpdate(snapshot.getId()).orElseThrow();
        MembershipUpgradePaymentEvidence evidence=upgradeEvidence.findByPaymentIdForUpdate(payment.getId()).orElseThrow();
        MembershipUpgradeCheckoutAttempt attempt=checkoutAttempts.findByReferenceForUpdate(failure.attemptReference()).orElseThrow();
        if (captureEvidence.findByEvidenceKey(key).isPresent()) return result(
                MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED,payment,null,null);
        MembershipUpgradeCaptureEvidence event=new MembershipUpgradeCaptureEvidence();
        event.setUpgradeEvidence(evidence); event.setCheckoutAttempt(attempt); event.setEvidenceKey(key);
        event.setTransactionCode(failure.attemptReference()); event.setProviderTransactionId(failure.providerTransactionId());
        event.setProviderResponseCode(failure.responseCode()+"/"+failure.transactionStatus());
        event.setReportedAmount(failure.amount()); event.setReceivedAtUtc(receivedAt);
        boolean valid=attempt.getProvider()==group5.example.sportCenter.entity.CheckoutProvider.VNPAY
                && payment.getMethod()==group5.example.sportCenter.entity.PaymentMethod.ONLINE
                && payment.getPurpose()==PaymentPurpose.UPGRADE && failure.amount()!=null
                && failure.amount().compareTo(payment.getAmount())==0 && payment.getStatus()==PaymentStatus.PENDING
                && attempt.getProviderTransactionId()==null;
        var priorProviderOwner=checkoutAttempts.findByProviderTransactionId(failure.providerTransactionId()).orElse(null);
        if(priorProviderOwner!=null && !priorProviderOwner.getId().equals(attempt.getId())) valid=false;
        event.setOutcome(valid?"FAILED":"RECONCILIATION_REQUIRED");
        event.setReconciliationReason(valid?null:"Failed callback conflicts with current Upgrade attempt/payment state");
        captureEvidence.saveAndFlush(event);
        if (valid) {
            attempt.setStatus(group5.example.sportCenter.entity.CheckoutAttemptStatus.FAILED);
            attempt.setProviderTransactionId(failure.providerTransactionId());
            checkoutAttempts.save(attempt);
        } else {
            attempt.setStatus(group5.example.sportCenter.entity.CheckoutAttemptStatus.RECONCILIATION_REQUIRED);
            checkoutAttempts.save(attempt);
            // A signed failure is not capture evidence. Preserve the conflict without representing funds as captured.
            if(payment.getStatus()!=PaymentStatus.SUCCESS) { payment.setNote(event.getReconciliationReason()); payments.save(payment); }
        }
        return result(valid?MembershipUpgradeSettlementResult.Outcome.APPLIED:
                MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,event.getReconciliationReason());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult applyCapture(Integer paymentId, String key,
            LocalDateTime settlementTime, Instant now) {
        Payment snapshot = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        MembershipUpgradePaymentEvidence evidenceSnapshot = upgradeEvidence.findByPayment_Id(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        users.findByIdForBookingUpdate(snapshot.getMember().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member was not found"));
        Membership source = memberships.findByIdForUpgradeUpdate(evidenceSnapshot.getSourceMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Source Membership was not found"));
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        entityManager.refresh(source, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradePaymentEvidence evidence = upgradeEvidence.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        entityManager.refresh(evidence, LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradeCaptureEvidence event = captureEvidence.findByEvidenceKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Verified capture evidence is missing"));
        MembershipUpgradeCheckoutAttempt attempt = event.getCheckoutAttempt() == null ? null
                : checkoutAttempts.findByReferenceForUpdate(event.getCheckoutAttempt().getTransactionReference()).orElse(null);

        if (payment.getStatus() == PaymentStatus.SUCCESS && evidence.getAppliedMembership() != null) {
            return result(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED, payment, evidence.getAppliedMembership(), null);
        }
        if (!"RECORDED".equals(event.getOutcome()) || event.getCapturedAtUtc() == null) {
            return reconcile(payment, event, "Capture evidence is not eligible for application");
        }
        LocalDate today = settlementTime.toLocalDate();
        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED || payment.getPurpose() != PaymentPurpose.UPGRADE
                || payment.getMethod() != group5.example.sportCenter.entity.PaymentMethod.ONLINE
                || attempt == null || !attempt.getPayment().getId().equals(paymentId)
                || !attempt.getTransactionReference().equals(event.getTransactionCode())
                || !attempt.getId().equals(event.getCheckoutAttempt().getId())
                || event.getReportedAmount().compareTo(payment.getAmount()) != 0
                || event.getCapturedAtUtc().isBefore(evidence.getCreatedAtUtc())
                || !event.getCapturedAtUtc().isBefore(evidence.getExpiresAtUtc().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                || !event.getCapturedAtUtc().isBefore(attempt.getExpiresAtUtc().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
            return reconcile(payment, event, "Capture, payment, or immutable Upgrade snapshot is inconsistent");
        }
        if (evidence.getAppliedMembership() != null) return reconcile(payment, event, "Upgrade evidence already links a different applied Membership");

        String invalid = validateCurrentState(payment, source, evidence, today);
        if (invalid != null) return reconcile(payment, event, invalid);

        Membership created = applyMembership(payment, source, evidence, settlementTime, now, false);
        event.setOutcome("APPLIED");
        captureEvidence.saveAndFlush(event);
        attempt.setStatus(group5.example.sportCenter.entity.CheckoutAttemptStatus.CAPTURED);
        checkoutAttempts.save(attempt);
        return result(MembershipUpgradeSettlementResult.Outcome.APPLIED, payment, created, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CounterReceiptRecordingResult recordCounterReceipt(VerifiedUpgradeCounterReceipt receipt) {
        Payment snapshot=payments.findById(receipt.paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment was not found"));
        MembershipUpgradePaymentEvidence snapshotEvidence=upgradeEvidence.findByPayment_Id(receipt.paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Upgrade evidence is missing"));
        users.findByIdForBookingUpdate(snapshot.getMember().getId()).orElseThrow();
        Membership source=memberships.findByIdForUpgradeUpdate(snapshotEvidence.getSourceMembership().getId()).orElseThrow();
        Payment payment=payments.findByIdForUpdate(receipt.paymentId).orElseThrow();
        entityManager.refresh(source,LockModeType.PESSIMISTIC_WRITE); entityManager.refresh(payment,LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradePaymentEvidence evidence=upgradeEvidence.findByPaymentIdForUpdate(payment.getId()).orElseThrow();
        var existing=counterReceipts.findByPayment_Id(payment.getId()).orElse(null);
        if(existing!=null) return payment.getStatus()==PaymentStatus.SUCCESS && evidence.getAppliedMembership()!=null
                ? new CounterReceiptRecordingResult(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED,existing.getId(),null)
                : new CounterReceiptRecordingResult(MembershipUpgradeSettlementResult.Outcome.APPLIED,existing.getId(),null);
        Instant now=receipt.receivedAt;
        if (!"ADMIN".equalsIgnoreCase(receipt.staff.getRole()) && !"RECEPTIONIST".equalsIgnoreCase(receipt.staff.getRole())
                || !"ACTIVE".equalsIgnoreCase(receipt.staff.getStatus()) || payment.getPurpose()!=PaymentPurpose.UPGRADE
                || payment.getMethod()!=group5.example.sportCenter.entity.PaymentMethod.COUNTER
                || payment.getStatus()!=PaymentStatus.PENDING || now==null || !now.isBefore(evidence.getExpiresAtUtc())
                || payment.getAmount()==null || payment.getAmount().compareTo(evidence.getPayableAmount())!=0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Counter Upgrade receipt is not eligible");
        }
        MembershipUpgradeCounterReceipt saved=new MembershipUpgradeCounterReceipt();
        saved.setPayment(payment); saved.setStaff(receipt.staff); saved.setAmount(payment.getAmount());
        saved.setReceivedAtUtc(now); saved.setStaffNote(receipt.note); saved.setOutcome("RECORDED"); saved=counterReceipts.saveAndFlush(saved);
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setPaidAt(LocalDateTime.ofInstant(now,java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        payment.setProcessedBy(receipt.staff); payment.setNote("Counter receipt recorded; Upgrade application pending");
        payments.saveAndFlush(payment);
        return new CounterReceiptRecordingResult(MembershipUpgradeSettlementResult.Outcome.APPLIED,saved.getId(),null);
    }

    @Transactional(readOnly = true)
    public MembershipUpgradeSettlementResult alreadyAppliedCounterReceipt(Integer paymentId) {
        Payment payment=payments.findById(paymentId).orElseThrow();
        MembershipUpgradePaymentEvidence evidence=upgradeEvidence.findByPayment_Id(paymentId).orElseThrow();
        if(evidence.getAppliedMembership()==null) return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                payment,null,"Counter receipt exists without an applied Membership link");
        return result(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED,payment,evidence.getAppliedMembership(),null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult applyCounterReceipt(Integer paymentId, Integer receiptId,
            LocalDateTime settlementTime, Instant now) {
        Payment snapshot=payments.findById(paymentId).orElseThrow();
        MembershipUpgradePaymentEvidence snapshotEvidence=upgradeEvidence.findByPayment_Id(paymentId).orElseThrow();
        users.findByIdForBookingUpdate(snapshot.getMember().getId()).orElseThrow();
        Membership source=memberships.findByIdForUpgradeUpdate(snapshotEvidence.getSourceMembership().getId()).orElseThrow();
        Payment payment=payments.findByIdForUpdate(paymentId).orElseThrow();
        entityManager.refresh(source,LockModeType.PESSIMISTIC_WRITE); entityManager.refresh(payment,LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradePaymentEvidence evidence=upgradeEvidence.findByPaymentIdForUpdate(paymentId).orElseThrow();
        entityManager.refresh(evidence,LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradeCounterReceipt receipt=counterReceipts.findByPayment_Id(paymentId).orElseThrow();
        if(payment.getStatus()==PaymentStatus.SUCCESS && evidence.getAppliedMembership()!=null)
            return result(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED,payment,evidence.getAppliedMembership(),null);
        if(!receipt.getId().equals(receiptId) || receipt.getAmount().compareTo(payment.getAmount())!=0
                || payment.getPurpose()!=PaymentPurpose.UPGRADE || payment.getMethod()!=group5.example.sportCenter.entity.PaymentMethod.COUNTER
                || payment.getStatus()!=PaymentStatus.REFUND_REQUIRED || !receipt.getReceivedAtUtc().isBefore(evidence.getExpiresAtUtc()))
            return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,"Counter receipt conflicts with Upgrade snapshot");
        LocalDate today=settlementTime.toLocalDate();
        String invalid=validateCurrentState(payment,source,evidence,today);
        if(invalid!=null) return reconcileCounter(payment,receipt,invalid);
        Membership created=applyMembership(payment,source,evidence,settlementTime,now,true);
        receipt.setOutcome("APPLIED"); counterReceipts.saveAndFlush(receipt);
        return result(MembershipUpgradeSettlementResult.Outcome.APPLIED,payment,created,null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult requireCounterReconciliation(Integer paymentId, String reason) {
        Payment snapshot=payments.findById(paymentId).orElseThrow();
        MembershipUpgradePaymentEvidence snapshotEvidence=upgradeEvidence.findByPayment_Id(paymentId).orElseThrow();
        users.findByIdForBookingUpdate(snapshot.getMember().getId()).orElseThrow();
        memberships.findByIdForUpgradeUpdate(snapshotEvidence.getSourceMembership().getId()).orElseThrow();
        Payment payment=payments.findByIdForUpdate(paymentId).orElseThrow();
        MembershipUpgradeCounterReceipt receipt=counterReceipts.findByPayment_Id(paymentId).orElse(null);
        if(receipt!=null) { receipt.setOutcome("RECONCILIATION_REQUIRED"); receipt.setReconciliationReason(reason); counterReceipts.save(receipt); }
        payment.setStatus(PaymentStatus.REFUND_REQUIRED); payment.setNote(reason); payments.saveAndFlush(payment);
        return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,reason);
    }

    private Membership applyMembership(Payment payment, Membership source, MembershipUpgradePaymentEvidence evidence,
            LocalDateTime settlementTime, Instant now, boolean counter) {
        LocalDate today=settlementTime.toLocalDate();
        Membership created=new Membership(); created.setMember(payment.getMember()); created.setPlan(evidence.getTargetPlan());
        created.setAutoRenew(source.getAutoRenew()); created.setStartDate(today); created.setEndDate(today.plusMonths(1));
        created.setStatus("ACTIVE"); created.setPurchasedMonths(1); created.setPaidAmount(evidence.getPayableAmount());
        created.setSourcePayment(payment); created=memberships.saveAndFlush(created);
        source.setStatus("REPLACED"); source.setReplacedOn(today); source.setReplacementMembership(created); memberships.save(source);
        evidence.setAppliedMembership(created); evidence.setAppliedAtUtc(now); upgradeEvidence.saveAndFlush(evidence);
        payment.setStatus(PaymentStatus.SUCCESS); payment.setPaidAt(settlementTime);
        payment.setNote(counter?"Counter Upgrade receipt applied; source Membership replaced":"Verified Upgrade capture applied; source Membership replaced");
        payments.saveAndFlush(payment);
        return created;
    }

    private MembershipUpgradeSettlementResult reconcileCounter(Payment payment, MembershipUpgradeCounterReceipt receipt, String reason) {
        receipt.setOutcome("RECONCILIATION_REQUIRED"); receipt.setReconciliationReason(reason); counterReceipts.save(receipt);
        payment.setStatus(PaymentStatus.REFUND_REQUIRED); payment.setNote(reason); payments.save(payment);
        return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,payment,null,reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MembershipUpgradeSettlementResult requireReconciliation(Integer paymentId, String key, String reason) {
        Payment snapshot = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        MembershipUpgradePaymentEvidence snapshotEvidence = upgradeEvidence.findByPayment_Id(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        users.findByIdForBookingUpdate(snapshot.getMember().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member was not found"));
        memberships.findByIdForUpgradeUpdate(snapshotEvidence.getSourceMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Source Membership was not found"));
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found"));
        var event = captureEvidence.findByEvidenceKey(key).orElseThrow();
        return reconcile(payment, event, reason);
    }

    private String validateCurrentState(Payment payment, Membership source,
            MembershipUpgradePaymentEvidence evidence, LocalDate today) {
        if (source.getStatus() == null || source.getStartDate() == null || source.getEndDate() == null
                || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || source.getStartDate().isAfter(today) || !source.getEndDate().isAfter(today)
                || !source.getId().equals(evidence.getSourceMembership().getId())
                || !source.getStartDate().equals(evidence.getSourceStartDate())
                || !source.getEndDate().equals(evidence.getSourceEndDate())) return "Source Membership is no longer the quoted current entitlement";
        List<Membership> current = memberships.findEffectiveMembershipsForUpgrade(payment.getMember().getId(), today);
        if (current.size() != 1 || !current.get(0).getId().equals(source.getId())) return "Source Membership is not the unique current entitlement on settlement date";
        if (!memberships.findFutureActiveMembershipsForUpgrade(payment.getMember().getId(), today).isEmpty()) return "A future Membership conflicts with Upgrade application";
        List<Payment> unresolvedPurchases = payments.findByMember_IdAndPurposeAndStatusIn(payment.getMember().getId(),
                PaymentPurpose.MEMBERSHIP, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED));
        if (!unresolvedPurchases.isEmpty()) return "A pending or unresolved Purchase/Renewal conflicts with Upgrade";
        List<Payment> upgrades = payments.findByMember_IdAndPurposeAndStatusIn(payment.getMember().getId(),
                PaymentPurpose.UPGRADE, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED, PaymentStatus.CANCELLED));
        if (upgrades.stream().anyMatch(p -> !p.getId().equals(payment.getId())
                && (p.getStatus() != PaymentStatus.CANCELLED || p.getPaidAt() != null))) return "A competing Upgrade Payment requires reconciliation";
        if (payment.getMember().getId() == null || source.getMember().getId().intValue() != payment.getMember().getId().intValue()
                || source.getSourcePayment() == null || source.getSourcePayment().getStatus() != PaymentStatus.SUCCESS
                || source.getPaidAmount() == null || source.getPaidAmount().compareTo(evidence.getSourcePaidAmount()) != 0
                || source.getSourcePayment().getAmount() == null
                || source.getSourcePayment().getAmount().compareTo(evidence.getSourcePaidAmount()) != 0
                || !source.getSourcePayment().getMember().getId().equals(payment.getMember().getId())) return "Source paid-amount evidence changed or is inconsistent";
        MembershipPlan sourcePlan = source.getPlan();
        MembershipPlan targetPlan = evidence.getTargetPlan();
        int low = Math.min(sourcePlan.getId(), targetPlan.getId());
        int high = Math.max(sourcePlan.getId(), targetPlan.getId());
        MembershipPlan first = plans.findByIdForUpgradeUpdate(low).orElse(null);
        MembershipPlan second = low == high ? first : plans.findByIdForUpgradeUpdate(high).orElse(null);
        if (first == null || second == null) return "A configured Upgrade plan no longer exists";
        sourcePlan = sourcePlan.getId().equals(low) ? first : second;
        targetPlan = targetPlan.getId().equals(low) ? first : second;
        if (!paths.existsByFromPlan_IdAndToPlan_Id(sourcePlan.getId(), targetPlan.getId())) return "ADMIN upgrade path no longer exists";
        BigDecimal sourcePrice = sourcePlan.getEffectiveMonthlyPrice();
        BigDecimal targetPrice = targetPlan.getEffectiveMonthlyPrice();
        if (!"ACTIVE".equalsIgnoreCase(sourcePlan.getStatus()) || !"ACTIVE".equalsIgnoreCase(targetPlan.getStatus())
                || sourcePrice == null || targetPrice == null
                || targetPrice.compareTo(sourcePrice) <= 0
                || targetPrice.compareTo(evidence.getTargetMonthlyPrice()) != 0) return "Upgrade plan status or price changed since draft issuance";
        long originalDays = ChronoUnit.DAYS.between(evidence.getSourceStartDate(), evidence.getSourceEndDate());
        long remainingDays = Math.max(0, Math.min(originalDays, ChronoUnit.DAYS.between(evidence.getIssuanceBusinessDate(), evidence.getSourceEndDate())));
        if (originalDays != evidence.getOriginalDurationDays() || remainingDays != evidence.getRemainingDays()) return "Immutable period evidence is inconsistent";
        BigDecimal expectedCredit = evidence.getSourcePaidAmount().multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(originalDays), 2, RoundingMode.HALF_UP);
        if (expectedCredit.compareTo(evidence.getRemainingCredit()) != 0
                || targetPrice.subtract(expectedCredit).compareTo(evidence.getPayableAmount()) != 0
                || payment.getAmount().compareTo(evidence.getPayableAmount()) != 0) return "Upgrade financial snapshots do not reconcile";
        if (memberships.existsTimelineOverlap(payment.getMember().getId(), source.getId(), today, today.plusMonths(1))) return "Another Membership overlaps the proposed immediate Upgrade period";
        return null;
    }

    private MembershipUpgradeSettlementResult reconcile(Payment payment, MembershipUpgradeCaptureEvidence event, String reason) {
        event.setOutcome("RECONCILIATION_REQUIRED");
        event.setReconciliationReason(reason);
        captureEvidence.save(event);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.REFUND_REQUIRED);
            if (payment.getPaidAt() == null && event.getCapturedAtUtc() != null) payment.setPaidAt(LocalDateTime.ofInstant(event.getCapturedAtUtc(), java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
            payment.setNote(reason);
            payments.save(payment);
        }
        return result(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, payment, null, reason);
    }

    private static MembershipUpgradeSettlementResult result(MembershipUpgradeSettlementResult.Outcome outcome,
            Payment payment, Membership membership, String reason) {
        return new MembershipUpgradeSettlementResult(outcome, payment.getId(), membership == null ? null : membership.getId(), reason);
    }
}
