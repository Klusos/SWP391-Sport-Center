package group5.example.sportCenter.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.ClassSession;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Integer> {

    List<ClassSession> findByCourseClassId(Integer classId);

    @Query("select distinct s from ClassSession s "
            + "join fetch s.courseClass courseClass "
            + "left join fetch s.facility "
            + "where courseClass.id in :classIds order by s.startTime, s.id")
    List<ClassSession> findByClassIds(@Param("classIds") List<Integer> classIds);

    @Query("select distinct s from ClassSession s "
            + "join fetch s.courseClass courseClass "
            + "where courseClass.id in :classIds and s.status = :status and s.endTime > :now")
    List<ClassSession> findUpcomingByClassIdsAndStatus(@Param("classIds") List<Integer> classIds,
            @Param("status") String status, @Param("now") LocalDateTime now);

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
