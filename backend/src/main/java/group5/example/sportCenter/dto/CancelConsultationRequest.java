package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelConsultationRequest(@NotBlank @Size(max = 1000) String reason) {
}
