package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.CourseClass;

public interface CourseClassRepository
        extends JpaRepository<CourseClass, Integer> {

    List<CourseClass> findByCourseOfferingId(
            Integer courseOfferingId);

    List<CourseClass> findByCoachId(Integer coachId);

    List<CourseClass> findByStatus(String status);
}