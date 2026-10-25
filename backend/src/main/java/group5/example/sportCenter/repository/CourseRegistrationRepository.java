package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Integer> {

    @EntityGraph(attributePaths = "member")
    List<CourseRegistration> findByCourseClassIdAndStatus(Integer classId, CourseRegistrationStatus status);

    @EntityGraph(attributePaths = "member")
    Optional<CourseRegistration> findByMemberIdAndCourseClassIdAndStatus(
            Integer memberId, Integer classId, CourseRegistrationStatus status);

    /** Serializes concurrent payments for the same registration (Flow 3). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from CourseRegistration registration where registration.id = :id")
    Optional<CourseRegistration> findByIdForPaymentUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from CourseRegistration registration where registration.id = :id")
    Optional<CourseRegistration> findByIdForUpdate(@Param("id") Integer id);

    @Query("select registration.id from CourseRegistration registration "
            + "where registration.status = :status and registration.paymentDeadline <= :now "
            + "order by registration.paymentDeadline, registration.id")
    List<Integer> findDueIds(@Param("status") CourseRegistrationStatus status,
            @Param("now") java.time.LocalDateTime now, Pageable pageable);

    @Query("select registration.id from CourseRegistration registration "
            + "where registration.courseClass.id = :classId and registration.status = :status "
            + "and registration.paymentDeadline <= :now order by registration.id")
    List<Integer> findDueIdsByClass(@Param("classId") Integer classId,
            @Param("status") CourseRegistrationStatus status, @Param("now") java.time.LocalDateTime now);

    @Query("select registration.id from CourseRegistration registration "
            + "where registration.member.id = :memberId and registration.status = :status "
            + "and registration.paymentDeadline <= :now order by registration.id")
    List<Integer> findDueIdsByMember(@Param("memberId") Integer memberId,
            @Param("status") CourseRegistrationStatus status, @Param("now") java.time.LocalDateTime now);

    boolean existsByMemberIdAndCourseClassIdAndStatus(
            Integer memberId, Integer classId, CourseRegistrationStatus status);

    boolean existsByMemberIdAndCourseClass_CourseOffering_IdAndStatus(
            Integer memberId, Integer offeringId, CourseRegistrationStatus status);

    @Query("select count(registration) from CourseRegistration registration "
            + "where registration.courseClass.id = :classId and registration.status = :status")
    long countByClassIdAndStatus(@Param("classId") Integer classId,
            @Param("status") CourseRegistrationStatus status);

    @Query("select count(registration) from CourseRegistration registration "
            + "where registration.courseClass.id = :classId and (registration.status = :confirmed "
            + "or (registration.status = :pending and registration.paymentDeadline > :now))")
    long countOccupiedSeats(@Param("classId") Integer classId,
            @Param("confirmed") CourseRegistrationStatus confirmed,
            @Param("pending") CourseRegistrationStatus pending,
            @Param("now") java.time.LocalDateTime now);

    List<CourseRegistration> findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(
            Integer memberId, CourseRegistrationStatus status);

    List<CourseRegistration> findByMemberIdOrderByRegisteredAtDescIdDesc(Integer memberId);

    List<CourseRegistration> findAllByOrderByRegisteredAtDescIdDesc();
}
