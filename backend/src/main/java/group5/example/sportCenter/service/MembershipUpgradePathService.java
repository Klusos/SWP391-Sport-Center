package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateMembershipUpgradePathRequest;
import group5.example.sportCenter.dto.MembershipUpgradePathResponse;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgradePath;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipUpgradePathRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipUpgradePathService {
    private final MembershipUpgradePathRepository pathRepository;
    private final MembershipPlanRepository planRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogs;
    private final Clock clock;

    public MembershipUpgradePathService(
            MembershipUpgradePathRepository pathRepository,
            MembershipPlanRepository planRepository,
            UserRepository userRepository,
            AuditLogService auditLogs,
            Clock clock) {
        this.pathRepository = pathRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MembershipUpgradePathResponse> list(Jwt jwt) {
        requireAdmin(jwt);
        return pathRepository.findAllByOrderByFromPlanIdAscToPlanIdAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public MembershipUpgradePathResponse create(CreateMembershipUpgradePathRequest request, Jwt jwt) {
        requireAdmin(jwt);
        if (request.fromPlanId().equals(request.toPlanId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An Upgrade Path must connect two different Plans");
        }
        PlanPair lockedPlans = lockPlans(request.fromPlanId(), request.toPlanId());
        MembershipPlan from = lockedPlans.from();
        MembershipPlan to = lockedPlans.to();
        validateConfigurable(from, to);
        if (pathRepository.findByFromPlanIdAndToPlanId(from.getId(), to.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This directed Upgrade Path already exists; enable it instead");
        }

        LocalDateTime now = LocalDateTime.now(clock.withZone(java.time.ZoneOffset.UTC));
        MembershipUpgradePath path = new MembershipUpgradePath();
        path.setFromPlan(from);
        path.setToPlan(to);
        path.setActive(true);
        path.setCreatedAt(now);
        path.setUpdatedAt(now);
        try {
            path = pathRepository.saveAndFlush(path);
            auditLogs.record("CREATE", "MEMBERSHIP_UPGRADE_PATH", path.getId(),
                    "Admin created directed Membership Upgrade Path " + from.getId() + " -> " + to.getId() + ".");
            return toResponse(path);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This directed Upgrade Path already exists", exception);
        }
    }

    @Transactional
    public MembershipUpgradePathResponse setStatus(Integer id, boolean active, Jwt jwt) {
        requireAdmin(jwt);
        MembershipUpgradePath path = pathRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership Upgrade Path not found"));
        if (active) {
            PlanPair lockedPlans = lockPlans(path.getFromPlan().getId(), path.getToPlan().getId());
            validateConfigurable(lockedPlans.from(), lockedPlans.to());
        }
        if (!java.util.Objects.equals(path.getActive(), active)) {
            path.setActive(active);
            path.setUpdatedAt(LocalDateTime.now(clock.withZone(java.time.ZoneOffset.UTC)));
            path = pathRepository.save(path);
            auditLogs.record("STATUS_CHANGE", "MEMBERSHIP_UPGRADE_PATH", id,
                    active ? "Membership Upgrade Path enabled." : "Membership Upgrade Path disabled.");
        }
        return toResponse(path);
    }

    private void validateConfigurable(MembershipPlan from, MembershipPlan to) {
        if (!"ACTIVE".equalsIgnoreCase(from.getStatus()) || !"ACTIVE".equalsIgnoreCase(to.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Both Plans must be ACTIVE to create or enable an Upgrade Path");
        }
        if (!validPlan(from) || !validPlan(to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Both Plans must have valid price, duration, discount, and entitlement values");
        }
        if (to.getPrice().compareTo(from.getPrice()) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The target Plan price must be greater than the source Plan price");
        }
    }

    private boolean validPlan(MembershipPlan plan) {
        return plan.getName() != null && !plan.getName().isBlank()
                && plan.getPrice() != null && plan.getPrice().signum() > 0
                && plan.getDurationMonths() != null && plan.getDurationMonths() > 0
                && validPercent(plan.getCourseDiscountPercent())
                && validPercent(plan.getPersonalTrainingDiscountPercent())
                && plan.getAllowPersonalTrainingPlan() != null
                && plan.getAllowOneOnOneCoaching() != null;
    }

    private boolean validPercent(java.math.BigDecimal percent) {
        return percent != null && percent.signum() >= 0 && percent.compareTo(java.math.BigDecimal.valueOf(100)) <= 0;
    }

    private PlanPair lockPlans(Integer fromId, Integer toId) {
        Integer firstId = Math.min(fromId, toId);
        Integer secondId = Math.max(fromId, toId);
        MembershipPlan first = planRepository.findByIdForUpgradeQuote(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership Plan not found with id: " + firstId));
        MembershipPlan second = planRepository.findByIdForUpgradeQuote(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership Plan not found with id: " + secondId));
        return fromId.equals(firstId) ? new PlanPair(first, second) : new PlanPair(second, first);
    }

    private record PlanPair(MembershipPlan from, MembershipPlan to) { }

    private User requireAdmin(Jwt jwt) {
        final Integer actorId;
        try {
            actorId = Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive accounts cannot manage Upgrade Paths");
        }
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can manage Upgrade Paths");
        }
        return actor;
    }

    private MembershipUpgradePathResponse toResponse(MembershipUpgradePath path) {
        return new MembershipUpgradePathResponse(
                path.getId(), path.getFromPlan().getId(), path.getFromPlan().getName(), path.getFromPlan().getPrice(),
                path.getFromPlan().getStatus(), path.getToPlan().getId(), path.getToPlan().getName(), path.getToPlan().getPrice(),
                path.getToPlan().getStatus(),
                path.getActive(), path.getCreatedAt(), path.getUpdatedAt());
    }
}
