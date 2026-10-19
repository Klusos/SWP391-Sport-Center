package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.CourseOffering;

public interface CourseOfferingRepository
        extends JpaRepository<CourseOffering, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offering from CourseOffering offering where offering.id = :id")
    Optional<CourseOffering> findByIdForScheduleUpdate(@Param("id") Integer id);

    List<CourseOffering> findByCourseId(Integer courseId);

    List<CourseOffering> findByStatus(String status);

    List<CourseOffering> findByCourseIdAndStatus(
            Integer courseId,
            String status);
}
