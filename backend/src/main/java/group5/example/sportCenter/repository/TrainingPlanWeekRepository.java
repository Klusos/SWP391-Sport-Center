package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import group5.example.sportCenter.entity.TrainingPlanWeek;

public interface TrainingPlanWeekRepository extends JpaRepository<TrainingPlanWeek, Integer> {
    List<TrainingPlanWeek> findByPlanIdOrderByWeekNumberAscIdAsc(Integer planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select week from TrainingPlanWeek week where week.id = :id")
    Optional<TrainingPlanWeek> findByIdForUpdate(@Param("id") Integer id);
}
