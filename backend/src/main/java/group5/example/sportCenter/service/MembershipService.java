package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;

import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.MembershipResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipCancellationRequestRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipService {

    public static final String CANCELLED = "CANCELLED";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES = List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final MembershipUpgradeRepository membershipUpgradeRepository;
    private final MembershipCancellationRequestRepository cancellationRequestRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipAutoRenewalService autoRenewalService;
    private final MembershipRenewalLifecycleService renewalLifecycleService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogs;
    private final Clock clock;
    private final EntityManager entityManager;

    public MembershipService(
            MembershipRepository membershipRepository,
            MembershipUpgradeRepository membershipUpgradeRepository,
            MembershipCancellationRequestRepository cancellationRequestRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipAutoRenewalService autoRenewalService,
            MembershipRenewalLifecycleService renewalLifecycleService,
            PaymentRepository paymentRepository,
            NotificationService notificationService,
            AuditLogService auditLogs,
            Clock clock,
            EntityManager entityManager) {
        this.membershipRepository = membershipRepository;
        this.membershipUpgradeRepository = membershipUpgradeRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.autoRenewalService = autoRenewalService;
        this.renewalLifecycleService = renewalLifecycleService;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.auditLogs = auditLogs;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getAllMemberships(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        return membershipRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MembershipResponse getMembershipById(Integer id, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        requireOwnMembershipOrStaff(actor, membership.getMember().getId());
        return toResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getMembershipsByMemberId(Integer memberId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireOwnMembershipOrStaff(actor, memberId);

        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberId);
        }

        return membershipRepository.findByMemberId(memberId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MembershipResponse createMembership(CreateMembershipRequest request, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        // MEMBER phải mua qua POST /api/payments (purpose MEMBERSHIP); endpoint này chỉ để staff gán thủ công.
        String actorRole = actor.getRole().toUpperCase(Locale.ROOT);
        if (!actorRole.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Membership assignment requires ADMIN; front-desk purchases must use /api/payments");
        }
        throw new ResponseStatusException(HttpStatus.GONE,
                "Direct Membership assignment is disabled; activate Memberships through successful Payment settlement");
    }

    /**
     * Gọi từ PaymentService khi thanh toán MEMBERSHIP thành công (chạy trong transaction của payment).
     * Chưa có gói còn hiệu lực -> kích hoạt từ hôm nay. Đang có gói -> gia hạn bằng một gói mới
     * bắt đầu đúng ngày gói hiện tại kết thúc (endDate là mốc loại trừ), không sửa gói cũ.
     */
    @Transactional
    public Optional<String> activateOrRenewFromPayment(Payment payment) {
        if (MembershipAutoRenewalService.sourceMembershipId(payment) != null) {
            return Optional.of("Legacy auto-renew Payment cannot use ordinary purchase activation");
        }
        if (payment.getPurpose() != PaymentPurpose.MEMBERSHIP || !payment.hasCompleteMembershipQuote()) {
            return Optional.of("Membership Payment has no complete captured quote; manual reconciliation is required");
        }
        if (payment.getAmount() == null
                || payment.getAmount().compareTo(payment.getMembershipPlanPriceSnapshot()) != 0) {
            return Optional.of("Membership Payment amount does not match its captured quote");
        }
        if (payment.getAmount().signum() <= 0) {
            return Optional.of("Membership Payment has no positive paid-value basis; manual reconciliation is required");
        }
        Integer memberId = payment.getMember().getId();
        Integer planId = payment.getReferenceId();
        // Khoá dòng user để hai lần gia hạn song song không cùng tính từ một endDate.
        User member = userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (hasPurchaseBlockingMembership(memberId, today)) {
            return Optional.of("Member has an effective or future Membership; successful Payment requires reconciliation");
        }
        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + planId));

        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(today);
        membership.setEndDate(today.plusMonths(payment.getMembershipDurationMonthsSnapshot()));
        membership.setAutoRenew(Boolean.TRUE.equals(payment.getAutoRenew()));
        membership.setStatus("ACTIVE");
        membership.setPurchasePayment(payment);
        copySnapshot(payment, membership, "PURCHASE", "PURCHASE_PAYMENT");
        membershipRepository.save(membership);
        return Optional.empty();
    }

    /** Activate a marked Auto-renew payment from its source period, not callback arrival date. */
    @Transactional
    public Optional<String> activateAutoRenewalFromPayment(Payment payment, Integer sourceMembershipId) {
        if (!payment.hasCompleteMembershipQuote()
                || payment.getAmount() == null
                || payment.getAmount().compareTo(payment.getMembershipPlanPriceSnapshot()) != 0
                || payment.getAmount().signum() <= 0) {
            return Optional.of("Legacy auto-renew Payment has no complete captured quote; manual reconciliation is required");
        }
        Integer memberId = payment.getMember().getId();
        User member = userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        Membership source = membershipRepository.findById(sourceMembershipId).orElse(null);
        if (source == null || !source.getMember().getId().equals(memberId)
                || !source.getPlan().getId().equals(payment.getReferenceId())) {
            return Optional.of("Auto-renew source membership does not match the payment");
        }
        if (!Boolean.TRUE.equals(source.getAutoRenew())
                || (!"ACTIVE".equalsIgnoreCase(source.getStatus())
                    && !"EXPIRED".equalsIgnoreCase(source.getStatus()))) {
            return Optional.of("Auto-renew was disabled or the source membership is no longer eligible");
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                memberId, "ACTIVE", source.getEndDate())) {
            return Optional.of("A later membership period already exists");
        }

        MembershipPlan plan = membershipPlanRepository.findById(payment.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership plan not found with id: " + payment.getReferenceId()));
        Membership renewal = new Membership();
        renewal.setMember(member);
        renewal.setPlan(plan);
        renewal.setStartDate(source.getEndDate());
        renewal.setEndDate(source.getEndDate().plusMonths(payment.getMembershipDurationMonthsSnapshot()));
        renewal.setAutoRenew(true);
        renewal.setStatus("ACTIVE");
        renewal.setPurchasePayment(payment);
        copySnapshot(payment, renewal, "RENEWAL", "RENEWAL_PAYMENT");
        membershipRepository.save(renewal);
        return Optional.empty();
    }

    private boolean hasPurchaseBlockingMembership(Integer memberId, LocalDate today) {
        return membershipRepository.existsByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                    memberId, "ACTIVE", today, today)
                || membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThan(
                    memberId, "ACTIVE", today);
    }

    private void copySnapshot(Payment payment, Membership membership, String source, String paidValueSource) {
        membership.setBenefitSnapshotStatus("COMPLETE");
        membership.setBenefitSnapshotSource(source);
        membership.setPlanNameSnapshot(payment.getMembershipPlanNameSnapshot());
        membership.setPlanDescriptionSnapshot(payment.getMembershipPlanDescriptionSnapshot());
        membership.setPlanPriceSnapshot(payment.getMembershipPlanPriceSnapshot());
        membership.setPlanDurationMonthsSnapshot(payment.getMembershipDurationMonthsSnapshot());
        membership.setCourseDiscountPercentSnapshot(payment.getMembershipCourseDiscountPercentSnapshot());
        membership.setPtDiscountPercentSnapshot(payment.getMembershipPtDiscountPercentSnapshot());
        membership.setAllowPtPlanSnapshot(payment.getMembershipAllowPtPlanSnapshot());
        membership.setAllowOneOnOneSnapshot(payment.getMembershipAllowOneOnOneSnapshot());
        membership.setPaidValueBasisStatus("VERIFIED");
        membership.setPaidValueBasisAmount(payment.getAmount());
        membership.setPaidValueBasisSource(paidValueSource);
    }

    /**
     * Huỷ gói trực tiếp, chỉ ADMIN. Gói chưa bắt đầu: khoản đã mua gói chuyển REFUND_REQUIRED để lễ tân
     * hoàn 100%. Gói đang chạy: bắt buộc lý do, không tự hoàn tiền.
     * Member gửi yêu cầu huỷ và lễ tân chỉ duyệt/từ chối yêu cầu đó (MembershipCancellationRequestService).
     * Chỉ huỷ được gói cuối trong chuỗi, để các gói còn lại không bị hở ngày.
     */
    @Transactional
    public MembershipResponse cancelMembership(Integer id, String reason, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        if (!role.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only ADMIN can cancel a membership directly; members send a cancellation request for staff to review");
        }
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        Integer memberId = membership.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        membership = membershipRepository.findByIdForRenewalUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        entityManager.refresh(membership);
        LocalDate today = lockAndRequireLastActive(membership);
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cancellation reason is required");
        }
        if (trimmedReason.length() > 250) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason must be at most 250 characters");
        }
        boolean upcoming = membership.getStartDate().isAfter(today);

        ExceptionalRefundPlan refundPlan = exceptionalCancellationPayments(membership);

        // An unpaid auto-renewal for this membership is pointless once it is cancelled.
        autoRenewalService.cancelPendingFor(membership);
        if (!upcoming && paymentRepository.existsByMember_IdAndPurposeInAndStatus(memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finish or cancel the member's pending membership payment before cancelling the running membership");
        }
        // An open Intent without an active attempt must not survive cancellation of its source period.
        renewalLifecycleService.closeForMembershipCancelled(membership.getId());

        // A direct cancellation answers the member's pending request, if any.
        cancellationRequestRepository.findFirstByMembershipIdAndStatus(id, MembershipCancellationRequestStatus.PENDING)
                .ifPresent(request -> {
                    request.setStatus(MembershipCancellationRequestStatus.APPROVED);
                    request.setDecidedBy(actor);
                    request.setDecidedAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
                    request.setDecisionNote("Quản trị viên đã huỷ gói trực tiếp");
                    cancellationRequestRepository.save(request);
                });
        return toResponse(applyExceptionalCancellation(membership, trimmedReason, refundPlan, actor));
    }

    /**
     * Huỷ gói khi nhân viên duyệt yêu cầu huỷ của member (chạy trong transaction của yêu cầu).
     * Gói phải vẫn chưa bắt đầu và vẫn là gói cuối trong chuỗi, nếu không trả 409.
     */
    @Transactional
    public Membership cancelForApprovedRequest(Membership membership, String reason) {
        LocalDate today = lockAndRequireLastActive(membership);
        if (!membership.getStartDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The membership has already started; the cancellation request can no longer be approved");
        }
        autoRenewalService.cancelPendingFor(membership);
        return applyCancellation(membership, reason, true, null, false);
    }

    /** Khoá dòng member (giống mua gói, nâng cấp, job tự gia hạn), rồi kiểm tra gói còn ACTIVE và là gói cuối. */
    private LocalDate lockAndRequireLastActive(Membership membership) {
        Integer memberId = membership.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (!"ACTIVE".equals(membership.getStatus()) || !membership.getEndDate().isAfter(today)) {
            throw new IllegalArgumentException("Only an active or upcoming membership can be cancelled");
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(memberId, "ACTIVE", membership.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cancel the membership that starts after this one first");
        }
        return today;
    }

    private ExceptionalRefundPlan exceptionalCancellationPayments(Membership target) {
        Integer memberId = target.getMember().getId();
        Membership cursor = target;
        Set<Integer> visitedMemberships = new HashSet<>();
        Set<Integer> seenPayments = new HashSet<>();
        List<Payment> lineage = new ArrayList<>();
        Integer cycleRootId;

        while (true) {
            if (!visitedMemberships.add(cursor.getId())) {
                throw financialLineageConflict("Membership lineage contains a cycle");
            }
            if (cursor.getMember() == null || !memberId.equals(cursor.getMember().getId())) {
                throw financialLineageConflict("Membership lineage crosses member accounts");
            }
            List<MembershipUpgrade> incoming = membershipUpgradeRepository
                    .findAllByResultMembership_Id(cursor.getId());
            if (incoming.size() > 1) {
                throw financialLineageConflict("More than one Upgrade points to this Membership");
            }
            if (incoming.isEmpty()) {
                cycleRootId = cursor.getId();
                validateMembershipPayment(cursor, memberId);
                addEligiblePayment(lineage, seenPayments, cursor.getPurchasePayment(), memberId);
                validateRenewalBoundary(cursor, cursor.getPurchasePayment());
                break;
            }
            MembershipUpgrade upgrade = incoming.get(0);
            Membership source = upgrade.getSourceMembership();
            Payment upgradePayment = upgrade.getPayment();
            if (source == null || source.getId() == null || source.getId().equals(cursor.getId())
                    || upgrade.getResultMembership() == null
                    || !cursor.getId().equals(upgrade.getResultMembership().getId())
                    || source.getMember() == null || !memberId.equals(source.getMember().getId())
                    || upgrade.getTargetPlan() == null || cursor.getPlan() == null
                    || !upgrade.getTargetPlan().getId().equals(cursor.getPlan().getId())
                    || cursor.getPurchasePayment() == null || upgradePayment == null
                    || !cursor.getPurchasePayment().getId().equals(upgradePayment.getId())
                    || !"UPGRADED".equalsIgnoreCase(source.getStatus())
                    || upgradePayment.getPurpose() != PaymentPurpose.UPGRADE
                    || !memberId.equals(upgradePayment.getMember().getId())
                    || !upgrade.getTargetPlan().getId().equals(upgradePayment.getReferenceId())
                    || upgradePayment.getRenewalIntent() != null
                    || !upgrade.hasCompleteFrozenQuote()) {
                throw financialLineageConflict("Upgrade financial lineage is incomplete or inconsistent");
            }
            addEligiblePayment(lineage, seenPayments, upgradePayment, memberId);
            cursor = source;
        }

        // Lock all financial rows in a stable order after the per-member lock. Re-read each status
        // under lock so concurrent refund confirmation cannot race this obligation creation.
        List<Payment> locked = lineage.stream().map(Payment::getId).sorted()
                .map(paymentId -> paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() -> financialLineageConflict("A linked Payment disappeared")))
                .toList();
        for (Payment payment : locked) validateRefundablePayment(payment, memberId);
        java.math.BigDecimal paidTotal = locked.stream().map(Payment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal refundDue = locked.stream().filter(p -> p.getStatus() != PaymentStatus.REFUNDED)
                .map(Payment::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return new ExceptionalRefundPlan(cycleRootId, locked, paidTotal, refundDue);
    }

    private void validateMembershipPayment(Membership root, Integer memberId) {
        Payment payment = root.getPurchasePayment();
        if (payment == null || payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || !memberId.equals(payment.getMember().getId()) || root.getPlan() == null
                || !root.getPlan().getId().equals(payment.getReferenceId())) {
            throw financialLineageConflict("Membership cycle has no matching purchase Payment");
        }
    }

    private void validateRenewalBoundary(Membership root, Payment payment) {
        var intent = root.getRenewalIntent();
        if (intent == null) {
            if (payment.getRenewalIntent() != null || payment.getRenewalSettlementAcceptedAt() != null) {
                throw financialLineageConflict("Renewal Payment is not linked to the entitlement cycle");
            }
            return;
        }
        if (payment.getRenewalIntent() == null || !intent.getId().equals(payment.getRenewalIntent().getId())
                || intent.getSettledPayment() == null || !payment.getId().equals(intent.getSettledPayment().getId())
                || intent.getIntentStatus() != group5.example.sportCenter.entity.RenewalIntentStatus.SETTLED
                || payment.getRenewalSettlementAcceptedAt() == null
                || intent.getSourceMembership() == null
                || !intent.getSourceMembership().getMember().getId().equals(root.getMember().getId())
                || intent.getSourceMembership().getEndDate() == null
                || !intent.getSourceMembership().getEndDate().equals(root.getStartDate())) {
            throw financialLineageConflict("Renewal boundary is not backed by an accepted settlement");
        }
    }

    private void addEligiblePayment(List<Payment> lineage, Set<Integer> seen, Payment payment, Integer memberId) {
        if (payment == null || payment.getId() == null || !seen.add(payment.getId())
                || payment.getPurpose() == null || !MEMBERSHIP_PURPOSES.contains(payment.getPurpose())
                || payment.getMember() == null || !memberId.equals(payment.getMember().getId())
                || payment.getAmount() == null || payment.getAmount().signum() <= 0
                || payment.getPaidAt() == null) {
            throw financialLineageConflict("Payment evidence is missing, duplicated, unpaid, or belongs to another member");
        }
        lineage.add(payment);
    }

    private void validateRefundablePayment(Payment payment, Integer memberId) {
        if (!memberId.equals(payment.getMember().getId()) || payment.getAmount() == null
                || payment.getAmount().signum() <= 0 || payment.getPaidAt() == null) {
            throw financialLineageConflict("Linked Payment lacks actual paid amount evidence");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) return;
        if (payment.getStatus() == PaymentStatus.REFUND_REQUIRED) return;
        if (payment.getStatus() == PaymentStatus.REFUNDED && payment.getRefundedAt() != null) return;
        throw financialLineageConflict("A linked Payment is not successful or has an inconsistent refund status");
    }

    private ResponseStatusException financialLineageConflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể xác định an toàn các khoản đã thanh toán thuộc chu kỳ Membership hiện tại: " + detail
                + ". Membership chưa bị hủy và không tạo yêu cầu hoàn tiền.");
    }

    private record ExceptionalRefundPlan(Integer cycleRootId, List<Payment> payments,
            java.math.BigDecimal paidTotal, java.math.BigDecimal refundDue) { }

    private Membership applyExceptionalCancellation(Membership membership, String reason,
            ExceptionalRefundPlan plan, User actor) {
        membership.setStatus(CANCELLED);
        membership.setCancelledAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        membership.setCancelReason(reason);
        Map<Integer, PaymentStatus> previousStatuses = new HashMap<>();
        for (Payment payment : plan.payments()) {
            previousStatuses.put(payment.getId(), payment.getStatus());
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.setStatus(PaymentStatus.REFUND_REQUIRED);
                paymentRepository.save(payment);
            }
        }
        Membership saved = membershipRepository.saveAndFlush(membership);
        String summary = "actorUserId=" + actor.getId() + ";actorRole=ADMIN;status=ACTIVE->CANCELLED;cycleRootMembershipId=" + plan.cycleRootId()
                + ";eligiblePaymentCount=" + plan.payments().size()
                + ";eligiblePaidAmount=" + plan.paidTotal().stripTrailingZeros().toPlainString()
                + ";refundAmountDue=" + plan.refundDue().stripTrailingZeros().toPlainString()
                + ";refundDisposition=REFUND_REQUIRED_PENDING_DISBURSEMENT_CONFIRMATION;reason=" + reason;
        auditLogs.record("CANCEL", "MEMBERSHIP", saved.getId(), summary);
        for (Payment payment : plan.payments()) {
            PaymentStatus before = previousStatuses.get(payment.getId());
            PaymentStatus after = payment.getStatus();
            String disposition = switch (before) {
                case SUCCESS -> "NEW_REFUND_OBLIGATION_PENDING_DISBURSEMENT";
                case REFUND_REQUIRED -> "EXISTING_REFUND_OBLIGATION_PRESERVED";
                case REFUNDED -> "ALREADY_REFUNDED_NO_DUPLICATE_OBLIGATION";
                default -> throw financialLineageConflict("Unexpected Payment state after locked validation");
            };
            String action = before == PaymentStatus.SUCCESS
                    ? "EXCEPTIONAL_CANCEL_REFUND" : "EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE";
            auditLogs.record(action, "PAYMENT", payment.getId(),
                    "membershipId=" + saved.getId() + ";cycleRootMembershipId=" + plan.cycleRootId()
                            + ";actualPaidAmount=" + payment.getAmount().stripTrailingZeros().toPlainString()
                            + ";status=" + before + "->" + after + ";disposition=" + disposition);
        }
        notificationService.createForUser(saved.getMember().getId(), "Membership đã được huỷ",
                "Membership #" + saved.getId() + " đã được huỷ. Yêu cầu hoàn 100% tổng số tiền thực tế đã thanh toán trong chu kỳ hiện tại ("
                        + plan.refundDue().stripTrailingZeros().toPlainString()
                        + "đ) đã được ghi nhận. Khoản này chưa được xác nhận là đã hoàn tiền.");
        return saved;
    }

    private Membership applyCancellation(Membership membership, String reason, boolean upcoming,
            Payment exceptionalRefundPayment, boolean exceptionalAdmin) {
        Integer memberId = membership.getMember().getId();
        membership.setStatus(CANCELLED);
        membership.setCancelledAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        membership.setCancelReason(reason);

        String moneyMessage;
        Payment purchase = membership.getPurchasePayment();
        if (exceptionalAdmin && exceptionalRefundPayment != null) {
            exceptionalRefundPayment.setStatus(PaymentStatus.REFUND_REQUIRED);
            exceptionalRefundPayment.setNote("Exceptional Admin cancellation: full actual paid amount requested for Membership #"
                    + membership.getId());
            paymentRepository.save(exceptionalRefundPayment);
            moneyMessage = "Yêu cầu hoàn 100% số tiền thực tế đã thanh toán ("
                    + exceptionalRefundPayment.getAmount().stripTrailingZeros().toPlainString()
                    + "đ) đã được ghi vào hàng đợi. Đây chưa phải xác nhận tiền đã được hoàn.";
        } else if (exceptionalAdmin) {
            moneyMessage = "Không có Payment thành công liên kết để yêu cầu hoàn tiền. Hủy Membership không xác nhận tiền đã được hoàn.";
        } else if (upcoming && purchase != null && purchase.getStatus() == PaymentStatus.SUCCESS) {
            purchase.setStatus(PaymentStatus.REFUND_REQUIRED);
            purchase.setNote("Refund 100%: membership #" + membership.getId() + " was cancelled before it started");
            paymentRepository.save(purchase);
            moneyMessage = "Khoản thanh toán #" + purchase.getId() + " sẽ được hoàn 100%; vui lòng liên hệ lễ tân để nhận tiền.";
        } else if (upcoming) {
            moneyMessage = "Không tìm thấy thanh toán gốc của gói này; lễ tân sẽ đối soát và hoàn tiền nếu có.";
        } else {
            moneyMessage = "Lý do: " + reason + ". Khoản hoàn tiền (nếu có) do trung tâm xử lý trực tiếp.";
        }
        notificationService.createForUser(memberId, "Gói " + membership.getPlan().getName() + " đã được huỷ",
                "Gói " + membership.getStartDate().format(VN_DATE) + " → " + membership.getEndDate().format(VN_DATE)
                        + " đã được huỷ. " + moneyMessage);
        Membership saved = membershipRepository.saveAndFlush(membership);
        Payment auditedPayment = exceptionalAdmin ? exceptionalRefundPayment : purchase;
        String paymentSummary = auditedPayment == null
                ? "payment=none-linked"
                : "paymentId=" + auditedPayment.getId()
                    + ";paymentStatus=" + auditedPayment.getStatus()
                    + ";actualPaidAmount=" + (auditedPayment.getAmount() == null ? "unknown"
                            : auditedPayment.getAmount().stripTrailingZeros().toPlainString())
                    + ";refundAmount=" + (exceptionalAdmin && exceptionalRefundPayment != null
                            ? exceptionalRefundPayment.getAmount().stripTrailingZeros().toPlainString()
                            : "not-applicable")
                    + ";refundDisposition=" + (exceptionalAdmin
                            ? exceptionalRefundPayment == null ? "NO_LINKED_SUCCESSFUL_PAYMENT" : "REFUND_REQUIRED_PENDING_DISBURSEMENT_CONFIRMATION"
                            : "existing-ordinary-cancellation-policy");
        auditLogs.record("CANCEL", "MEMBERSHIP", saved.getId(),
                "status=ACTIVE->CANCELLED;" + paymentSummary + ";reason="
                        + (exceptionalAdmin ? reason : "stored-on-membership-or-request"));
        return saved;
    }

    @Transactional
    public MembershipResponse cancelAutoRenew(Integer id, Jwt callerJwt) {
        return updateAutoRenew(id, false, callerJwt);
    }

    @Transactional
    public MembershipResponse enableAutoRenew(Integer id, Jwt callerJwt) {
        return updateAutoRenew(id, true, callerJwt);
    }

    /**
     * Tự gia hạn là cài đặt của cả chuỗi gói còn hiệu lực: bật/tắt ở một gói áp dụng cho mọi gói ACTIVE
     * chưa hết hạn của member, vì chỉ gói cuối trong chuỗi mới thực sự được gia hạn.
     */
    private MembershipResponse updateAutoRenew(Integer id, boolean enabled, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())
                || !actor.getId().equals(membership.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Membership owner can change auto-renewal preference");
        }
        validateActiveMembership(membership);
        Integer memberId = membership.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Membership> chain = membershipRepository.findByMemberIdAndStatusAndEndDateGreaterThan(
                membership.getMember().getId(), "ACTIVE", today);
        if (chain.stream().noneMatch(item -> item.getId().equals(membership.getId()))) {
            chain = List.of(membership);
        }
        List<Integer> changedIds = chain.stream()
                .filter(item -> !java.util.Objects.equals(item.getAutoRenew(), enabled))
                .map(Membership::getId).toList();
        chain.forEach(item -> item.setAutoRenew(enabled));
        List<Membership> saved = membershipRepository.saveAllAndFlush(chain);
        for (Integer changedId : changedIds) {
            auditLogs.record("RENEWAL_PREFERENCE", "MEMBERSHIP", changedId,
                    "member renewal preference=" + (enabled ? "OFF->ON" : "ON->OFF")
                            + ";system-collection=not-authorized-by-this-setting");
        }
        for (Membership item : saved) {
            if (!enabled) {
                autoRenewalService.cancelPendingFor(item);
                renewalLifecycleService.closeForAutoRenewDisabled(item.getId());
            }
        }
        return toResponse(saved.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(membership));
    }

    /** Chạy job tự gia hạn ngay (ADMIN), giống job lúc nửa đêm; dùng cho demo và kiểm tra. */
    @Transactional
    public MembershipAutoRenewalService.RunResult runAutoRenewal(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can run auto-renewal manually");
        }
        MembershipAutoRenewalService.RunResult result = autoRenewalService.runDaily();
        auditLogs.record("MANUAL_RUN", "MEMBERSHIP_RENEWAL", null,
                "Admin manually invoked legacy renewal processing;created=" + result.created()
                        + ";cancelled=" + result.cancelled() + ".");
        return result;
    }

    private void validateActiveMembership(Membership membership) {
        if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            throw new IllegalArgumentException("Only active membership can change auto-renew setting");
        }
    }

    /** Endpoint-only guard; the scheduler continues to call the existing no-argument method. */
    @Transactional
    public int expireMemberships(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can manually expire memberships");
        }
        int expired = expireMemberships();
        auditLogs.record("MANUAL_EXPIRE", "MEMBERSHIP", null,
                "Admin manually expired eligible Memberships;count=" + expired + ".");
        return expired;
    }

    public int expireMemberships() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Membership> expiredCandidates = membershipRepository.findByStatusAndEndDateLessThanEqual("ACTIVE", today);
        int expiredCount = 0;

        for (Membership membership : expiredCandidates) {
            membership.setStatus("EXPIRED");
            membershipRepository.save(membership);
            expiredCount++;
        }
        return expiredCount;
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access memberships");
        }
        return actor;
    }

    private void requireStaff(User actor) {
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        if (!role.equals("ADMIN") && !role.equals("RECEPTIONIST")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can view all memberships");
        }
    }

    private void requireOwnMembershipOrStaff(User actor, Integer memberId) {
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        if (role.equals("ADMIN") || role.equals("RECEPTIONIST")) {
            return;
        }
        if (role.equals("MEMBER") && actor.getId().equals(memberId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this membership");
    }

    private MembershipResponse toResponse(Membership membership) {
        MembershipBenefitValues terms = MembershipBenefitValues.resolve(membership);
        return new MembershipResponse(
                membership.getId(),
                membership.getMember().getId(),
                membership.getMember().getName(),
                membership.getPlan().getId(),
                terms.planName(),
                terms.planDescription(),
                terms.planPrice(),
                terms.durationMonths(),
                terms.courseDiscountPercent(),
                terms.personalTrainingDiscountPercent(),
                terms.allowPersonalTrainingPlan(),
                terms.allowOneOnOneCoaching(),
                terms.source().name(),
                membership.getStartDate(),
                membership.getEndDate(),
                membership.getAutoRenew(),
                membership.getStatus(),
                membership.getCancelReason(),
                membership.getPurchasePayment() == null ? null : membership.getPurchasePayment().getId(),
                membership.getRenewalIntent() == null ? null : membership.getRenewalIntent().getId());
    }
}
