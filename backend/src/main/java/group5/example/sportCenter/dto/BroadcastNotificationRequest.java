package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastNotificationRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message must not exceed 5000 characters")
        String message) {
}
