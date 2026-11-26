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

        String beforeAfter = changes(plan, request);
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
        String updatedStatus = updatedPlan.getStatus();
        if (!previousStatus.equalsIgnoreCase(updatedStatus)) {
            beforeAfter = append(beforeAfter, "status=" + previousStatus + "->" + updatedStatus);
        }
        if (!beforeAfter.isEmpty()) auditLogs.record("UPDATE", "MEMBERSHIP_PLAN", id, beforeAfter);

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
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id,
                    "status=" + previousStatus + "->" + saved.getStatus());
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
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id,
                    "status=" + previousStatus + "->" + plan.getStatus());
        }
    }

    private String changes(MembershipPlan plan, UpdateMembershipPlanRequest request) {
        String changes = "";
        changes = changed(changes, "name", plan.getName(), request.getName());
        changes = changed(changes, "price", plan.getPrice(), request.getPrice());
        changes = changed(changes, "durationMonths", plan.getDurationMonths(), request.getDurationMonths());
        changes = changed(changes, "courseDiscountPercent", plan.getCourseDiscountPercent(), request.getCourseDiscountPercent());
        changes = changed(changes, "ptDiscountPercent", plan.getPersonalTrainingDiscountPercent(), request.getPersonalTrainingDiscountPercent());
        changes = changed(changes, "allowPtPlan", plan.getAllowPersonalTrainingPlan(), request.getAllowPersonalTrainingPlan());
        changes = changed(changes, "allowOneOnOne", plan.getAllowOneOnOneCoaching(), request.getAllowOneOnOneCoaching());
        return changes;
    }

    private String changed(String current, String field, Object before, Object after) {
        return java.util.Objects.equals(before, after) ? current
                : append(current, field + "=" + before + "->" + after);
    }

    private String append(String current, String next) {
        return current.isEmpty() ? next : current + ";" + next;
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
