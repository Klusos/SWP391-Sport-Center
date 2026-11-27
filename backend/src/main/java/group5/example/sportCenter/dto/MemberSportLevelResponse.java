package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record MemberSportLevelResponse(
        Integer id, Integer memberId, Integer sportId, String level,
        Integer verifiedByCoachId, String verifiedByCoachName, LocalDateTime verifiedAt, LocalDateTime updatedAt) {}
