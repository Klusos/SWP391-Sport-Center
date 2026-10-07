package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.CourseOffering;

public interface CourseOfferingRepository
        extends JpaRepository<CourseOffering, Integer> {

    List<CourseOffering> findByCourseId(Integer courseId);

    List<CourseOffering> findByStatus(String status);

    List<CourseOffering> findByCourseIdAndStatus(
            Integer courseId,
            String status);
}