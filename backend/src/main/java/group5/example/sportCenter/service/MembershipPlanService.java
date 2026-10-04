package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CreateMembershipPlanRequest;
import group5.example.sportCenter.dto.MembershipPlanResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;

@Service
public class MembershipPlanService {

    private final MembershipPlanRepository membershipPlanRepository;

    public MembershipPlanService(
            MembershipPlanRepository membershipPlanRepository) {
        this.membershipPlanRepository = membershipPlanRepository;
    }

    public List<MembershipPlanResponse> getAllPlans() {
        return membershipPlanRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MembershipPlanResponse getPlanById(Integer id) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        return toResponse(plan);
    }

    public MembershipPlanResponse createPlan(
            CreateMembershipPlanRequest request) {

        MembershipPlan plan = new MembershipPlan();

        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationMonths(request.getDurationMonths());
        plan.setDiscountPercent(request.getDiscountPercent());
        plan.setBenefits(request.getBenefits());
        plan.setStatus("ACTIVE");

        MembershipPlan savedPlan =
                membershipPlanRepository.save(plan);

        return toResponse(savedPlan);
    }

    private MembershipPlanResponse toResponse(MembershipPlan plan) {
        return new MembershipPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getDurationMonths(),
                plan.getDiscountPercent(),
                plan.getBenefits(),
                plan.getStatus()
        );
    }
}