package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record SkillAssessmentResponse(
        Integer id, Integer memberId, Integer sportId, Integer assignedCoachId,
        Integer requestedByUserId, String status, String assessedLevel, String notes,
        LocalDateTime createdAt, LocalDateTime assessedAt, LocalDateTime cancelledAt) {}
