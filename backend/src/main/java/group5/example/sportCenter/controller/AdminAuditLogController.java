package group5.example.sportCenter.controller;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.AuditLogResponse;
import group5.example.sportCenter.service.AuditLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {
    private final AuditLogService auditLogs;

    public AdminAuditLogController(AuditLogService auditLogs) {
        this.auditLogs = auditLogs;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Integer actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return auditLogs.list(jwt, page, size, actorUserId, action, entityType, entityId, from, to);
    }
}
