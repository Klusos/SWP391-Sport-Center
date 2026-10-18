package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Integer> {
    /** Serializes concurrent payments for the same registration (Flow 3). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from CourseRegistration registration where registration.id = :id")
    Optional<CourseRegistration> findByIdForPaymentUpdate(@Param("id") Integer id);

    boolean existsByMemberIdAndCourseClassIdAndStatus(
            Integer memberId, Integer classId, CourseRegistrationStatus status);

    boolean existsByMemberIdAndCourseClass_CourseOffering_IdAndStatus(
            Integer memberId, Integer offeringId, CourseRegistrationStatus status);

    @Query("select count(registration) from CourseRegistration registration "
            + "where registration.courseClass.id = :classId and registration.status = :status")
    long countByClassIdAndStatus(@Param("classId") Integer classId,
            @Param("status") CourseRegistrationStatus status);

    List<CourseRegistration> findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(
            Integer memberId, CourseRegistrationStatus status);

    List<CourseRegistration> findAllByOrderByRegisteredAtDescIdDesc();
}
