package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.*;

public interface SkillAssessmentRepository extends JpaRepository<SkillAssessment, Integer> {
    boolean existsByMemberIdAndSportIdAndStatus(Integer memberId, Integer sportId, AssessmentStatus status);
    List<SkillAssessment> findAllByOrderByCreatedAtDescIdDesc();
    List<SkillAssessment> findByMemberIdOrderByCreatedAtDescIdDesc(Integer memberId);
    List<SkillAssessment> findByAssignedCoachIdOrderByCreatedAtDescIdDesc(Integer coachId);
    // Scalar lookup avoids loading stale assessment state before the member lock.
    @Query("select a.member.id from SkillAssessment a where a.id = :id")
    Optional<Integer> findMemberIdByAssessmentId(@Param("id") Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SkillAssessment a where a.id = :id")
    Optional<SkillAssessment> findByIdForUpdate(@Param("id") Integer id);
}
