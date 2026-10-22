package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(@NotNull SupportTicketStatus status) {
}
