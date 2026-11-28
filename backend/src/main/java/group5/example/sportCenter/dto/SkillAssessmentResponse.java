package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record SkillAssessmentResponse(
        Integer id, Integer memberId, String memberName, Integer sportId, String sportName,
        Integer assignedCoachId, String assignedCoachName,
        Integer requestedByUserId, String status, String assessedLevel, String notes,
        LocalDateTime createdAt, LocalDateTime assessedAt, LocalDateTime cancelledAt,
        Integer consultationRequestId) {}
