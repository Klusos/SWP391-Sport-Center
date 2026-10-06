package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public MembershipService(
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            MembershipPlanRepository membershipPlanRepository) {

        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.membershipPlanRepository = membershipPlanRepository;
    }

    public List<MembershipResponse> getAllMemberships() {
        return membershipRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MembershipResponse getMembershipById(Integer id) {

        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership not found with id: " + id));

        return toResponse(membership);
    }

    public List<MembershipResponse> getMembershipsByMemberId(Integer memberId) {

        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException(
                    "User not found with id: " + memberId);
        }

        return membershipRepository.findByMemberId(memberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MembershipResponse createMembership(
            CreateMembershipRequest request) {

        User member = userRepository.findById(request.getMemberId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: "
                                        + request.getMemberId()));

        Optional<Membership> activeMembership =
                membershipRepository
                        .findFirstByMemberIdAndStatusAndEndDateGreaterThanEqual(
                                request.getMemberId(),
                                "ACTIVE",
                                LocalDate.now());

        if (activeMembership.isPresent()) {
            throw new IllegalArgumentException(
                    "Member already has an active membership");
        }

        MembershipPlan plan = membershipPlanRepository
                .findById(request.getPlanId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: "
                                        + request.getPlanId()));

        LocalDate endDate =
                request.getStartDate()
                        .plusMonths(plan.getDurationMonths());

        Membership membership = new Membership();

        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(endDate);
        membership.setAutoRenew(request.getAutoRenew());
        membership.setStatus("ACTIVE");

        Membership savedMembership =
                membershipRepository.save(membership);

        return toResponse(savedMembership);
    }

    public MembershipResponse cancelAutoRenew(Integer id) {

        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership not found with id: " + id));
        validateActiveMembership(membership);                    
        membership.setAutoRenew(false);

        Membership updatedMembership =
                membershipRepository.save(membership);

        return toResponse(updatedMembership);
    }

    public MembershipResponse enableAutoRenew(Integer id) {

        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership not found with id: " + id));
        validateActiveMembership(membership);                    
        membership.setAutoRenew(true);

        Membership updatedMembership =
                membershipRepository.save(membership);

        return toResponse(updatedMembership);
    }

    private void validateActiveMembership(Membership membership) {

        if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            throw new IllegalArgumentException(
                    "Only active membership can change auto-renew setting");
        }
    }

    public int expireMemberships() {

        LocalDate today = LocalDate.now();

        List<Membership> expiredCandidates =
                membershipRepository
                        .findByStatusAndEndDateBefore(
                                "ACTIVE",
                                today);

        int expiredCount = 0;

        for (Membership membership : expiredCandidates) {

            if (!membership.getAutoRenew()) {

                membership.setStatus("EXPIRED");
                membershipRepository.save(membership);

                expiredCount++;
            }

            // TODO:
            // If autoRenew = true,
            // process renewal payment before expiring membership.
        }

        return expiredCount;
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

                membership.getStatus()
        );
    }
}