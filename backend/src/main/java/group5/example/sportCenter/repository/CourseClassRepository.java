package group5.example.sportCenter.repository;

import java.util.List;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.CourseClass;

public interface CourseClassRepository
        extends JpaRepository<CourseClass, Integer> {

    List<CourseClass> findByCourseOfferingId(
            Integer courseOfferingId);

    List<CourseClass> findByCoachId(Integer coachId);

    List<CourseClass> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select courseClass from CourseClass courseClass where courseClass.id = :id")
    Optional<CourseClass> findByIdForRegistrationUpdate(@Param("id") Integer id);
}
