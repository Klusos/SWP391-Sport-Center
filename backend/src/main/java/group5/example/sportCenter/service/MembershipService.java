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
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final Clock clock;

    public MembershipService(
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
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
        if (!actorRole.equals("ADMIN") && !actorRole.equals("RECEPTIONIST")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Members must purchase a membership through /api/payments");
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
        membership.setAutoRenew(request.getAutoRenew());
        membership.setStatus("ACTIVE");

        return toResponse(membershipRepository.save(membership));
    }

    /**
     * Gọi từ PaymentService khi thanh toán MEMBERSHIP thành công (chạy trong transaction của payment).
     * Chưa có gói còn hiệu lực -> kích hoạt từ hôm nay. Đang có gói -> gia hạn bằng một gói mới
     * bắt đầu đúng ngày gói hiện tại kết thúc (endDate là mốc loại trừ), không sửa gói cũ.
     */
    @Transactional
    public Membership activateOrRenewFromPayment(Integer memberId, Integer planId, boolean autoRenew) {
        // Khoá dòng user để hai lần gia hạn song song không cùng tính từ một endDate.
        User member = userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));
        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + planId));

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate startDate = membershipRepository
                .findByMemberIdAndStatusAndEndDateGreaterThan(memberId, "ACTIVE", today).stream()
                .map(Membership::getEndDate)
                .max(LocalDate::compareTo)
                .orElse(today);

        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(startDate);
        membership.setEndDate(startDate.plusMonths(plan.getDurationMonths()));
        membership.setAutoRenew(autoRenew);
        membership.setStatus("ACTIVE");
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

    private MembershipResponse updateAutoRenew(Integer id, boolean enabled, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + id));
        requireOwnMembershipOrStaff(actor, membership.getMember().getId());
        validateActiveMembership(membership);
        membership.setAutoRenew(enabled);
        return toResponse(membershipRepository.save(membership));
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
        return new MembershipResponse(
                membership.getId(),
                membership.getMember().getId(),
                membership.getMember().getName(),
                membership.getPlan().getId(),
                membership.getPlan().getName(),
                membership.getStartDate(),
                membership.getEndDate(),
                membership.getAutoRenew(),
                membership.getStatus());
    }
}
