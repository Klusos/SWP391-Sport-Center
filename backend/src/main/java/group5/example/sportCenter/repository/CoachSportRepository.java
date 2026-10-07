package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.CoachSport;

public interface CoachSportRepository
        extends JpaRepository<CoachSport, Integer> {

    List<CoachSport> findByCoachId(Integer coachId);

    List<CoachSport> findBySportId(Integer sportId);

    Optional<CoachSport> findByCoachIdAndSportId(
            Integer coachId,
            Integer sportId);

    boolean existsByCoachIdAndSportId(
            Integer coachId,
            Integer sportId);
}