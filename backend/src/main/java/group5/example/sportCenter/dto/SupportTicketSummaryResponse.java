package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record SupportTicketSummaryResponse(
        Integer id, Integer memberId, String memberName, String subject, String category,
        String status, Integer assignedStaffId, String assignedStaffName,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
