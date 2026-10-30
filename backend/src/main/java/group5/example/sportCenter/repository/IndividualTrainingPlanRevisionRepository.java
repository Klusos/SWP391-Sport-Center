package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.IndividualTrainingPlanRevision;

public interface IndividualTrainingPlanRevisionRepository
        extends JpaRepository<IndividualTrainingPlanRevision, Integer> {
    @EntityGraph(attributePaths = "revisedBy")
    Optional<IndividualTrainingPlanRevision> findFirstByPlanIdOrderByRevisionNumberDesc(Integer planId);

    @EntityGraph(attributePaths = "revisedBy")
    List<IndividualTrainingPlanRevision> findByPlanIdOrderByRevisionNumberDesc(Integer planId);
}
