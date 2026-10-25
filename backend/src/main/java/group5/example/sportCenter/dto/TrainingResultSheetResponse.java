package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrainingResultSheetResponse(
        Integer sessionId,
        Integer classId,
        String className,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        boolean canRecordResults,
        List<TrainingResultRowResponse> members) {
}
