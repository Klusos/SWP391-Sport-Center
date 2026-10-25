package group5.example.sportCenter.dto;

public record TrainingExerciseResponse(
        Integer id,
        String name,
        Integer sets,
        Integer reps,
        Integer durationMinutes,
        String notes,
        Integer sortOrder) {
}
