package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.IndividualTrainingPlan;

public interface IndividualTrainingPlanRepository extends JpaRepository<IndividualTrainingPlan, Integer> {
    boolean existsByCourseClassIdAndMemberId(Integer classId, Integer memberId);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    Optional<IndividualTrainingPlan> findDetailedById(Integer id);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    List<IndividualTrainingPlan> findByCourseClassIdOrderByIdAsc(Integer classId);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    List<IndividualTrainingPlan> findByMemberIdOrderByUpdatedAtDescIdDesc(Integer memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from IndividualTrainingPlan plan where plan.id = :id")
    Optional<IndividualTrainingPlan> findByIdForUpdate(@Param("id") Integer id);
}
