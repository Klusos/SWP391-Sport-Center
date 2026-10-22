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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.UpdateMembershipPlanRequest;
import group5.example.sportCenter.dto.CreateMembershipPlanRequest;
import group5.example.sportCenter.dto.MembershipPlanResponse;
import group5.example.sportCenter.dto.MembershipPlanUpgradeResponse;
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

    @Transactional(readOnly = true)
    public List<MembershipPlanUpgradeResponse> getUpgradePaths() {
        return upgradeRepository.findAllByOrderByIdAsc().stream().map(this::toUpgradeResponse).toList();
    }

    @Transactional
    public MembershipPlanUpgradeResponse addUpgradePath(Integer fromPlanId, Integer toPlanId) {
        if (fromPlanId == null || toPlanId == null || fromPlanId.equals(toPlanId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target plans must be different");
        }
        int firstPlanId = Math.min(fromPlanId, toPlanId);
        int secondPlanId = Math.max(fromPlanId, toPlanId);
        MembershipPlan firstPlan = membershipPlanRepository.findByIdForUpgradeUpdate(firstPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found: " + firstPlanId));
        MembershipPlan secondPlan = membershipPlanRepository.findByIdForUpgradeUpdate(secondPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found: " + secondPlanId));
        MembershipPlan from = firstPlanId == fromPlanId ? firstPlan : secondPlan;
        MembershipPlan to = firstPlanId == toPlanId ? firstPlan : secondPlan;
        validateNewUpgradePath(from, to);
        if (upgradeRepository.existsByFromPlan_IdAndToPlan_Id(fromPlanId, toPlanId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upgrade path is already configured");
        }
        MembershipPlanUpgrade path = new MembershipPlanUpgrade();
        path.setFromPlan(from);
        path.setToPlan(to);
        try {
            MembershipPlanUpgrade saved = upgradeRepository.saveAndFlush(path);
            auditLogs.record("CREATE", "MEMBERSHIP_PLAN_UPGRADE", saved.getId(),
                    "Configured upgrade path from plan " + fromPlanId + " to plan " + toPlanId + ".");
            return toUpgradeResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint is the final guard for concurrent duplicate requests.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upgrade path is already configured", exception);
        }
    }

    @Transactional
    public void removeUpgradePath(Integer pathId) {
        MembershipPlanUpgrade path = upgradeRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership upgrade path not found: " + pathId));
        auditLogs.record("DELETE", "MEMBERSHIP_PLAN_UPGRADE", pathId,
                "Removed upgrade path from plan " + path.getFromPlan().getId() + " to plan " + path.getToPlan().getId() + ".");
        upgradeRepository.delete(path);
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

        MembershipPlan plan = membershipPlanRepository.findByIdForUpgradeUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        validatePriceKeepsExistingUpgradePaths(plan, request.getMonthlyPrice());

        boolean meaningfulChange = !java.util.Objects.equals(plan.getName(), request.getName())
                || !java.util.Objects.equals(plan.getDescription(), request.getDescription())
                || !java.util.Objects.equals(plan.getEffectiveMonthlyPrice(), request.getMonthlyPrice())
                || !java.util.Objects.equals(plan.getCourseDiscountPercent(), request.getCourseDiscountPercent())
                || !java.util.Objects.equals(plan.getPersonalTrainingDiscountPercent(), request.getPersonalTrainingDiscountPercent())
                || !java.util.Objects.equals(plan.getAllowPersonalTrainingPlan(), request.getAllowPersonalTrainingPlan())
                || !java.util.Objects.equals(plan.getAllowOneOnOneCoaching(), request.getAllowOneOnOneCoaching());
        String previousStatus = plan.getStatus();
        String requestedStatus = normalizeStatus(request.getStatus());
        if (previousStatus.equalsIgnoreCase("ACTIVE") && requestedStatus.equals("INACTIVE")
                && hasUpgradePath(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remove configured upgrade paths before deactivating this plan");
        }
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

        plan.setStatus(requestedStatus);

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
        MembershipPlan plan = membershipPlanRepository.findByIdForUpgradeUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership plan not found with id: " + id));
        String previousStatus = plan.getStatus();
        String nextStatus = normalizeStatus(requestedStatus);
        if (previousStatus.equalsIgnoreCase("ACTIVE") && nextStatus.equals("INACTIVE") && hasUpgradePath(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remove configured upgrade paths before deactivating this plan");
        }
        plan.setStatus(nextStatus);
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

        MembershipPlan plan = membershipPlanRepository.findByIdForUpgradeUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Membership plan not found with id: " + id));

        String previousStatus = plan.getStatus();
        if (previousStatus.equalsIgnoreCase("ACTIVE") && hasUpgradePath(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remove configured upgrade paths before deactivating this plan");
        }
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
        List<Integer> lockIds = new ArrayList<>(sourceIds);
        lockIds.add(target.getId());
        for (Integer planId : lockIds.stream().distinct().sorted().toList()) {
            membershipPlanRepository.findByIdForUpgradeUpdate(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Upgrade plan not found: " + planId));
        }
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        for (MembershipPlanUpgrade edge : upgradeRepository.findAll()) {
            if (!edge.getToPlan().getId().equals(target.getId())) {
                graph.computeIfAbsent(edge.getFromPlan().getId(), ignored -> new HashSet<>()).add(edge.getToPlan().getId());
            }
        }
        List<MembershipPlan> sources = new ArrayList<>();
        for (Integer sourceId : sourceIds) {
            MembershipPlan source = membershipPlanRepository.findById(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Upgrade source plan not found: " + sourceId));
            sources.add(source);
            graph.computeIfAbsent(sourceId, ignored -> new HashSet<>()).add(target.getId());
        }
        if (hasCycle(graph)) throw new IllegalArgumentException("Membership plan upgrade relationships cannot contain cycles");
        for (MembershipPlan source : sources) validateNewUpgradePath(source, target);

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

    private void validateNewUpgradePath(MembershipPlan source, MembershipPlan target) {
        if (!"ACTIVE".equalsIgnoreCase(source.getStatus()) || !"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upgrade paths can only be configured between ACTIVE plans");
        }
        if (source.getEffectiveMonthlyPrice() == null || target.getEffectiveMonthlyPrice() == null
                || target.getEffectiveMonthlyPrice().compareTo(source.getEffectiveMonthlyPrice()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target plan monthly price must be greater than source plan monthly price");
        }
    }

    private boolean hasUpgradePath(Integer planId) {
        return upgradeRepository.findAll().stream().anyMatch(path -> path.getFromPlan().getId().equals(planId)
                || path.getToPlan().getId().equals(planId));
    }

    private void validatePriceKeepsExistingUpgradePaths(MembershipPlan plan, java.math.BigDecimal proposedPrice) {
        for (MembershipPlanUpgrade path : upgradeRepository.findAll()) {
            if (path.getFromPlan().getId().equals(plan.getId())
                    && path.getToPlan().getEffectiveMonthlyPrice().compareTo(proposedPrice) <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Monthly price change would make a configured upgrade target no more expensive");
            }
            if (path.getToPlan().getId().equals(plan.getId())
                    && proposedPrice.compareTo(path.getFromPlan().getEffectiveMonthlyPrice()) <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Monthly price change would make this plan no more expensive than a configured source");
            }
        }
    }

    private MembershipPlanUpgradeResponse toUpgradeResponse(MembershipPlanUpgrade path) {
        MembershipPlan from = path.getFromPlan();
        MembershipPlan to = path.getToPlan();
        return new MembershipPlanUpgradeResponse(path.getId(), from.getId(), from.getName(),
                from.getEffectiveMonthlyPrice(), to.getId(), to.getName(), to.getEffectiveMonthlyPrice());
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
