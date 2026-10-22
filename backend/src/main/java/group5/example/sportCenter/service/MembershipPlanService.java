package group5.example.sportCenter.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.UpdateMembershipPlanRequest;
import group5.example.sportCenter.dto.CreateMembershipPlanRequest;
import group5.example.sportCenter.dto.MembershipPlanResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipPlanUpgrade;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;

@Service
public class MembershipPlanService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipPlanUpgradeRepository upgradeRepository;
    private final AuditLogService auditLogs;

    public MembershipPlanService(
            MembershipPlanRepository membershipPlanRepository,
            MembershipPlanUpgradeRepository upgradeRepository,
            AuditLogService auditLogs) {
        this.membershipPlanRepository = membershipPlanRepository;
        this.upgradeRepository = upgradeRepository;
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
        plan.setMonthlyPrice(request.getMonthlyPrice());
        // Keep the old required columns as an immutable one-month compatibility snapshot.
        plan.setPrice(request.getMonthlyPrice());
        plan.setDurationMonths(1);

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
        replaceUpgradeSources(savedPlan, request.getUpgradeFromPlanIds());
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
                || !java.util.Objects.equals(plan.getEffectiveMonthlyPrice(), request.getMonthlyPrice())
                || !java.util.Objects.equals(plan.getCourseDiscountPercent(), request.getCourseDiscountPercent())
                || !java.util.Objects.equals(plan.getPersonalTrainingDiscountPercent(), request.getPersonalTrainingDiscountPercent())
                || !java.util.Objects.equals(plan.getAllowPersonalTrainingPlan(), request.getAllowPersonalTrainingPlan())
                || !java.util.Objects.equals(plan.getAllowOneOnOneCoaching(), request.getAllowOneOnOneCoaching());
        String previousStatus = plan.getStatus();
        List<Integer> previousUpgradeSources = upgradeRepository.findByToPlan_Id(id).stream()
                .map(edge -> edge.getFromPlan().getId()).sorted().toList();
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setMonthlyPrice(request.getMonthlyPrice());

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
        if (request.getUpgradeFromPlanIds() != null) {
            replaceUpgradeSources(updatedPlan, request.getUpgradeFromPlanIds());
        }
        if (meaningfulChange) auditLogs.record("UPDATE", "MEMBERSHIP_PLAN", id, "Membership plan details updated.");
        if (!previousStatus.equalsIgnoreCase(updatedPlan.getStatus())) {
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_PLAN", id, "Membership plan status changed.");
        }

        boolean relationshipChange = request.getUpgradeFromPlanIds() != null
                && !previousUpgradeSources.equals(request.getUpgradeFromPlanIds().stream().sorted().toList());
        if (relationshipChange) auditLogs.record("UPDATE", "MEMBERSHIP_PLAN", id, "Membership upgrade relationships updated.");
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
                plan.getEffectiveMonthlyPrice(),
                upgradeRepository.findByToPlan_Id(plan.getId()).stream()
                        .map(edge -> edge.getFromPlan().getId()).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                plan.getCourseDiscountPercent(),
                plan.getPersonalTrainingDiscountPercent(),
                plan.getAllowPersonalTrainingPlan(),
                plan.getAllowOneOnOneCoaching(),
                plan.getStatus()
        );
    }

    private void replaceUpgradeSources(MembershipPlan target, List<Integer> requestedIds) {
        List<Integer> sourceIds = requestedIds == null ? List.of() : requestedIds;
        if (new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new IllegalArgumentException("Upgrade source plan IDs must not contain duplicates");
        }
        if (sourceIds.stream().anyMatch(id -> id == null || id.equals(target.getId()))) {
            throw new IllegalArgumentException("A plan cannot upgrade to itself");
        }
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        for (MembershipPlanUpgrade edge : upgradeRepository.findAll()) {
            if (!edge.getToPlan().getId().equals(target.getId())) {
                graph.computeIfAbsent(edge.getFromPlan().getId(), ignored -> new HashSet<>()).add(edge.getToPlan().getId());
            }
        }
        for (Integer sourceId : sourceIds) {
            membershipPlanRepository.findById(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Upgrade source plan not found: " + sourceId));
            graph.computeIfAbsent(sourceId, ignored -> new HashSet<>()).add(target.getId());
        }
        if (hasCycle(graph)) throw new IllegalArgumentException("Membership plan upgrade relationships cannot contain cycles");

        upgradeRepository.deleteByToPlan_Id(target.getId());
        List<MembershipPlanUpgrade> replacements = new ArrayList<>();
        for (Integer sourceId : sourceIds) {
            MembershipPlanUpgrade edge = new MembershipPlanUpgrade();
            edge.setFromPlan(membershipPlanRepository.getReferenceById(sourceId));
            edge.setToPlan(target);
            replacements.add(edge);
        }
        upgradeRepository.saveAll(replacements);
    }

    private boolean hasCycle(Map<Integer, Set<Integer>> graph) {
        Map<Integer, Integer> colors = new HashMap<>();
        Set<Integer> nodes = new HashSet<>(graph.keySet());
        graph.values().forEach(nodes::addAll);
        for (Integer node : nodes) if (visit(node, graph, colors)) return true;
        return false;
    }

    private boolean visit(Integer node, Map<Integer, Set<Integer>> graph, Map<Integer, Integer> colors) {
        int color = colors.getOrDefault(node, 0);
        if (color == 1) return true;
        if (color == 2) return false;
        colors.put(node, 1);
        for (Integer next : graph.getOrDefault(node, Set.of())) if (visit(next, graph, colors)) return true;
        colors.put(node, 2);
        return false;
    }
}
