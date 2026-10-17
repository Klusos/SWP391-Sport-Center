package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Facility;

public interface FacilityRepository
        extends JpaRepository<Facility, Integer> {

    List<Facility> findBySportId(Integer sportId);

    List<Facility> findByStatus(String status);

    List<Facility> findBySportIdAndStatus(
            Integer sportId,
            String status);

    List<Facility> findByStatusAndSportStatus(String status, String sportStatus);
}
