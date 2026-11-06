package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.IndividualTrainingPlan;

public interface IndividualTrainingPlanRepository extends JpaRepository<IndividualTrainingPlan, Integer> {
    boolean existsByCourseClassIdAndMemberId(Integer classId, Integer memberId);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    Optional<IndividualTrainingPlan> findDetailedById(Integer id);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    List<IndividualTrainingPlan> findByCourseClassIdOrderByIdAsc(Integer classId);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    List<IndividualTrainingPlan> findByMemberIdOrderByUpdatedAtDescIdDesc(Integer memberId);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "member", "createdBy" })
    @Query("select plan from IndividualTrainingPlan plan "
            + "where plan.member.id = :memberId and exists ("
            + "select registration.id from CourseRegistration registration "
            + "where registration.member.id = :memberId "
            + "and registration.courseClass.id = plan.courseClass.id "
            + "and registration.status = :status) "
            + "order by plan.updatedAt desc, plan.id desc")
    List<IndividualTrainingPlan> findByMemberIdAndRegistrationStatus(
            @Param("memberId") Integer memberId, @Param("status") CourseRegistrationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from IndividualTrainingPlan plan where plan.id = :id")
    Optional<IndividualTrainingPlan> findByIdForUpdate(@Param("id") Integer id);
}
