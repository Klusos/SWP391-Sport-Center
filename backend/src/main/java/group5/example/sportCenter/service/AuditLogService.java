package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.AuditLogResponse;
import group5.example.sportCenter.entity.AuditLog;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public AuditLogService(AuditLogRepository auditLogs, UserRepository users) {
        this.auditLogs = auditLogs;
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(String action, String entityType, Object entityId, String description) {
        String checkedAction = required(action, 50, "action");
        String checkedType = required(entityType, 60, "entityType");
        String checkedDescription = required(description, 500, "description");
        String checkedId = entityId == null ? null : required(entityId.toString(), 64, "entityId");

        Actor actor = currentActor();
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actor.userId());
        entry.setActorRole(actor.role());
        entry.setAction(checkedAction);
        entry.setEntityType(checkedType);
        entry.setEntityId(checkedId);
        entry.setDescription(checkedDescription);
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return auditLogs.insert(entry);
    }

    @Transactional(readOnly = true)
    public boolean existsForActor(Integer userId) {
        return auditLogs.existsByActorUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            org.springframework.security.oauth2.jwt.Jwt jwt,
            int page,
            int size,
            Integer actorUserId,
            String action,
            String entityType,
            String entityId,
            OffsetDateTime from,
            OffsetDateTime to) {
        User actor = authenticatedUser(jwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can view audit logs");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be nonnegative and size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be later than to");
        }
        String normalizedAction = optional(action, 50, "action");
        String normalizedType = optional(entityType, 60, "entityType");
        String normalizedEntityId = filter(entityId, 64, "entityId", false);
        LocalDateTime fromUtc = from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();

        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (actorUserId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        if (normalizedAction != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), normalizedAction));
        if (normalizedType != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), normalizedType));
        if (normalizedEntityId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), normalizedEntityId));
        if (fromUtc != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromUtc));
        if (toUtc != null) spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), toUtc));

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return auditLogs.findAll(spec, PageRequest.of(page, size, sort)).map(AuditLogService::response);
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            // Allows trusted, non-HTTP internal calls to record explicitly as SYSTEM. API mutations are secured
            // before reaching this service and resolve their actor from the authenticated JWT context.
            return new Actor(null, "SYSTEM");
        }
        User user = authenticatedUser(jwtAuthentication.getToken());
        String role = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(value -> value.startsWith("ROLE_")).map(value -> value.substring(5)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated role is required"));
        if (!role.equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated role does not match the current account");
        }
        return new Actor(user.getId(), role.toUpperCase(Locale.ROOT));
    }

    private User authenticatedUser(org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        final Integer id;
        try { id = Integer.valueOf(jwt.getSubject()); }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject"); }
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access audit logs");
        return user;
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + max + " characters");
        }
        return value;
    }

    private static String optional(String value, int max, String name) {
        return filter(value, max, name, true);
    }

    private static String filter(String value, int max, String name, boolean uppercase) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is too long");
        return uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private static AuditLogResponse response(AuditLog entry) {
        return new AuditLogResponse(entry.getId(), entry.getActorUserId(), entry.getActorRole(), entry.getAction(),
                entry.getEntityType(), entry.getEntityId(), entry.getDescription(), entry.getCreatedAt());
    }

    private record Actor(Integer userId, String role) { }
}
