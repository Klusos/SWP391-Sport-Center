package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.ActionablePendingPaymentSummaryResponse;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.PendingCoursePaymentResponse;
import group5.example.sportCenter.dto.PendingPtPaymentResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
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
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipUpgradeRepository membershipUpgradeRepository;
    private final CourseRegistrationRepository courseRegistrationRepository;
    private final PersonalTrainingBookingRepository personalTrainingBookingRepository;
    private final MembershipService membershipService;
    private final MembershipAutoRenewalService membershipAutoRenewalService;
    private final MembershipUpgradeService membershipUpgradeService;
    private final CourseRegistrationPaymentHandler courseRegistrationHandler;
    private final PtBookingPaymentHandler ptBookingHandler;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final int onlinePaymentExpiryMinutes;

    public PaymentService(
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipUpgradeRepository membershipUpgradeRepository,
            CourseRegistrationRepository courseRegistrationRepository,
            PersonalTrainingBookingRepository personalTrainingBookingRepository,
            MembershipService membershipService,
            MembershipAutoRenewalService membershipAutoRenewalService,
            MembershipUpgradeService membershipUpgradeService,
            CourseRegistrationPaymentHandler courseRegistrationHandler,
            PtBookingPaymentHandler ptBookingHandler,
            PaymentGateway paymentGateway,
            NotificationService notificationService,
            Clock clock,
            @Value("${app.payment.vnpay.expire-minutes:15}") int onlinePaymentExpiryMinutes) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.membershipUpgradeRepository = membershipUpgradeRepository;
        this.courseRegistrationRepository = courseRegistrationRepository;
        this.personalTrainingBookingRepository = personalTrainingBookingRepository;
        this.membershipService = membershipService;
        this.membershipAutoRenewalService = membershipAutoRenewalService;
        this.membershipUpgradeService = membershipUpgradeService;
        this.courseRegistrationHandler = courseRegistrationHandler;
        this.ptBookingHandler = ptBookingHandler;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.clock = clock;
        this.onlinePaymentExpiryMinutes = onlinePaymentExpiryMinutes;
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

        Integer autoRenewSourceMembershipId = null;
        if (request.getRenewalSourcePaymentId() != null) {
            if (request.getPurpose() != PaymentPurpose.MEMBERSHIP
                    || request.getMethod() != PaymentMethod.ONLINE
                    || !Boolean.TRUE.equals(request.getAutoRenew())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Auto-renew source is only valid for an ONLINE auto-renew Membership payment");
            }
        }

        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP || request.getPurpose() == PaymentPurpose.UPGRADE) {
            // Serialize repeated requests on a stable member row before checking PENDING payments.
            Integer lockedMemberId = member.getId();
            member = userRepository.findByIdForBookingUpdate(lockedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + lockedMemberId));
            if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Membership payment requires an ACTIVE MEMBER account");
            }
        }

        if (request.getRenewalSourcePaymentId() != null) {
            autoRenewSourceMembershipId = membershipAutoRenewalService.validateOnlineReplacement(
                    request.getRenewalSourcePaymentId(), member, request.getReferenceId());
        }

        if (request.getPurpose() == PaymentPurpose.PT_BOOKING) {
            // Serialize all payment attempts for this member before checking the booking's
            // existing pending attempts. Booking creation uses the same stable member row lock.
            Integer lockedMemberId = member.getId();
            member = userRepository.findByIdForBookingUpdate(lockedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + lockedMemberId));
            if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "PT booking payment requires an ACTIVE MEMBER account");
            }
        }

        // The upgrade quote checks pending payments itself and must be stored with the payment.
        MembershipUpgradeService.Quote upgradeQuote = request.getPurpose() == PaymentPurpose.UPGRADE
                ? membershipUpgradeService.quote(member, request.getReferenceId()) : null;
        BigDecimal amount = upgradeQuote != null ? upgradeQuote.amount() : resolveAmountAndValidate(request, member);
        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP
                && paymentRepository.existsByMember_IdAndPurposeAndReferenceIdAndStatus(
                        member.getId(), PaymentPurpose.MEMBERSHIP, request.getReferenceId(), PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending membership payment already exists for this member and plan");
        }
        if (request.getPurpose() == PaymentPurpose.MEMBERSHIP
                && paymentRepository.existsByMember_IdAndPurposeInAndStatus(
                        member.getId(), List.of(PaymentPurpose.UPGRADE), PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending membership upgrade must be paid or cancelled before buying another plan");
        }
        if (request.getPurpose() == PaymentPurpose.PT_BOOKING
                && paymentRepository.existsByPurposeAndReferenceIdAndStatus(
                        PaymentPurpose.PT_BOOKING, request.getReferenceId(), PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending payment already exists for this PT booking; cancel it before retrying or changing method");
        }

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        payment.setPurpose(request.getPurpose());
        payment.setReferenceId(request.getReferenceId());
        payment.setAutoRenew(request.getPurpose() == PaymentPurpose.MEMBERSHIP
                ? Boolean.TRUE.equals(request.getAutoRenew()) : null);
        if (autoRenewSourceMembershipId != null) {
            payment.setNote(MembershipAutoRenewalService.NOTE_PREFIX + autoRenewSourceMembershipId);
        }
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment = paymentRepository.save(payment);
        if (upgradeQuote != null) {
            membershipUpgradeService.record(payment, upgradeQuote);
        }

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

        PendingAutoRenewalLockRow candidate = paymentRepository.findPaymentLockRow(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (MembershipAutoRenewalService.sourceMembershipId(candidate.note()) != null) {
            userRepository.findByIdForBookingUpdate(candidate.memberId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not a counter payment");
        }
        requirePending(payment);
        if (payment.getPurpose() == PaymentPurpose.UPGRADE && Boolean.TRUE.equals(request.getSuccess())) {
            membershipUpgradeService.requireOpenQuote(payment);
        }

        payment.setProcessedBy(actor);
        return toResponse(finalizePayment(payment, request.getSuccess(), request.getNote(),
                Boolean.TRUE.equals(request.getSuccess()) ? now() : null), null);
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
        Optional<PendingAutoRenewalLockRow> candidate = paymentRepository.findCallbackLockRow(callback.transactionCode());
        if (candidate.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        boolean autoRenewal = MembershipAutoRenewalService.sourceMembershipId(candidate.get().note()) != null;
        if (autoRenewal) {
            userRepository.findByIdForBookingUpdate(candidate.get().memberId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        }
        Optional<Payment> found = paymentRepository.findByTransactionCodeForUpdate(callback.transactionCode());
        if (found.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        Payment payment = found.get();
        if (payment.getMethod() != PaymentMethod.ONLINE) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE, null);
        }
        if (callback.amount() != null && payment.getAmount().compareTo(callback.amount()) != 0) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH, null);
        }
        if (autoRenewal && callback.success()
                && (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.CANCELLED)) {
            return settleAutoRenewalCallback(payment, callback);
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
        return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED, toResponse(
                finalizePayment(payment, callback.success(), callback.success() ? null : failureNote, null), null));
    }

    /** Huỷ payment đang PENDING (member huỷ của mình, hoặc staff huỷ). */
    @Transactional
    public PaymentResponse cancelPayment(Integer paymentId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (!isStaff(actor) && !payment.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own payments");
        }
        requirePending(payment);
        payment.setStatus(PaymentStatus.CANCELLED);
        Integer renewalSourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
        payment.setNote(renewalSourceId == null
                ? "Cancelled by " + actor.getRole().toUpperCase(Locale.ROOT)
                : MembershipAutoRenewalService.NOTE_PREFIX + renewalSourceId
                    + " cancelled by " + actor.getRole().toUpperCase(Locale.ROOT));
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
        return finalizePayment(payment, success, note, null);
    }

    private Payment finalizePayment(Payment payment, boolean success, String note, LocalDateTime verifiedPaidAt) {
        if (!success) {
            payment.setStatus(PaymentStatus.CANCELLED);
            Integer sourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
            payment.setNote(sourceId == null
                    ? (note != null && !note.isBlank() ? note : "Payment failed")
                    : MembershipAutoRenewalService.NOTE_PREFIX + sourceId + " | Payment failed: "
                            + (note == null || note.isBlank() ? "unspecified" : note));
            return paymentRepository.save(payment);
        }

        Optional<String> refundReason = switch (payment.getPurpose()) {
            case MEMBERSHIP -> {
                Integer sourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
                if (sourceId == null) {
                    membershipService.activateOrRenewFromPayment(payment);
                    yield Optional.empty();
                } else {
                    var settlement = membershipAutoRenewalService.validateVerifiedSettlement(
                            payment, verifiedPaidAt, now());
                    if (!settlement.eligible()) {
                        yield Optional.of(settlement.rejectionReason());
                    }
                    yield membershipService.activateAutoRenewalFromPayment(payment, sourceId);
                }
            }
            case COURSE_REGISTRATION -> courseRegistrationHandler.apply(payment);
            case PT_BOOKING -> ptBookingHandler.apply(payment);
            case UPGRADE -> membershipUpgradeService.apply(payment);
        };
        if (refundReason.isPresent()) {
            if (MembershipAutoRenewalService.sourceMembershipId(payment) != null) {
                return markAutoRenewalRefundRequired(payment, refundReason.get(),
                        providerPaymentTimeFromNote(note), verifiedPaidAt);
            }
            return markRefundRequired(payment, refundReason.get());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(verifiedPaidAt == null ? now() : verifiedPaidAt);
        Integer renewalSourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
        if (renewalSourceId != null && (note == null || note.isBlank()
                || !note.startsWith(MembershipAutoRenewalService.NOTE_PREFIX))) {
            payment.setNote(MembershipAutoRenewalService.NOTE_PREFIX + renewalSourceId
                    + " | verified paidAt=" + payment.getPaidAt()
                    + (note == null || note.isBlank() ? "" : " | " + note));
        } else if (note != null && !note.isBlank()) {
            payment.setNote(note);
        }
        return paymentRepository.save(payment);
    }

    private OnlineCallbackResult settleAutoRenewalCallback(Payment payment, GatewayCallback callback) {
        var settlement = membershipAutoRenewalService.validateVerifiedSettlement(
                payment, callback.verifiedPaymentTime(), now());
        if (!settlement.eligible()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markAutoRenewalRefundRequired(payment, settlement.rejectionReason(),
                            callback.verifiedPaymentTime(), settlement.paidAt()), null));
        }
        // An expiry cleanup may have run before the signed callback arrived. A timely verified
        // payment remains eligible; opt-out/manual cancellations are rejected by the validator.
        payment.setStatus(PaymentStatus.PENDING);
        Integer sourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
        String evidence = MembershipAutoRenewalService.NOTE_PREFIX + sourceId
                + " | VNPay PayDate=" + callback.verifiedPaymentTime();
        return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                toResponse(finalizePayment(payment, true, evidence, settlement.paidAt()), null));
    }

    private Payment markAutoRenewalRefundRequired(Payment payment, String reason,
            String providerPaymentTime, LocalDateTime parsedPaymentTime) {
        Integer sourceId = MembershipAutoRenewalService.sourceMembershipId(payment);
        String evidence;
        if (providerPaymentTime == null && parsedPaymentTime != null) {
            evidence = "Counter receipt time=" + parsedPaymentTime;
        } else {
            String payDateEvidence;
            if (providerPaymentTime == null || providerPaymentTime.isBlank()) {
                payDateEvidence = "missing";
            } else if (providerPaymentTime.matches("[0-9]{14}")) {
                payDateEvidence = providerPaymentTime;
            } else {
                String bounded = providerPaymentTime.replaceAll("[^A-Za-z0-9._-]", "?");
                payDateEvidence = "malformed:" + bounded.substring(0, Math.min(32, bounded.length()));
            }
            evidence = "VNPay PayDate=" + payDateEvidence;
        }
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        // Do not substitute callback arrival time when the provider timestamp is absent/unusable.
        payment.setPaidAt(parsedPaymentTime);
        payment.setNote(MembershipAutoRenewalService.NOTE_PREFIX + sourceId
                + " | " + evidence + " | REFUND_REQUIRED: " + reason);
        return paymentRepository.save(payment);
    }

    private String providerPaymentTimeFromNote(String note) {
        String marker = "VNPay PayDate=";
        if (note == null) return null;
        int start = note.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = note.indexOf(' ', start);
        return end < 0 ? note.substring(start) : note.substring(start, end);
    }

    private Payment markRefundRequired(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setPaidAt(now());
        payment.setNote(reason);
        return paymentRepository.save(payment);
    }

    /** Hàng đợi khoản cần hoàn tiền (mọi purpose), cũ nhất trước. */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getRefundRequiredPayments(int page, int size, Jwt jwt) {
        requireStaff(currentActor(jwt));
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        return paymentRepository.findByStatus(PaymentStatus.REFUND_REQUIRED,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id")))
                .map(payment -> toResponse(payment, null));
    }

    /** Nhân viên xác nhận đã trả tiền lại cho member: REFUND_REQUIRED -> REFUNDED. */
    @Transactional
    public PaymentResponse confirmRefund(Integer paymentId, String note, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalArgumentException("Only a REFUND_REQUIRED payment can be marked refunded: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(now());
        payment.setRefundedBy(actor);
        if (note != null && !note.isBlank()) {
            String combined = (payment.getNote() == null ? "" : payment.getNote() + " | ") + "Refund: " + note.trim();
            payment.setNote(combined.length() > 500 ? combined.substring(0, 500) : combined);
        }
        notificationService.createForUser(payment.getMember().getId(), "Đã hoàn tiền thanh toán #" + payment.getId(),
                "Trung tâm đã hoàn " + payment.getAmount().stripTrailingZeros().toPlainString()
                        + " VND cho khoản thanh toán #" + payment.getId() + ".");
        return toResponse(paymentRepository.save(payment), null);
    }

    /** View Payment History (của chính mình). */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyHistory(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        return paymentRepository.findByMemberIdOrderByCreatedAtDesc(actor.getId())
                .stream().map(p -> toResponse(p, null)).toList();
    }

    /**
     * Dashboard-only total of pending payments that can still be settled through their existing
     * purpose-specific workflow. This is a read-only check; it never expires registrations/bookings
     * or changes Payment status.
     */
    @Transactional(readOnly = true)
    public ActionablePendingPaymentSummaryResponse getMyActionablePendingSummary(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a MEMBER can view this summary");
        }
        LocalDateTime now = now();
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Payment> actionable = paymentRepository
                .findByMemberIdAndStatusOrderByCreatedAtDesc(actor.getId(), PaymentStatus.PENDING)
                .stream().filter(payment -> isActionablePendingPayment(payment, actor.getId(), now, today)).toList();
        BigDecimal total = actionable.stream().map(Payment::getAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ActionablePendingPaymentSummaryResponse(actionable.size(), total);
    }

    private boolean isActionablePendingPayment(Payment payment, Integer memberId,
            LocalDateTime now, LocalDate today) {
        if (payment.getPurpose() == null || payment.getReferenceId() == null
                || payment.getAmount() == null || payment.getAmount().signum() <= 0) return false;
        if (payment.getMethod() != PaymentMethod.COUNTER && payment.getMethod() != PaymentMethod.ONLINE) return false;
        // A VNPay URL is a short-lived capability. A pending Payment remains in history after that URL expires.
        if (payment.getMethod() == PaymentMethod.ONLINE
                && (onlinePaymentExpiryMinutes < 1 || payment.getCreatedAt() == null
                    || !payment.getCreatedAt().plusMinutes(onlinePaymentExpiryMinutes).isAfter(now))) return false;

        return switch (payment.getPurpose()) {
            case MEMBERSHIP -> membershipPlanRepository.findById(payment.getReferenceId()).isPresent();
            case COURSE_REGISTRATION -> courseRegistrationRepository.findById(payment.getReferenceId())
                    .filter(registration -> registration.getMember().getId().equals(memberId)
                            && registration.getStatus() == CourseRegistrationStatus.PENDING_PAYMENT
                            && registration.getPaymentDeadline() != null
                            && registration.getPaymentDeadline().isAfter(now)
                            && registration.getFinalPrice() != null
                            && registration.getFinalPrice().signum() > 0)
                    .isPresent();
            case PT_BOOKING -> personalTrainingBookingRepository.findById(payment.getReferenceId())
                    .filter(booking -> booking.getMember().getId().equals(memberId)
                            && booking.getStatus() == PersonalTrainingBookingStatus.PENDING_PAYMENT
                            && booking.getPaymentExpiresAt() != null
                            && booking.getPaymentExpiresAt().isAfter(now))
                    .isPresent();
            case UPGRADE -> membershipUpgradeRepository.findByPaymentId(payment.getId())
                    .filter(upgrade -> isOpenUpgrade(upgrade, memberId, now, today))
                    .isPresent();
        };
    }

    private boolean isOpenUpgrade(MembershipUpgrade upgrade, Integer memberId,
            LocalDateTime now, LocalDate today) {
        var source = upgrade.getSourceMembership();
        return source != null && source.getMember().getId().equals(memberId)
                && !now.isAfter(upgrade.getExpiresAt())
                && "ACTIVE".equalsIgnoreCase(source.getStatus())
                && !source.getStartDate().isAfter(today)
                && source.getEndDate().isAfter(today);
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
        return paymentRepository.findByStatusAndMethodAndPurposeIn(
                PaymentStatus.PENDING, PaymentMethod.COUNTER, List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE),
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

    @Transactional(readOnly = true)
    public Page<PendingPtPaymentResponse> getPendingPtCounterPayments(int page, int size, Jwt jwt) {
        requireStaff(currentActor(jwt));
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        // Expired or terminal booking holds are omitted: a pending payment is not authority
        // to confirm after the booking's 15-minute payment window has elapsed.
        return paymentRepository.findPendingPtPayments(now(), PageRequest.of(page, size));
    }

    // ---------------------------------------------------------------- helpers

    /** Số tiền luôn do server tính, không nhận từ client. */
    private BigDecimal resolveAmountAndValidate(CreatePaymentRequest request, User member) {
        return switch (request.getPurpose()) {
            case MEMBERSHIP -> {
                MembershipPlan plan = membershipPlanRepository.findById(request.getReferenceId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Membership plan not found with id: " + request.getReferenceId()));
                if (!"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
                    throw new IllegalArgumentException("Membership plan is not active");
                }
                yield plan.getPrice();
            }
            case COURSE_REGISTRATION -> courseRegistrationHandler.quote(member, request.getReferenceId());
            case PT_BOOKING -> ptBookingHandler.quote(member, request.getReferenceId());
            case UPGRADE -> throw new IllegalStateException("Upgrade amounts come from MembershipUpgradeService.quote");
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
                p.getMethod().name(), p.getPurpose().name(), p.getReferenceId(), p.getStatus().name(),
                p.getTransactionCode(), paymentUrl, p.getNote(), p.getCreatedAt(), p.getPaidAt(),
                p.getRefundedAt(), p.getRefundedBy() == null ? null : p.getRefundedBy().getName());
    }
}
