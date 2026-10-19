package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.CourseRegistrationPaymentHandler;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.payment.PtBookingPaymentHandler;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipService membershipService;
    private final CourseRegistrationPaymentHandler courseRegistrationHandler;
    private final PtBookingPaymentHandler ptBookingHandler;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipService membershipService,
            CourseRegistrationPaymentHandler courseRegistrationHandler,
            PtBookingPaymentHandler ptBookingHandler,
            PaymentGateway paymentGateway,
            Clock clock) {
        this.paymentRepository = paymentRepository;
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
                && paymentRepository.existsByMember_IdAndPurposeAndReferenceIdAndStatus(
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
        payment.setAutoRenew(request.getPurpose() == PaymentPurpose.MEMBERSHIP
                ? Boolean.TRUE.equals(request.getAutoRenew()) : null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment = paymentRepository.save(payment);

        String url = request.getMethod() == PaymentMethod.ONLINE
                ? paymentGateway.createPaymentUrl(payment.getTransactionCode(), amount)
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
        if (payment.getMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not a counter payment");
        }
        requirePending(payment);

        payment.setProcessedBy(actor);
        return toResponse(finalizePayment(payment, request.getSuccess(), request.getNote()), null);
    }

    /** Nhánh "Online": callback từ cổng thanh toán (endpoint public, bảo vệ bằng chữ ký). */
    @Transactional
    public PaymentResponse handleOnlineCallback(String transactionCode, boolean success, String signature) {
        if (!paymentGateway.verifyCallback(transactionCode, success, signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid payment callback signature");
        }
        Payment payment = paymentRepository.findByTransactionCodeForUpdate(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getMethod() != PaymentMethod.ONLINE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not an online payment");
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED && success) {
            // Member đã huỷ nhưng cổng vẫn báo trừ tiền: không áp dụng, chuyển sang hoàn tiền.
            return toResponse(markRefundRequired(payment, "Payment succeeded after it was cancelled"), null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return toResponse(payment, null); // callback lặp lại: bỏ qua (idempotent)
        }
        return toResponse(finalizePayment(payment, success, success ? null : "Online payment failed"), null);
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
        payment.setNote("Cancelled by " + actor.getRole().toUpperCase(Locale.ROOT));
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

        Optional<String> refundReason = switch (payment.getPurpose()) {
            case MEMBERSHIP -> {
                membershipService.activateOrRenewFromPayment(
                        payment.getMember().getId(), payment.getReferenceId(),
                        Boolean.TRUE.equals(payment.getAutoRenew()));
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
                p.getTransactionCode(), paymentUrl, p.getNote(), p.getCreatedAt(), p.getPaidAt());
    }
}
