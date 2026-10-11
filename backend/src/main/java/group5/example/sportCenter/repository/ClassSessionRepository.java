package group5.example.sportCenter.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.ClassSession;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Integer> {

    List<ClassSession> findByCourseClassId(Integer classId);

    long countByCourseClassIdAndStatusNot(Integer classId, String excludedStatus);

    boolean existsByFacilityIdAndStatusAndEndTimeAfter(
            Integer facilityId, String status, LocalDateTime endTime);

    boolean existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            Integer facilityId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime);

    boolean existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Integer facilityId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime, Integer excludedId);

    boolean existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            Integer coachId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime);

    boolean existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Integer coachId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime, Integer excludedId);
}
