package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record SupportTicketMessageResponse(
        Integer id, Integer senderId, String senderName, String senderRole,
        String message, LocalDateTime createdAt) {
}
