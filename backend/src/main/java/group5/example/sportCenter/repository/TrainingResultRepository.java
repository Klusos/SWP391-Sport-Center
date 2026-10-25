package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.TrainingResult;

public interface TrainingResultRepository extends JpaRepository<TrainingResult, Integer> {

    @EntityGraph(attributePaths = { "member", "recordedBy" })
    List<TrainingResult> findByClassSessionId(Integer classSessionId);

    Optional<TrainingResult> findByClassSessionIdAndMemberId(Integer classSessionId, Integer memberId);

    @EntityGraph(attributePaths = { "classSession", "classSession.courseClass", "recordedBy" })
    List<TrainingResult> findByMemberIdOrderByClassSession_StartTimeDescIdDesc(Integer memberId);
}
