package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.MembershipResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipCancellationRequestRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipService {

    public static final String CANCELLED = "CANCELLED";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES = List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final MembershipCancellationRequestRepository cancellationRequestRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipAutoRenewalService autoRenewalService;
    private final MembershipRenewalLifecycleService renewalLifecycleService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public MembershipService(
            MembershipRepository membershipRepository,
            MembershipCancellationRequestRepository cancellationRequestRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipAutoRenewalService autoRenewalService,
            MembershipRenewalLifecycleService renewalLifecycleService,
            PaymentRepository paymentRepository,
            NotificationService notificationService,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.autoRenewalService = autoRenewalService;
        this.renewalLifecycleService = renewalLifecycleService;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.clock = clock;
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
        LocalDate today = lockAndRequireLastActive(membership);
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        boolean upcoming = membership.getStartDate().isAfter(today);
        if (!upcoming) {
            if (trimmedReason == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to cancel a running membership");
            }
        }

        // An unpaid auto-renewal for this membership is pointless once it is cancelled.
        autoRenewalService.cancelPendingFor(membership);
        if (!upcoming && paymentRepository.existsByMember_IdAndPurposeInAndStatus(memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finish or cancel the member's pending membership payment before cancelling the running membership");
        }

        // A direct cancellation answers the member's pending request, if any.
        cancellationRequestRepository.findFirstByMembershipIdAndStatus(id, MembershipCancellationRequestStatus.PENDING)
                .ifPresent(request -> {
                    request.setStatus(MembershipCancellationRequestStatus.APPROVED);
                    request.setDecidedBy(actor);
                    request.setDecidedAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
                    request.setDecisionNote("Quản trị viên đã huỷ gói trực tiếp");
                    cancellationRequestRepository.save(request);
                });
        return toResponse(applyCancellation(membership,
                trimmedReason != null ? trimmedReason : "Cancelled by " + role + " before it started", upcoming));
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
        return applyCancellation(membership, reason, true);
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

    private Membership applyCancellation(Membership membership, String reason, boolean upcoming) {
        Integer memberId = membership.getMember().getId();
        membership.setStatus(CANCELLED);
        membership.setCancelledAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        membership.setCancelReason(reason);

        String moneyMessage;
        Payment purchase = membership.getPurchasePayment();
        if (upcoming && purchase != null && purchase.getStatus() == PaymentStatus.SUCCESS) {
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
        return membershipRepository.save(membership);
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
        chain.forEach(item -> item.setAutoRenew(enabled));
        List<Membership> saved = membershipRepository.saveAllAndFlush(chain);
        for (Membership item : saved) {
            if (!enabled) {
                autoRenewalService.cancelPendingFor(item);
                renewalLifecycleService.closeForAutoRenewDisabled(item.getId());
            }
        }
        return toResponse(saved.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(membership));
    }

    /** Chạy job tự gia hạn ngay (ADMIN), giống job lúc nửa đêm; dùng cho demo và kiểm tra. */
    public MembershipAutoRenewalService.RunResult runAutoRenewal(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can run auto-renewal manually");
        }
        return autoRenewalService.runDaily();
    }

    private void validateActiveMembership(Membership membership) {
        if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            throw new IllegalArgumentException("Only active membership can change auto-renew setting");
        }
    }

    /** Endpoint-only guard; the scheduler continues to call the existing no-argument method. */
    public int expireMemberships(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can manually expire memberships");
        }
        return expireMemberships();
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
                membership.getCancelReason());
    }
}
