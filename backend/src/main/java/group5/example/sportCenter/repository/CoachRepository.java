package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Coach;

public interface CoachRepository
        extends JpaRepository<Coach, Integer> {

    Optional<Coach> findByUserId(Integer userId);

    boolean existsByUserId(Integer userId);
}