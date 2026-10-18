package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass" })
    List<Attendance> findByClassSessionId(Integer classSessionId);

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass" })
    Optional<Attendance> findByClassSessionIdAndMemberId(Integer classSessionId, Integer memberId);

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass",
            "classSession.courseClass.courseOffering", "classSession.courseClass.courseOffering.course" })
    List<Attendance> findByMemberIdOrderByMarkedAtDescIdDesc(Integer memberId);
}
