package group5.example.sportCenter.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClassSessionGenerationPreviewResponse(
        Integer courseClassId,
        String courseClassName,
        String offeringName,
        LocalDate offeringStartDate,
        LocalDate offeringEndDate,
        Integer coachId,
        String coachName,
        Integer facilityId,
        String facilityName,
        Integer sessionDurationMinutes,
        Integer plannedSessions,
        long existingSessionCount,
        long remainingSessionCount,
        int requestedSessionCount,
        List<Candidate> candidates,
        List<String> conflicts,
        boolean canGenerate) {

    public record Candidate(LocalDateTime startTime, LocalDateTime endTime, List<String> conflicts) { }
}
