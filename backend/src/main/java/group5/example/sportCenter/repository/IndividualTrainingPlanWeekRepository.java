package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.IndividualTrainingPlanWeek;

public interface IndividualTrainingPlanWeekRepository extends JpaRepository<IndividualTrainingPlanWeek, Integer> {
    List<IndividualTrainingPlanWeek> findByRevisionIdOrderByWeekNumberAscIdAsc(Integer revisionId);
}
