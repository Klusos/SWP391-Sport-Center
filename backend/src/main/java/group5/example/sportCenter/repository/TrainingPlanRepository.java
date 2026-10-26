package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.TrainingPlan;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Integer> {

    @EntityGraph(attributePaths = { "courseClass", "updatedBy" })
    Optional<TrainingPlan> findByCourseClassId(Integer classId);
}
