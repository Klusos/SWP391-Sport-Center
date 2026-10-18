package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.AttendanceHistory;

public interface AttendanceHistoryRepository extends JpaRepository<AttendanceHistory, Integer> {

    @EntityGraph(attributePaths = "changedBy")
    List<AttendanceHistory> findByAttendanceIdOrderByChangedAtAscIdAsc(Integer attendanceId);
}
