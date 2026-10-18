package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Integer id,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt) {
}
