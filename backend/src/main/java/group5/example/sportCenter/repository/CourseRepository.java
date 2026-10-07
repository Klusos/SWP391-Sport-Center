package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Course;

public interface CourseRepository
        extends JpaRepository<Course, Integer> {

    List<Course> findBySportId(Integer sportId);

    List<Course> findByStatus(String status);

    List<Course> findBySportIdAndStatus(
            Integer sportId,
            String status);
}