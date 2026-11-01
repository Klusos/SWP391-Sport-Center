package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Integer actorUserId,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String description,
        LocalDateTime createdAt) {
}
