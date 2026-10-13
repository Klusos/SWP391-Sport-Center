package group5.example.sportCenter.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ClassSessionResponse(
        Integer id,
        Integer classId,
        String className,
        Integer facilityId,
        String facilityName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        List<String> warnings) {
}
