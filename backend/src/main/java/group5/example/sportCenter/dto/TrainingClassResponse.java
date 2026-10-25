package group5.example.sportCenter.dto;

import java.time.LocalDate;

public record TrainingClassResponse(
        Integer classId,
        String className,
        String classStatus,
        Integer courseOfferingId,
        String courseOfferingName,
        LocalDate startDate,
        LocalDate endDate,
        Integer courseId,
        String courseName,
        Integer sportId,
        String sportName,
        Integer coachId,
        String coachName,
        long confirmedMembers,
        long totalSessions,
        long completedSessions,
        boolean hasPlan,
        String planGoal) {
}
