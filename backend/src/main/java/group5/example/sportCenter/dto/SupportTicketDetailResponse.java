package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SupportTicketDetailResponse(
        Integer id, Integer memberId, String memberName, String subject, String category,
        String description, String status, Integer assignedStaffId, String assignedStaffName,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime resolvedAt,
        LocalDateTime closedAt, List<SupportTicketMessageResponse> messages) {
}
