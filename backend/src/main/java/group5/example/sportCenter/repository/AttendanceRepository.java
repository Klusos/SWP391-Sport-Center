package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceStatus;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    @Query("select case when count(a) > 0 then true else false end from Attendance a "
            + "where a.member.id = :memberId "
            + "and a.classSession.courseClass.id = :classId "
            + "and a.status = :attendanceStatus "
            + "and a.classSession.status = :sessionStatus")
    boolean existsQualifyingCompletedSessionAttendance(
            @Param("memberId") Integer memberId,
            @Param("classId") Integer classId,
            @Param("attendanceStatus") AttendanceStatus attendanceStatus,
            @Param("sessionStatus") String sessionStatus);

    @Query("select distinct a.member.id from Attendance a "
            + "where a.classSession.courseClass.id = :classId "
            + "and a.member.id in :memberIds "
            + "and a.status = :attendanceStatus "
            + "and a.classSession.status = :sessionStatus")
    List<Integer> findMemberIdsWithQualifyingCompletedSessionAttendance(
            @Param("classId") Integer classId,
            @Param("memberIds") List<Integer> memberIds,
            @Param("attendanceStatus") AttendanceStatus attendanceStatus,
            @Param("sessionStatus") String sessionStatus);

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass" })
    List<Attendance> findByClassSessionId(Integer classSessionId);

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass" })
    Optional<Attendance> findByClassSessionIdAndMemberId(Integer classSessionId, Integer memberId);

    @EntityGraph(attributePaths = { "member", "markedBy", "classSession", "classSession.courseClass",
            "classSession.courseClass.courseOffering", "classSession.courseClass.courseOffering.course" })
    List<Attendance> findByMemberIdOrderByMarkedAtDescIdDesc(Integer memberId);
}
