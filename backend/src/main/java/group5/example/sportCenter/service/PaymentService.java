package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.PendingCoursePaymentResponse;
import group5.example.sportCenter.dto.SelectRenewalPaymentMethodRequest;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.CourseRegistrationPaymentHandler;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.payment.PtBookingPaymentHandler;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final PaymentCheckoutAttemptRepository paymentCheckoutAttemptRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipService membershipService;
    private final CourseRegistrationPaymentHandler courseRegistrationHandler;
    private final PtBookingPaymentHandler ptBookingHandler;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentCheckoutAttemptRepository paymentCheckoutAttemptRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipService membershipService,
            CourseRegistrationPaymentHandler courseRegistrationHandler,
            PtBookingPaymentHandler ptBookingHandler,
            PaymentGateway paymentGateway,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentCheckoutAttemptRepository = paymentCheckoutAttemptRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.membershipService = membershipService;
        this.courseRegistrationHandler = courseRegistrationHandler;
        this.ptBookingHandler = ptBookingHandler;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    /** Member chọn phương thức thanh toán -> tạo Payment PENDING. */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, Jwt callerJwt) {
        return createPayment(request, callerJwt, null);
    }

    /** clientIp: IP trình duyệt của người thanh toán (VNPay yêu cầu vnp_IpAddr). */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, Jwt callerJwt, String clientIp) {
        User actor = currentActor(callerJwt);
        User member = resolveMember(actor, request.getMemberId());

        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP) {
            // Serialize repeated requests on a stable member row before checking PENDING payments.
            Integer lockedMemberId = member.getId();
            member = userRepository.findByIdForBookingUpdate(lockedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + lockedMemberId));
            if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Membership payment requires an ACTIVE MEMBER account");
            }
        }

        BigDecimal amount = resolveAmountAndValidate(request, member);
        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP
                && paymentRepository.existsOrdinaryPayment(
                        member.getId(), PaymentPurpose.MEMBERSHIP, request.getReferenceId(), PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending membership payment already exists for this member and plan");
        }

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        payment.setPurpose(request.getPurpose());
        payment.setReferenceId(request.getReferenceId());
        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP) {
            MembershipPlan plan = membershipPlanRepository.findById(request.getReferenceId()).orElseThrow();
            // New purchases are one month. Keep this explicit on the payment so later settlement
            // never depends on a mutable catalog duration; older pending rows retain their snapshot.
            payment.setPurchasedMonths(request.getPurchasedMonths() == null ? 1 : request.getPurchasedMonths());
            payment.setMonthlyPriceSnapshot(plan.getEffectiveMonthlyPrice());
        }
        payment.setAutoRenew(request.getPurpose() == PaymentPurpose.MEMBERSHIP
                ? Boolean.TRUE.equals(request.getAutoRenew()) : null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment = paymentRepository.save(payment);

        String url = request.getMethod() == PaymentMethod.ONLINE
                ? paymentGateway.createPaymentUrl(payment.getTransactionCode(), amount, clientIp)
                : null;
        return toResponse(payment, url);
    }

    /** Nhánh "At Counter": Receptionist xử lý thanh toán. */
    @Transactional
    public PaymentResponse processAtCounter(Integer paymentId, CounterPaymentRequest request, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getRenewalMembership() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Membership renewal settlement is unavailable until Stage C");
        }
        if (payment.getMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not a counter payment");
        }
        requirePending(payment);

        payment.setProcessedBy(actor);
        return toResponse(finalizePayment(payment, request.getSuccess(), request.getNote()), null);
    }

    /**
     * Nhánh "Online": callback từ cổng thanh toán (endpoint public, bảo vệ bằng chữ ký).
     * Không ném lỗi cho các trường hợp bị từ chối để controller tự trả về theo định dạng của từng cổng
     * (mock: HTTP status, VNPay IPN: RspCode, VNPay return: redirect về frontend).
     */
    @Transactional
    public OnlineCallbackResult processOnlineCallback(Map<String, String> params) {
        Optional<GatewayCallback> verified = paymentGateway.verifyCallback(params);
        if (verified.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.INVALID_SIGNATURE, null);
        }
        GatewayCallback callback = verified.get();
        Optional<Payment> found = paymentRepository.findByTransactionCodeForUpdate(callback.transactionCode());
        if (found.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        Payment payment = found.get();
        if (payment.getMethod() != PaymentMethod.ONLINE) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE, null);
        }
        if (callback.success() && payment.getRenewalMembership() != null
                && callback.amount() != null && payment.getAmount().compareTo(callback.amount()) != 0
                && payment.getStatus() == PaymentStatus.PENDING) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markRefundRequired(payment,
                            "Verified renewal callback amount mismatch requires manual reconciliation; Stage C settlement is unavailable"), null));
        }
        if (callback.amount() != null && payment.getAmount().compareTo(callback.amount()) != 0) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH, null);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED && callback.success()) {
            // Member đã huỷ nhưng cổng vẫn báo trừ tiền: không áp dụng, chuyển sang hoàn tiền.
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markRefundRequired(payment, "Payment succeeded after it was cancelled"), null));
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            // callback lặp lại: bỏ qua (idempotent)
            return new OnlineCallbackResult(OnlineCallbackResult.Status.ALREADY_PROCESSED, toResponse(payment, null));
        }
        String failureNote = callback.failureReason() != null ? callback.failureReason() : "Online payment failed";
        if (callback.success() && payment.getRenewalMembership() != null) {
            // A signed success is evidence that funds may have been captured. Stage B cannot
            // activate renewals, so retain the captured-payment reconciliation state.
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markRefundRequired(payment,
                            "Verified renewal payment requires manual reconciliation; renewal settlement is not available until Stage C"), null));
        }
        return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED, toResponse(
                finalizePayment(payment, callback.success(), callback.success() ? null : failureNote), null));
    }

    @Transactional
    public PaymentResponse selectRenewalPaymentMethod(Integer paymentId,
            group5.example.sportCenter.dto.SelectRenewalPaymentMethodRequest request, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the owning MEMBER can select a renewal payment method");
        }
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        Membership snapshotMembership = snapshot.getRenewalMembership();
        if (snapshotMembership == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not a renewal request");
        }
        userRepository.findByIdForBookingUpdate(snapshot.getMember().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        Membership source = membershipService.lockRenewalSource(snapshotMembership.getId());
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        List<group5.example.sportCenter.entity.PaymentCheckoutAttempt> attempts = paymentCheckoutAttemptRepository
                .findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(paymentId);
        if (attempts.stream().anyMatch(attempt -> attempt.getVerifiedAt() != null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A verified renewal capture is recorded; payment method cannot be changed before reconciliation");
        }
        if (!payment.getMember().getId().equals(actor.getId())
                || !source.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only select a payment method for your own renewal request");
        }
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (payment.getRenewalMembership() == null
                || !payment.getRenewalMembership().getId().equals(source.getId())
                || payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || payment.getStatus() != PaymentStatus.PENDING
                || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || source.getStartDate() == null || source.getStartDate().isAfter(today)
                || source.getEndDate() == null || !source.getEndDate().isAfter(today)
                || source.getEndDate().isAfter(today.plusDays(3))
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !membershipService.isFinalPaidRenewalPeriod(source)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Renewal request is no longer eligible for payment method selection");
        }
        if (request.getMethod() != PaymentMethod.ONLINE && request.getMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Renewal method must be ONLINE or COUNTER");
        }
        // No checkout URL or counter settlement is issued in Stage B, so method selection
        // remains reversible while the request is pending and no processing has begun.
        payment.setMethod(request.getMethod());
        payment.setNote("Payment method selected; renewal settlement will be available in Stage C");
        return toResponse(paymentRepository.save(payment), null);
    }

    /** Huỷ payment đang PENDING (member huỷ của mình, hoặc staff huỷ). */
    @Transactional
    public PaymentResponse cancelPayment(Integer paymentId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        Payment payment;
        java.util.List<group5.example.sportCenter.entity.PaymentCheckoutAttempt> renewalAttempts = java.util.List.of();
        if (snapshot.getRenewalMembership() != null) {
            userRepository.findByIdForBookingUpdate(snapshot.getMember().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
            membershipService.lockRenewalSource(snapshot.getRenewalMembership().getId());
            payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
            renewalAttempts = paymentCheckoutAttemptRepository.findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(paymentId);
        } else {
            // Preserve the ordinary payment locking path outside renewal processing.
            payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        }
        if (!isStaff(actor) && !payment.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own payments");
        }
        requirePending(payment);
        var verifiedAttempt = renewalAttempts.stream().filter(a -> a.getVerifiedAt() != null).findFirst().orElse(null);
        if (payment.getRenewalMembership() != null && verifiedAttempt != null) {
            payment.setStatus(payment.getMethod() == null ? PaymentStatus.CANCELLED : PaymentStatus.REFUND_REQUIRED);
            payment.setPaidAt(verifiedAttempt.getVerifiedAt());
            payment.setNote("Verified renewal capture exists; cancellation requires manual reconciliation");
        } else {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setNote("Cancelled by " + actor.getRole().toUpperCase(Locale.ROOT));
        }
        return toResponse(paymentRepository.save(payment), null);
    }

    /**
     * Payment Processing -> Successful?
     *   No  -> Payment Failed -> Cancel
     *   Yes -> Payment Complete -> Payment Purpose
     *          -> (Active/Renew Membership | Confirm Class Registration | Confirm PT Booking)
     *       -> Update Payment Status (SUCCESS, hoặc REFUND_REQUIRED nếu không áp dụng được)
     */
    private Payment finalizePayment(Payment payment, boolean success, String note) {
        if (!success) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setNote(note != null && !note.isBlank() ? note : "Payment failed");
            return paymentRepository.save(payment);
        }

        if (payment.getRenewalMembership() != null) {
            return markRefundRequired(payment,
                    "Verified renewal payment requires manual reconciliation; renewal settlement is not available until Stage C");
        }

        Optional<String> refundReason = switch (payment.getPurpose()) {
            case MEMBERSHIP -> {
                membershipService.activateOrRenewFromPayment(payment);
                yield Optional.empty();
            }
            case COURSE_REGISTRATION -> courseRegistrationHandler.apply(payment);
            case PT_BOOKING -> ptBookingHandler.apply(payment);
        };
        if (refundReason.isPresent()) {
            return markRefundRequired(payment, refundReason.get());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(now());
        if (note != null && !note.isBlank()) {
            payment.setNote(note);
        }
        return paymentRepository.save(payment);
    }

    private Payment markRefundRequired(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setPaidAt(now());
        payment.setNote(reason);
        return paymentRepository.save(payment);
    }

    /** View Payment History (của chính mình). */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyHistory(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        return paymentRepository.findByMemberIdOrderByCreatedAtDesc(actor.getId())
                .stream().map(p -> toResponse(p, null)).toList();
    }

    /** View Payment History của một member (ADMIN/RECEPTIONIST). */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getHistoryByMember(Integer memberId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberId);
        }
        return paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream().map(p -> toResponse(p, null)).toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPendingMembershipCounterPayments(int page, int size, Jwt jwt) {
        requireStaff(currentActor(jwt));
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        return paymentRepository.findOrdinaryPendingMembershipPayments(
                PaymentStatus.PENDING, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(payment -> toResponse(payment, null));
    }

    @Transactional(readOnly = true)
    public Page<PendingCoursePaymentResponse> getPendingCourseCounterPayments(int page, int size, Jwt jwt) {
        requireStaff(currentActor(jwt));
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        // Read eligibility only: an expired hold does not prove that money was received.
        return paymentRepository.findPendingCoursePayments(now(), PageRequest.of(page, size));
    }

    // ---------------------------------------------------------------- helpers

    /** Số tiền luôn do server tính, không nhận từ client. */
    private BigDecimal resolveAmountAndValidate(CreatePaymentRequest request, User member) {
        return switch (request.getPurpose()) {
            case MEMBERSHIP -> {
                Integer months = request.getPurchasedMonths();
                if (months != null && months != 1) {
                    throw new IllegalArgumentException("New membership purchases must be exactly one month");
                }
                MembershipPlan plan = membershipPlanRepository.findById(request.getReferenceId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Membership plan not found with id: " + request.getReferenceId()));
                if (!"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
                    throw new IllegalArgumentException("Membership plan is not active");
                }
                BigDecimal monthlyPrice = plan.getEffectiveMonthlyPrice();
                if (monthlyPrice == null || monthlyPrice.signum() < 0) {
                    throw new IllegalArgumentException("Membership plan monthly price has not been safely configured");
                }
                yield monthlyPrice.setScale(2, java.math.RoundingMode.UNNECESSARY);
            }
            case COURSE_REGISTRATION -> courseRegistrationHandler.quote(member, request.getReferenceId());
            case PT_BOOKING -> ptBookingHandler.quote(member, request.getReferenceId());
        };
    }

    private User resolveMember(User actor, Integer requestedMemberId) {
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        final User member;
        if (role.equals("MEMBER")) {
            if (requestedMemberId != null && !requestedMemberId.equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay for yourself");
            }
            member = actor;
        } else if (isStaff(actor)) {
            if (requestedMemberId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member ID is required");
            }
            member = userRepository.findById(requestedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requestedMemberId));
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot create payments");
        }
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment can only be made for a MEMBER");
        }
        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new IllegalArgumentException("Member account is not active");
        }
        return member;
    }

    private void requirePending(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Payment was already processed: " + payment.getStatus());
        }
    }

    private boolean isStaff(User actor) {
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        return role.equals("ADMIN") || role.equals("RECEPTIONIST");
    }

    private void requireStaff(User actor) {
        if (!isStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can do this");
        }
    }

    private User currentActor(Jwt callerJwt) {
        final Integer userId;
        try {
            userId = Integer.valueOf(callerJwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access payments");
        }
        return actor;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }

    private PaymentResponse toResponse(Payment p, String paymentUrl) {
        return new PaymentResponse(
                p.getId(), p.getMember().getId(), p.getMember().getName(), p.getAmount(),
                p.getMethod() == null ? null : p.getMethod().name(), p.getPurpose().name(), p.getReferenceId(), p.getStatus().name(),
                p.getTransactionCode(), paymentUrl, p.getNote(), p.getCreatedAt(), p.getPaidAt(), p.getPurchasedMonths());
    }
}
