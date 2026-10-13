package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Size;

public record CompleteConsultationRequest(@Size(max = 1000) String resolutionNote) {
}
