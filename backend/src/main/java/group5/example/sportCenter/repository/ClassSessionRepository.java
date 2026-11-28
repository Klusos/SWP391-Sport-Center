package group5.example.sportCenter.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Integer> {

    @Query("select case when count(s) > 0 then true else false end from ClassSession s "
            + "where s.courseClass.courseOffering.id = :offeringId and "
            + "(s.startTime < :rangeStart or s.startTime >= :rangeEndExclusive "
            + "or s.endTime < :rangeStart or s.endTime >= :rangeEndExclusive)")
    boolean existsOutsideOfferingRange(@Param("offeringId") Integer offeringId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEndExclusive") LocalDateTime rangeEndExclusive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClassSession s where s.id = :id")
    Optional<ClassSession> findByIdForAttendanceUpdate(@Param("id") Integer id);

    @Query("select s.id from ClassSession s where upper(s.status) = 'SCHEDULED' and s.endTime <= :now order by s.endTime, s.id")
    List<Integer> findElapsedScheduledIds(@Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "courseClass.courseOffering",
            "courseClass.courseOffering.course", "courseClass.courseOffering.course.sport" })
    List<ClassSession> findAllByOrderByStartTimeAscIdAsc();

    @EntityGraph(attributePaths = { "courseClass", "courseClass.coach", "courseClass.courseOffering",
            "courseClass.courseOffering.course", "courseClass.courseOffering.course.sport" })
    List<ClassSession> findByCourseClass_Coach_IdOrderByStartTimeAscIdAsc(Integer coachId);

    List<ClassSession> findByCourseClassId(Integer classId);

    /** Active class registrations reserve the Member's time for scheduled sessions. */
    @Query("select count(s) > 0 from ClassSession s, CourseRegistration r "
            + "where r.courseClass = s.courseClass and r.member.id = :memberId "
            + "and s.status = 'SCHEDULED' "
            + "and (r.status = :confirmed or (r.status = :pending and r.paymentDeadline > :now)) "
            + "and s.startTime < :endTime and s.endTime > :startTime")
    boolean existsMemberClassOverlap(@Param("memberId") Integer memberId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("confirmed") CourseRegistrationStatus confirmed,
            @Param("pending") CourseRegistrationStatus pending, @Param("now") LocalDateTime now);

    /** Prevents a new registration from overlapping an active PT booking for the Member. */
    @Query("select count(s) > 0 from ClassSession s, PersonalTrainingBooking b "
            + "where s.courseClass.id = :classId and s.status = 'SCHEDULED' and b.member.id = :memberId "
            + "and (b.status = :confirmed or (b.status = :pending and b.paymentExpiresAt > :now)) "
            + "and b.startTime < s.endTime and b.endTime > s.startTime")
    boolean existsClassPtOverlap(@Param("memberId") Integer memberId, @Param("classId") Integer classId,
            @Param("confirmed") PersonalTrainingBookingStatus confirmed,
            @Param("pending") PersonalTrainingBookingStatus pending, @Param("now") LocalDateTime now);

    List<ClassSession> findByCourseClass_CourseOffering_Id(Integer offeringId);

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

    @Query("select case when count(s) > 0 then true else false end from ClassSession s "
            + "where s.facility.id = :facilityId and s.courseClass.courseOffering.course.sport.id = :sportId "
            + "and s.status <> :cancelled and s.endTime > :now")
    boolean existsFutureNonCancelledByFacilityAndCourseSport(@Param("facilityId") Integer facilityId,
            @Param("sportId") Integer sportId, @Param("cancelled") String cancelled,
            @Param("now") LocalDateTime now);

    boolean existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            Integer facilityId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime);

    boolean existsByFacilityIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Integer facilityId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime, Integer excludedId);

    boolean existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            Integer coachId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime);

    boolean existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Integer coachId, String excludedStatus, LocalDateTime endTime, LocalDateTime startTime, Integer excludedId);
}
