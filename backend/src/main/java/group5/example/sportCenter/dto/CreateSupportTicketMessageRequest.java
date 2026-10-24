package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketMessageRequest(@NotBlank @Size(max = 10000) String message) {
}
