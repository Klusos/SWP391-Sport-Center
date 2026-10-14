package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Integer> {
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
