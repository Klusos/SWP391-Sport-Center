package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.IndividualTrainingPlanExercise;

public interface IndividualTrainingPlanExerciseRepository
        extends JpaRepository<IndividualTrainingPlanExercise, Integer> {
    List<IndividualTrainingPlanExercise> findByWeekIdOrderBySortOrderAscIdAsc(Integer weekId);
}
