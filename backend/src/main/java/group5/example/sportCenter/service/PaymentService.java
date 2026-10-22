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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.PendingCoursePaymentResponse;
import group5.example.sportCenter.dto.RenewalCheckoutResponse;
import group5.example.sportCenter.dto.RenewalCheckoutAttemptResponse;
import group5.example.sportCenter.dto.SelectRenewalPaymentMethodRequest;
import group5.example.sportCenter.dto.MembershipUpgradeCheckoutResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.CheckoutProvider;
import group5.example.sportCenter.entity.PaymentCheckoutAttempt;
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
import group5.example.sportCenter.payment.VerifiedUpgradeCapture;
import group5.example.sportCenter.payment.VerifiedUpgradeFailure;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PaymentService {

    @PersistenceContext private EntityManager entityManager;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final PaymentCheckoutAttemptRepository paymentCheckoutAttemptRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipService membershipService;
    private final CourseRegistrationPaymentHandler courseRegistrationHandler;
    private final PtBookingPaymentHandler ptBookingHandler;
    private final PaymentGateway paymentGateway;
    private final RenewalSettlementService renewalSettlementService;
    private final MembershipUpgradeSettlementService upgradeSettlementService;
    private final MembershipUpgradeCheckoutService upgradeCheckoutService;
    private final group5.example.sportCenter.repository.MembershipUpgradeCheckoutAttemptRepository upgradeCheckoutAttemptRepository;
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
            RenewalSettlementService renewalSettlementService,
            MembershipUpgradeSettlementService upgradeSettlementService,
            MembershipUpgradeCheckoutService upgradeCheckoutService,
            group5.example.sportCenter.repository.MembershipUpgradeCheckoutAttemptRepository upgradeCheckoutAttemptRepository,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentCheckoutAttemptRepository = paymentCheckoutAttemptRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.membershipService = membershipService;
        this.courseRegistrationHandler = courseRegistrationHandler;
        this.ptBookingHandler = ptBookingHandler;
        this.paymentGateway = paymentGateway;
        this.renewalSettlementService = renewalSettlementService;
        this.upgradeSettlementService = upgradeSettlementService;
        this.upgradeCheckoutService = upgradeCheckoutService;
        this.upgradeCheckoutAttemptRepository = upgradeCheckoutAttemptRepository;
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
        if (request.getPurpose() == PaymentPurpose.UPGRADE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upgrade Payments must use the dedicated authenticated upgrade request flow");
        }
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
            membershipService.ordinaryPurchaseConflict(member.getId(), null,
                    LocalDate.now(clock.withZone(BUSINESS_ZONE))).ifPresent(reason -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
                    });
        }

        BigDecimal amount = resolveAmountAndValidate(request, member);

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

        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (snapshot.getPurpose() == PaymentPurpose.UPGRADE) {
            if (!Boolean.TRUE.equals(request.getSuccess())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upgrade counter confirmation must record receipt; failed/cancelled attempts cannot confirm funds");
            upgradeSettlementService.settleCounterReceipt(paymentId, actor, request.getNote());
            Payment settled=paymentRepository.findById(paymentId).orElseThrow();
            entityManager.refresh(settled);
            return toResponse(settled,null);
        }
        lockOrdinaryMembershipPurchaseMember(snapshot);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getPurpose() == PaymentPurpose.UPGRADE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade counter receipt path must be used");
        }
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
        if (callback.rawTransactionReference()!=null && (callback.rawTransactionReference().startsWith("UGP")
                || callback.rawTransactionReference().startsWith("UPG"))) {
            // UGP attempt dispatch is IPN-only; legacy UPG payment references have no persisted
            // checkout attempt and neither browser nor generic callback may settle them.
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        Payment snapshot = paymentRepository.findByTransactionCode(callback.transactionCode()).orElse(null);
        if (snapshot == null) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        lockOrdinaryMembershipPurchaseMember(snapshot);
        Optional<Payment> found = paymentRepository.findByTransactionCodeForUpdate(callback.transactionCode());
        if (found.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        Payment payment = found.get();
        if (payment.getMethod() != PaymentMethod.ONLINE) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE, null);
        }
        if (payment.getPurpose() == PaymentPurpose.UPGRADE && payment.getStatus() == PaymentStatus.PENDING) {
            if (callback.success()) {
                // C2.5A never issues a checkout URL. If trusted provider evidence nevertheless
                // arrives, fail closed and retain the captured-funds reconciliation state.
                return new OnlineCallbackResult(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,
                        toResponse(markRefundRequired(payment,
                                "Verified Upgrade capture cannot be applied before C2.5B; manual reconciliation is required"), null));
            }
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(finalizePayment(payment, false,
                            callback.failureReason() == null ? "Upgrade payment failed" : callback.failureReason()), null));
        }
        if (callback.success() && payment.getRenewalMembership() != null
                && callback.amount() != null && payment.getAmount().compareTo(callback.amount()) != 0
                && payment.getStatus() == PaymentStatus.PENDING) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markRefundRequired(payment,
                            "Verified renewal callback amount mismatch requires manual reconciliation; Stage C settlement is unavailable"), null));
        }
        if (callback.success() && payment.getPurpose() == PaymentPurpose.MEMBERSHIP
                && payment.getRenewalMembership() == null && callback.amount() != null
                && payment.getAmount().compareTo(callback.amount()) != 0
                && payment.getStatus() == PaymentStatus.PENDING) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    toResponse(markRefundRequired(payment,
                            "Verified Membership purchase capture amount differs from the server amount; manual reconciliation is required"), null));
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

    /** Dispatches signed RNL IPNs to attempt-scoped renewal settlement. TXN callbacks retain the legacy path. */
    @Transactional
    public OnlineCallbackResult processVnpayIpn(Map<String, String> params) {
        String rawReference = params.get("vnp_TxnRef");
        if (rawReference != null && rawReference.startsWith("UGP")) {
            Optional<GatewayCallback> verified=paymentGateway.verifyCallback(params);
            if (verified.isEmpty()) return new OnlineCallbackResult(OnlineCallbackResult.Status.INVALID_SIGNATURE,null);
            GatewayCallback callback=verified.get();
            if (!rawReference.matches("UGP[0-9A-F]{32}") || !rawReference.equals(callback.rawTransactionReference())
                    || !rawReference.equals(callback.transactionCode()))
                return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND,null);
            return processVerifiedUpgradeIpn(callback);
        }
        if (rawReference == null || !rawReference.startsWith("RNL")) {
            return processOnlineCallback(params);
        }
        Optional<GatewayCallback> verified = paymentGateway.verifyCallback(params);
        if (verified.isEmpty()) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.INVALID_SIGNATURE, null);
        }
        GatewayCallback callback = verified.get();
        String reference = callback.rawTransactionReference();
        if (reference == null || !reference.matches("RNL[0-9a-fA-F]{32}")
                || !reference.equals(callback.transactionCode())) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        PaymentCheckoutAttempt attempt = paymentCheckoutAttemptRepository.findByTransactionReference(reference).orElse(null);
        if (attempt == null) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND, null);
        }
        Payment payment = attempt.getPayment();
        if (attempt.getProvider() != CheckoutProvider.VNPAY || payment == null
                || payment.getRenewalMembership() == null || payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || payment.getMethod() != PaymentMethod.ONLINE) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE, null);
        }
        boolean amountMatches = callback.amount() != null && attempt.getAmount() != null
                && payment.getAmount() != null && callback.amount().compareTo(attempt.getAmount()) == 0
                && callback.amount().compareTo(payment.getAmount()) == 0;
        if (!callback.success() && !amountMatches) {
            return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH, null);
        }

        RenewalSettlementResult settlement;
        if (callback.success()) {
            settlement = renewalSettlementService.settleVerifiedOnlineCapture(new VerifiedRenewalCapture(
                    reference, callback.providerTransactionId(), callback.amount(),
                    callback.responseCode() + "/" + callback.transactionStatus()));
            if (!amountMatches) {
                return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH, null);
            }
        } else {
            settlement = renewalSettlementService.recordVerifiedOnlineFailure(reference,
                    callback.providerTransactionId(), callback.amount(),
                    callback.responseCode() + "/" + callback.transactionStatus());
        }
        return switch (settlement.outcome()) {
            case APPLIED -> new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED, null);
            case ALREADY_APPLIED -> new OnlineCallbackResult(OnlineCallbackResult.Status.ALREADY_PROCESSED, null);
            case RECONCILIATION_REQUIRED -> new OnlineCallbackResult(
                    OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, null);
        };
    }

    private OnlineCallbackResult processVerifiedUpgradeIpn(GatewayCallback callback) {
        String reference=callback.rawTransactionReference();
        group5.example.sportCenter.entity.MembershipUpgradeCheckoutAttempt attempt =
                upgradeCheckoutAttemptRepository.findByTransactionReference(reference).orElse(null);
        if(attempt==null) return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND,null);
        Payment payment=attempt.getPayment();
        if(attempt.getProvider()!=CheckoutProvider.VNPAY || payment==null
                || payment.getPurpose()!=PaymentPurpose.UPGRADE || payment.getMethod()!=PaymentMethod.ONLINE)
            return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE,null);
        boolean matches=callback.amount()!=null && attempt.getAmount()!=null && payment.getAmount()!=null
                && callback.amount().compareTo(attempt.getAmount())==0 && callback.amount().compareTo(payment.getAmount())==0;
        if(callback.success()) {
            VerifiedUpgradeCapture capture=callback.verifiedUpgradeCapture();
            if(capture==null) return new OnlineCallbackResult(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,null);
            MembershipUpgradeSettlementResult result=upgradeSettlementService.settleVerifiedOnlineCapture(capture);
            if(!matches) return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH,null);
            return switch(result.outcome()) {
                case APPLIED -> new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,null);
                case ALREADY_APPLIED -> new OnlineCallbackResult(OnlineCallbackResult.Status.ALREADY_PROCESSED,null);
                case RECONCILIATION_REQUIRED -> new OnlineCallbackResult(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,null);
                case NOT_APPLICABLE -> new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND,null);
            };
        }
        VerifiedUpgradeFailure failure=callback.verifiedUpgradeFailure();
        if(failure==null) return new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND,null);
        MembershipUpgradeSettlementResult result=upgradeSettlementService.recordVerifiedOnlineFailure(failure);
        if(!matches) return new OnlineCallbackResult(OnlineCallbackResult.Status.AMOUNT_MISMATCH,null);
        return switch(result.outcome()) {
            case APPLIED, ALREADY_APPLIED -> new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,null);
            case RECONCILIATION_REQUIRED -> new OnlineCallbackResult(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,null);
            case NOT_APPLICABLE -> new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_FOUND,null);
        };
    }

    @Transactional
    public MembershipUpgradeCheckoutResponse createUpgradeCheckout(Integer paymentId, Jwt jwt, String clientIp) {
        return upgradeCheckoutService.issue(paymentId,jwt,clientIp);
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
        if (!payment.getMember().getId().equals(actor.getId())
                || !source.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only select a payment method for your own renewal request");
        }
        if (!attempts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Renewal payment options cannot be changed after a checkout attempt has been issued");
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
        // Method and next-period Auto Renew preference remain editable only before the first
        // checkout attempt. The preference is an explicit member choice, not charge authority.
        payment.setMethod(request.getMethod());
        if (request.getRenewalAutoRenewChoice() != null) {
            payment.setRenewalAutoRenewChoice(request.getRenewalAutoRenewChoice());
        }
        payment.setNote("Renewal payment options selected");
        return toResponse(paymentRepository.save(payment), null);
    }

    /** Issues a durable, member-owned VNPay checkout attempt for a pending renewal. */
    @Transactional
    public RenewalCheckoutResponse createRenewalCheckout(Integer paymentId, Jwt callerJwt, String clientIp) {
        if (!paymentGateway.supportsRenewalCheckout()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPay renewal checkout is not configured");
        }
        User actor = currentActor(callerJwt);
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owning MEMBER can start renewal checkout");
        }
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        Membership snapshotSource = snapshot.getRenewalMembership();
        if (snapshotSource == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not a renewal request");
        }

        // Match renewal settlement's serialization order: member, source Membership, Payment, then attempts.
        userRepository.findByIdForBookingUpdate(snapshot.getMember().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        Membership source = membershipService.lockRenewalSource(snapshotSource.getId());
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        List<PaymentCheckoutAttempt> attempts = paymentCheckoutAttemptRepository
                .findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(paymentId);

        if (!payment.getMember().getId().equals(actor.getId())
                || !source.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay for your own renewal");
        }
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || payment.getRenewalMembership() == null
                || !payment.getRenewalMembership().getId().equals(source.getId())
                || payment.getStatus() != PaymentStatus.PENDING
                || payment.getMethod() != PaymentMethod.ONLINE
                || source.getStatus() == null || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || source.getStartDate() == null || source.getStartDate().isAfter(today)
                || source.getEndDate() == null || !source.getEndDate().isAfter(today)
                || source.getEndDate().isAfter(today.plusDays(3))
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !membershipService.isFinalPaidRenewalPeriod(source)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Renewal payment is no longer eligible for checkout");
        }
        if (payment.getRenewalAutoRenewChoice() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Select Auto Renew for the next membership period before checkout");
        }
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0
                || payment.getMonthlyPriceSnapshot() == null
                || payment.getAmount().compareTo(payment.getMonthlyPriceSnapshot()) != 0
                || payment.getPurchasedMonths() == null || payment.getPurchasedMonths() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Renewal payment price snapshot is invalid");
        }

        LocalDateTime now = now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        PaymentCheckoutAttempt liveAttempt = attempts.stream()
                .filter(a -> a.getProvider() == CheckoutProvider.VNPAY)
                .filter(a -> a.getStatus() == CheckoutAttemptStatus.URL_ISSUED)
                .filter(a -> a.getExpiresAt() != null && a.getExpiresAt().isAfter(now))
                .max(java.util.Comparator.comparing(PaymentCheckoutAttempt::getCreatedAt))
                .orElse(null);
        if (liveAttempt != null) {
            // The signed URL is intentionally not stored (and cannot be reconstructed exactly:
            // its creation time, expiry, and client IP are part of the signed query).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A valid renewal checkout has already been issued; use the original checkout link or retry after it expires");
        }

        int expiryMinutes = paymentGateway.renewalCheckoutExpiryMinutes();
        if (expiryMinutes <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay checkout expiry is invalid");
        }
        LocalDateTime expiresAt = now.plusMinutes(expiryMinutes);
        String reference = "RNL" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        PaymentCheckoutAttempt attempt = new PaymentCheckoutAttempt();
        attempt.setPayment(payment);
        attempt.setProvider(CheckoutProvider.VNPAY);
        attempt.setTransactionReference(reference);
        attempt.setAmount(payment.getAmount());
        attempt.setCreatedAt(now);
        attempt.setExpiresAt(expiresAt);
        attempt.setStatus(CheckoutAttemptStatus.CREATED);
        attempt = paymentCheckoutAttemptRepository.saveAndFlush(attempt);
        String checkoutUrl = paymentGateway.createRenewalPaymentUrl(reference, payment.getAmount(), clientIp, now);
        attempt.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        paymentCheckoutAttemptRepository.save(attempt);
        return new RenewalCheckoutResponse(payment.getId(), attempt.getId(), reference,
                attempt.getAmount(), expiresAt, checkoutUrl);
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
                yield membershipService.activateOrRenewFromPayment(payment);
            }
            case COURSE_REGISTRATION -> courseRegistrationHandler.apply(payment);
            case PT_BOOKING -> ptBookingHandler.apply(payment);
            case UPGRADE -> Optional.of("Upgrade settlement is unavailable until C2.5B");
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

    /** Keeps ordinary Membership issuance and settlement on the same member-first lock order. */
    private void lockOrdinaryMembershipPurchaseMember(Payment payment) {
        if (payment.getPurpose() == PaymentPurpose.MEMBERSHIP && payment.getRenewalMembership() == null) {
            Integer memberId = payment.getMember().getId();
            userRepository.findByIdForBookingUpdate(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        }
    }

    /** View Payment History (của chính mình). */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyHistory(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        return toHistoryResponses(paymentRepository.findByMemberIdOrderByCreatedAtDesc(actor.getId()));
    }

    /** View Payment History của một member (ADMIN/RECEPTIONIST). */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getHistoryByMember(Integer memberId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberId);
        }
        return toHistoryResponses(paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId));
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
            case UPGRADE -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upgrade Payments must use the dedicated authenticated upgrade request flow");
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
        PaymentCheckoutAttempt latestAttempt = null;
        if (p.getRenewalMembership() != null) {
            List<PaymentCheckoutAttempt> attempts = paymentCheckoutAttemptRepository
                    .findByPayment_IdOrderByCreatedAtAscIdAsc(p.getId());
            if (!attempts.isEmpty()) latestAttempt = attempts.get(attempts.size() - 1);
        }
        return toResponse(p, paymentUrl, latestAttempt);
    }

    private List<PaymentResponse> toHistoryResponses(List<Payment> payments) {
        List<Integer> renewalIds = payments.stream()
                .filter(p -> p.getRenewalMembership() != null)
                .map(Payment::getId).toList();
        java.util.Map<Integer, PaymentCheckoutAttempt> latestAttempts = new java.util.HashMap<>();
        if (!renewalIds.isEmpty()) {
            // Repository ordering makes the first row per payment the deterministic newest attempt.
            for (PaymentCheckoutAttempt attempt : paymentCheckoutAttemptRepository
                    .findNewestFirstForPayments(renewalIds)) {
                latestAttempts.putIfAbsent(attempt.getPayment().getId(), attempt);
            }
        }
        return payments.stream().map(p -> toResponse(p, null, latestAttempts.get(p.getId()))).toList();
    }

    private PaymentResponse toResponse(Payment p, String paymentUrl, PaymentCheckoutAttempt latestAttempt) {
        boolean renewal = p.getRenewalMembership() != null;
        RenewalCheckoutAttemptResponse attemptResponse = latestAttempt == null ? null
                : new RenewalCheckoutAttemptResponse(latestAttempt.getId(), latestAttempt.getStatus().name(),
                        latestAttempt.getExpiresAt());
        return new PaymentResponse(
                p.getId(), p.getMember().getId(), p.getMember().getName(), p.getAmount(),
                p.getMethod() == null ? null : p.getMethod().name(), p.getPurpose().name(), p.getReferenceId(), p.getStatus().name(),
                p.getTransactionCode(), paymentUrl, p.getNote(), p.getCreatedAt(), p.getPaidAt(), p.getPurchasedMonths(),
                renewal, renewal ? p.getRenewalMembership().getId() : null,
                renewal ? p.getRenewalAutoRenewChoice() : null, renewal ? attemptResponse : null);
    }

    /**
     * Resolves a safe browser-return locator only from a valid signed VNPay payload and
     * a persisted renewal attempt. This method is read-only and never invokes settlement.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> resolveVerifiedRenewalReturnPaymentId(Map<String, String> params) {
        String reference = params.get("vnp_TxnRef");
        if (reference == null || !reference.matches("RNL[0-9A-F]{32}")) return Optional.empty();
        GatewayCallback callback = paymentGateway.verifyCallback(params).orElse(null);
        if (callback == null || !reference.equals(callback.rawTransactionReference())
                || !reference.equals(callback.transactionCode()) || callback.amount() == null) {
            return Optional.empty();
        }
        PaymentCheckoutAttempt attempt = paymentCheckoutAttemptRepository.findByTransactionReference(reference).orElse(null);
        if (attempt == null || attempt.getProvider() != CheckoutProvider.VNPAY
                || attempt.getPayment() == null || attempt.getPayment().getRenewalMembership() == null
                || attempt.getAmount() == null || attempt.getPayment().getAmount() == null
                || callback.amount().compareTo(attempt.getAmount()) != 0
                || callback.amount().compareTo(attempt.getPayment().getAmount()) != 0) {
            return Optional.empty();
        }
        return Optional.of(attempt.getPayment().getId());
    }
}
