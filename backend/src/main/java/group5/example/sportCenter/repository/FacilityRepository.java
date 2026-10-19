package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Facility;

public interface FacilityRepository
        extends JpaRepository<Facility, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id")
    Optional<Facility> findByIdForScheduleUpdate(@Param("id") Integer id);

    List<Facility> findBySportId(Integer sportId);

    List<Facility> findByStatus(String status);

    List<Facility> findBySportIdAndStatus(
            Integer sportId,
            String status);

    List<Facility> findByStatusAndSportStatus(String status, String sportStatus);
}
