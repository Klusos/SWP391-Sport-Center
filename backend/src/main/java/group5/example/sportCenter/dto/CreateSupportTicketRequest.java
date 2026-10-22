package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.SupportTicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank @Size(max = 160) String subject,
        @NotNull SupportTicketCategory category,
        @NotBlank @Size(max = 10000) String description) {
}
