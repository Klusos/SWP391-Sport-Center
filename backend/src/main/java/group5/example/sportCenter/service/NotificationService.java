package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MarkAllNotificationsReadResponse;
import group5.example.sportCenter.dto.BroadcastNotificationRequest;
import group5.example.sportCenter.dto.BroadcastNotificationResponse;
import group5.example.sportCenter.dto.NotificationResponse;
import group5.example.sportCenter.dto.UnreadNotificationCountResponse;
import group5.example.sportCenter.entity.Notification;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class NotificationService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "RECEPTIONIST", "COACH", "MEMBER");

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMyNotifications(Jwt jwt, int page, int size) {
        User owner = authenticatedUser(jwt);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return notifications.findByUserId(owner.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getMyUnreadCount(Jwt jwt) {
        User owner = authenticatedUser(jwt);
        return new UnreadNotificationCountResponse(
                notifications.countByUserIdAndReadFalse(owner.getId()));
    }

    @Transactional
    public NotificationResponse markMyNotificationRead(Integer notificationId, Jwt jwt) {
        User owner = authenticatedUser(jwt);
        Notification notification = notifications.findByIdAndUserId(notificationId, owner.getId())
                .orElseThrow(() -> inaccessible(notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
        }
        return toResponse(notification);
    }

    @Transactional
    public MarkAllNotificationsReadResponse markAllMyNotificationsRead(Jwt jwt) {
        User owner = authenticatedUser(jwt);
        int updated = notifications.markAllUnreadAsReadForUser(owner.getId());
        return new MarkAllNotificationsReadResponse(updated);
    }

    /**
     * Sends one personal notification to every eligible ACTIVE account. The user ID query defines
     * the recipient set for this transaction; concurrent status changes after that query may mean
     * a user deactivated during the broadcast still receives it, or a newly activated user does not.
     */
    @Transactional
    public BroadcastNotificationResponse broadcastToAllActiveUsers(BroadcastNotificationRequest request, Jwt jwt) {
        User actor = authenticatedUser(jwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can broadcast notifications");
        }

        List<Integer> recipientIds = users.findEligibleActiveUserIds();
        if (recipientIds.isEmpty()) {
            return new BroadcastNotificationResponse(0);
        }

        LocalDateTime createdAt = LocalDateTime.now();
        List<Notification> batch = recipientIds.stream().map(userId -> {
            Notification notification = new Notification();
            notification.setUser(users.getReferenceById(userId));
            notification.setTitle(request.title());
            notification.setMessage(request.message());
            notification.setRead(false);
            notification.setCreatedAt(createdAt);
            return notification;
        }).toList();

        int persisted = notifications.insertBroadcastNotifications(batch);
        if (persisted != recipientIds.size()) {
            throw new IllegalStateException("Broadcast did not persist one notification for every eligible user");
        }
        return new BroadcastNotificationResponse(persisted);
    }

    /** Internal event-integration seam; there is intentionally no client-facing send endpoint. */
    @Transactional
    public NotificationResponse createForUser(Integer userId, String title, String message) {
        if (userId == null || title == null || title.isBlank() || title.length() > 150
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification user, title, and message are required; title is limited to 150 characters");
        }
        User owner = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        Notification notification = new Notification();
        notification.setUser(owner);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return toResponse(notifications.save(notification));
    }

    private User authenticatedUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
        final Integer userId;
        try {
            userId = Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access notifications");
        }
        if (!ALLOWED_ROLES.contains(user.getRole().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot access notifications");
        }
        return user;
    }

    private ResourceNotFoundException inaccessible(Integer notificationId) {
        return new ResourceNotFoundException("Notification not found with id: " + notificationId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getTitle(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
