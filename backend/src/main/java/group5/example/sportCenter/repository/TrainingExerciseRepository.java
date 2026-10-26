package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.TrainingExercise;

public interface TrainingExerciseRepository extends JpaRepository<TrainingExercise, Integer> {

    List<TrainingExercise> findByPlanIdOrderBySortOrderAscIdAsc(Integer planId);

    @EntityGraph(attributePaths = { "plan", "plan.courseClass", "plan.courseClass.coach" })
    Optional<TrainingExercise> findWithPlanById(Integer id);
}
