package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

/** The level for the member and sport attached to one authorized assessment. */
public record AssessmentMemberLevelResponse(
        Integer assessmentId,
        Integer memberId,
        Integer sportId,
        String level,
        boolean defaultLevel,
        Integer verifiedByCoachId,
        LocalDateTime verifiedAt,
        LocalDateTime updatedAt) {}
