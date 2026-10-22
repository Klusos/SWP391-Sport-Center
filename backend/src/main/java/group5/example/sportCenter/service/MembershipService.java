package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
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
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final AuditLogService auditLogService;
    private final PaymentRepository paymentRepository;
    private final PaymentCheckoutAttemptRepository paymentCheckoutAttemptRepository;
    private final MembershipRenewalService membershipRenewalService;
    private final Clock clock;

    public MembershipService(
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            AuditLogService auditLogService,
            PaymentRepository paymentRepository,
            PaymentCheckoutAttemptRepository paymentCheckoutAttemptRepository,
            MembershipRenewalService membershipRenewalService,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.auditLogService = auditLogService;
        this.paymentRepository = paymentRepository;
        this.paymentCheckoutAttemptRepository = paymentCheckoutAttemptRepository;
        this.membershipRenewalService = membershipRenewalService;
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

        User member = userRepository.findById(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getMemberId()));
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership can only be assigned to a MEMBER");
        }

        Optional<Membership> activeMembership = membershipRepository
                .findFirstByMemberIdAndStatusAndEndDateGreaterThan(
                        request.getMemberId(), "ACTIVE", LocalDate.now(clock.withZone(BUSINESS_ZONE)));
        if (activeMembership.isPresent()) {
            throw new IllegalArgumentException("Member already has an active membership");
        }

        MembershipPlan plan = membershipPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership plan not found with id: " + request.getPlanId()));

        LocalDate endDate = request.getStartDate().plusMonths(plan.getDurationMonths());
        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(endDate);
        membership.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        membership.setStatus("ACTIVE");
        Membership saved = membershipRepository.save(membership);
        auditLogService.record("ADMIN_ASSIGN_MEMBERSHIP", "MEMBERSHIP", saved.getId(),
                "Admin assigned no-charge membership; targetMemberId=" + member.getId()
                        + ", planId=" + plan.getId() + ", startDate=" + saved.getStartDate()
                        + ", endDate=" + saved.getEndDate() + ", reason=manual administrative assignment");
        return toResponse(saved);
    }

    /**
     * Gọi từ PaymentService khi thanh toán MEMBERSHIP thành công (chạy trong transaction của payment).
     * Chưa có gói còn hiệu lực -> kích hoạt từ hôm nay. Đang có gói -> gia hạn bằng một gói mới
     * bắt đầu đúng ngày gói hiện tại kết thúc (endDate là mốc loại trừ), không sửa gói cũ.
     */
    @Transactional
    public Optional<String> activateOrRenewFromPayment(Payment payment) {
        Integer memberId = payment.getMember().getId();
        Integer planId = payment.getReferenceId();
        // Khoá dòng user để hai lần gia hạn song song không cùng tính từ một endDate.
        User member = userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + planId));

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Optional<String> blocker = ordinaryPurchaseConflict(memberId, payment.getId(), today);
        if (blocker.isPresent()) return blocker;

        // Null purchasedMonths is a pre-9.2A pending payment: preserve its captured amount and
        // legacy fixed plan duration. New payments always carry the selected month snapshot.
        int purchasedMonths = payment.getPurchasedMonths() == null
                ? plan.getDurationMonths() : payment.getPurchasedMonths();
        if (purchasedMonths <= 0) throw new IllegalArgumentException("Membership payment duration is invalid");

        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(today);
        membership.setEndDate(today.plusMonths(purchasedMonths));
        membership.setAutoRenew(Boolean.TRUE.equals(payment.getAutoRenew()));
        membership.setStatus("ACTIVE");
        membership.setPurchasedMonths(payment.getPurchasedMonths());
        membership.setPaidAmount(payment.getAmount());
        membership.setSourcePayment(payment);
        membershipRepository.save(membership);
        return Optional.empty();
    }

    /** Returns why an ordinary purchase cannot be issued or applied for this member. */
    public Optional<String> ordinaryPurchaseConflict(Integer memberId, Integer excludedPaymentId, LocalDate today) {
        if (!membershipRepository.findByMemberIdAndStatusAndEndDateGreaterThan(memberId, "ACTIVE", today).isEmpty()) {
            return Optional.of("Member already has a current or future Membership; use the dedicated Renewal or Upgrade workflow");
        }
        boolean pendingPayment = excludedPaymentId == null
                ? paymentRepository.existsByMember_IdAndPurposeAndStatus(
                        memberId, group5.example.sportCenter.entity.PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING)
                : paymentRepository.existsByMember_IdAndPurposeAndStatusAndIdNot(
                        memberId, group5.example.sportCenter.entity.PaymentPurpose.MEMBERSHIP,
                        PaymentStatus.PENDING, excludedPaymentId);
        if (pendingPayment) {
            return Optional.of("Member already has a pending Membership payment that must be resolved first");
        }
        return Optional.empty();
    }

    @Transactional
    public MembershipResponse cancelAutoRenew(Integer id, Jwt callerJwt) {
        return updateAutoRenew(id, false, callerJwt);
    }

    @Transactional
    public MembershipResponse enableAutoRenew(Integer id, Jwt callerJwt) {
        return updateAutoRenew(id, true, callerJwt);
    }

    private MembershipResponse updateAutoRenew(Integer id, boolean enabled, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Membership snapshot = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        Integer memberId = snapshot.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        Membership membership = membershipRepository.findByIdForRenewalUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        requireOwnMembershipOrStaff(actor, membership.getMember().getId());
        validateActiveMembership(membership);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (enabled) {
            if (membership.getStartDate().isAfter(today) || !membership.getEndDate().isAfter(today)
                    || membership.getSourcePayment() == null
                    || membership.getSourcePayment().getStatus() != PaymentStatus.SUCCESS) {
                throw new IllegalArgumentException("Auto Renew can only be enabled for a current paid membership period");
            }
            if (!isFinalPaidRenewalPeriod(membership)) {
                throw new IllegalArgumentException("Auto Renew is available only for the member's final paid membership period");
            }
            Payment existing = paymentRepository.findRenewalByMembershipIdForUpdate(id).orElse(null);
            if (existing != null) {
                paymentCheckoutAttemptRepository.findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(existing.getId());
            }
            if (existing != null && existing.getStatus() != PaymentStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This membership period already has a terminal renewal payment attempt and cannot be retried");
            }
        }
        membership.setAutoRenew(enabled);
        if (!enabled) {
            Payment renewal = paymentRepository.findRenewalByMembershipIdForUpdate(id).orElse(null);
            List<group5.example.sportCenter.entity.PaymentCheckoutAttempt> attempts = renewal == null ? List.of()
                    : paymentCheckoutAttemptRepository.findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(renewal.getId());
            if (renewal != null && renewal.getStatus() == PaymentStatus.PENDING) {
                var captured = attempts.stream().filter(a -> a.getVerifiedAt() != null).findFirst().orElse(null);
                if (captured != null) {
                    renewal.setStatus(renewal.getMethod() == null ? PaymentStatus.CANCELLED : PaymentStatus.REFUND_REQUIRED);
                    renewal.setPaidAt(captured.getVerifiedAt());
                    renewal.setNote("Verified renewal capture exists while Auto Renew was disabled; manual reconciliation required");
                } else {
                    renewal.setStatus(PaymentStatus.CANCELLED);
                    renewal.setNote("Renewal request cancelled because Auto Renew was disabled");
                }
                paymentRepository.save(renewal);
            }
        }
        Membership saved = membershipRepository.save(membership);
        if (enabled) {
            membershipRenewalService.createRenewalRequestIfDue(saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    public Membership lockRenewalSource(Integer id) {
        return membershipRepository.findByIdForRenewalUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public boolean isFinalPaidRenewalPeriod(Membership membership) {
        if (membership.getSourcePayment() == null
                || membership.getSourcePayment().getStatus() != PaymentStatus.SUCCESS) return false;
        List<Membership> paidPeriods = membershipRepository
                .findSuccessfulPaidPeriods(membership.getMember().getId());
        return !paidPeriods.isEmpty() && paidPeriods.get(0).getId().equals(membership.getId());
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
        return expireMemberships();
    }

    @Transactional
    public int expireMemberships() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Integer> expiredCandidateIds = membershipRepository.findExpiredCandidateIds(today);
        int expiredCount = 0;

        for (Integer candidateId : expiredCandidateIds) {
            Membership snapshot = membershipRepository.findById(candidateId).orElse(null);
            if (snapshot == null) continue;
            Integer memberId = snapshot.getMember().getId();
            if (userRepository.findByIdForBookingUpdate(memberId).isEmpty()) continue;
            Membership membership = membershipRepository.findByIdForRenewalUpdate(candidateId).orElse(null);
            if (membership == null || !"ACTIVE".equalsIgnoreCase(membership.getStatus())
                    || membership.getEndDate().isAfter(today)) continue;
            Payment renewal = paymentRepository.findRenewalByMembershipIdForUpdate(membership.getId()).orElse(null);
            List<group5.example.sportCenter.entity.PaymentCheckoutAttempt> attempts = renewal == null ? List.of()
                    : paymentCheckoutAttemptRepository.findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(renewal.getId());
            membership.setStatus("EXPIRED");
            membershipRepository.save(membership);
            if (renewal != null && renewal.getStatus() == PaymentStatus.PENDING) {
                var captured = attempts.stream().filter(a -> a.getVerifiedAt() != null).findFirst().orElse(null);
                if (captured != null) {
                    renewal.setStatus(renewal.getMethod() == null ? PaymentStatus.CANCELLED : PaymentStatus.REFUND_REQUIRED);
                    renewal.setPaidAt(captured.getVerifiedAt());
                    renewal.setNote("Verified renewal capture exists after source membership expiry; manual reconciliation required");
                } else {
                    renewal.setStatus(PaymentStatus.CANCELLED);
                    renewal.setNote("Renewal request expired with its source membership period");
                }
                paymentRepository.save(renewal);
            }
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
        return new MembershipResponse(
                membership.getId(),
                membership.getMember().getId(),
                membership.getMember().getName(),
                membership.getPlan().getId(),
                membership.getPlan().getName(),
                membership.getStartDate(),
                membership.getEndDate(),
                membership.getAutoRenew(),
                membership.getStatus(),
                membership.getPurchasedMonths(),
                membership.getPaidAmount(),
                membership.getSourcePayment() == null ? null : membership.getSourcePayment().getId());
    }
}
