package group5.example.sportCenter.controller;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.MarkAllNotificationsReadResponse;
import group5.example.sportCenter.dto.NotificationResponse;
import group5.example.sportCenter.dto.UnreadNotificationCountResponse;
import group5.example.sportCenter.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.listMyNotifications(jwt, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notifications.getMyUnreadCount(jwt);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return notifications.markMyNotificationRead(id, jwt);
    }

    @PatchMapping("/read-all")
    public MarkAllNotificationsReadResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return notifications.markAllMyNotificationsRead(jwt);
    }
}
