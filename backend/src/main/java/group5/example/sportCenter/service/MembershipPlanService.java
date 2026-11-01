package group5.example.sportCenter.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.UpdateMembershipPlanRequest;
import group5.example.sportCenter.dto.CreateMembershipPlanRequest;
import group5.example.sportCenter.dto.MembershipPlanResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;

@Service
public class MembershipPlanService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final AuditLogService auditLogs;

    public MembershipPlanService(
            MembershipPlanRepository membershipPlanRepository,
            AuditLogService auditLogs) {
        this.membershipPlanRepository = membershipPlanRepository;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanResponse> getAllPlans() {
        return membershipPlanRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipPlanResponse> getActivePlans() {
        return membershipPlanRepository.findByStatus("ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MembershipPlanResponse getPlanById(Integer id) {

        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        return toResponse(plan);
    }

    @Transactional
    public MembershipPlanResponse createPlan(
            CreateMembershipPlanRequest request) {

        MembershipPlan plan = new MembershipPlan();

        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationMonths(request.getDurationMonths());

        plan.setCourseDiscountPercent(
                request.getCourseDiscountPercent());

        plan.setPersonalTrainingDiscountPercent(
                request.getPersonalTrainingDiscountPercent());

        plan.setAllowPersonalTrainingPlan(
                request.getAllowPersonalTrainingPlan());

        plan.setAllowOneOnOneCoaching(
                request.getAllowOneOnOneCoaching());

        plan.setStatus("ACTIVE");

        MembershipPlan savedPlan =
                membershipPlanRepository.save(plan);
        auditLogs.record("CREATE", "MEMBERSHIP_PLAN", savedPlan.getId(), "Membership plan created.");

        return toResponse(savedPlan);
    }

    @Transactional
    public MembershipPlanResponse updatePlan(
            Integer id,
            UpdateMembershipPlanRequest request) {

        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        boolean meaningfulChange = !java.util.Objects.equals(plan.getName(), request.getName())
                || !java.util.Objects.equals(plan.getDescription(), request.getDescription())
                || !java.util.Objects.equals(plan.getPrice(), request.getPrice())
                || !java.util.Objects.equals(plan.getDurationMonths(), request.getDurationMonths())
                || !java.util.Objects.equals(plan.getCourseDiscountPercent(), request.getCourseDiscountPercent())
                || !java.util.Objects.equals(plan.getPersonalTrainingDiscountPercent(), request.getPersonalTrainingDiscountPercent())
                || !java.util.Objects.equals(plan.getAllowPersonalTrainingPlan(), request.getAllowPersonalTrainingPlan())
                || !java.util.Objects.equals(plan.getAllowOneOnOneCoaching(), request.getAllowOneOnOneCoaching());
        String previousStatus = plan.getStatus();
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationMonths(request.getDurationMonths());

        plan.setCourseDiscountPercent(
                request.getCourseDiscountPercent());

        plan.setPersonalTrainingDiscountPercent(
                request.getPersonalTrainingDiscountPercent());

        plan.setAllowPersonalTrainingPlan(
                request.getAllowPersonalTrainingPlan());

        plan.setAllowOneOnOneCoaching(
                request.getAllowOneOnOneCoaching());

        plan.setStatus(normalizeStatus(request.getStatus()));

        MembershipPlan updatedPlan =
                membershipPlanRepository.save(plan);
        if (meaningfulChange) auditLogs.record("UPDATE", "MEMBERSHIP_PLAN", id, "Membership plan details updated.");
        if (!previousStatus.equalsIgnoreCase(updatedPlan.getStatus())) {
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id, "Membership plan status changed.");
        }

        return toResponse(updatedPlan);
    }

    @Transactional
    public MembershipPlanResponse updateStatus(Integer id, String requestedStatus) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership plan not found with id: " + id));
        String previousStatus = plan.getStatus();
        plan.setStatus(normalizeStatus(requestedStatus));
        MembershipPlan saved = membershipPlanRepository.save(plan);
        if (!previousStatus.equalsIgnoreCase(saved.getStatus())) {
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id, "Membership plan status changed.");
        }
        return toResponse(saved);
    }

    /**
     * Keeps the legacy DELETE API compatible while preserving plan rows referenced
     * by memberships. DELETE now performs the same soft deactivation as status=INACTIVE.
     */
    @Transactional
    public void deletePlan(Integer id) {

        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        String previousStatus = plan.getStatus();
        plan.setStatus("INACTIVE");
        membershipPlanRepository.save(plan);
        if (!previousStatus.equalsIgnoreCase(plan.getStatus())) {
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id, "Membership plan status changed.");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        return normalized;
    }

    private MembershipPlanResponse toResponse(MembershipPlan plan) {

        return new MembershipPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getDurationMonths(),
                plan.getCourseDiscountPercent(),
                plan.getPersonalTrainingDiscountPercent(),
                plan.getAllowPersonalTrainingPlan(),
                plan.getAllowOneOnOneCoaching(),
                plan.getStatus()
        );
    }
}
