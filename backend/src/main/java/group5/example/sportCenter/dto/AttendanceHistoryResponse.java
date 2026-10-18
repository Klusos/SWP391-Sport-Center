package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record AttendanceHistoryResponse(
        Integer id,
        Integer attendanceId,
        String oldStatus,
        String newStatus,
        Integer changedById,
        LocalDateTime changedAt,
        String reason) {
}
