package group5.example.sportCenter.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.BroadcastNotificationRequest;
import group5.example.sportCenter.dto.BroadcastNotificationResponse;
import group5.example.sportCenter.service.NotificationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notifications;

    public AdminNotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/broadcast")
    public BroadcastNotificationResponse broadcast(
            @Valid @RequestBody BroadcastNotificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return notifications.broadcastToAllActiveUsers(request, jwt);
    }
}
