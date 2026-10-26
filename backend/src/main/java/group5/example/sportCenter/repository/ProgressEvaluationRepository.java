package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.ProgressEvaluation;

public interface ProgressEvaluationRepository extends JpaRepository<ProgressEvaluation, Integer> {

    @EntityGraph(attributePaths = { "member", "evaluatedBy" })
    List<ProgressEvaluation> findByCourseClassIdOrderByEvaluatedAtDescIdDesc(Integer classId);

    @EntityGraph(attributePaths = { "courseClass", "evaluatedBy" })
    List<ProgressEvaluation> findByMemberIdOrderByEvaluatedAtDescIdDesc(Integer memberId);
}
