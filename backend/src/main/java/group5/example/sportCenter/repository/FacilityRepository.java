package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Facility;

public interface FacilityRepository
        extends JpaRepository<Facility, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id")
    Optional<Facility> findByIdForScheduleUpdate(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"sport", "sports"})
    List<Facility> findDistinctBySports_Id(Integer sportId);

    @EntityGraph(attributePaths = {"sport", "sports"})
    List<Facility> findByStatus(String status);

    @EntityGraph(attributePaths = {"sport", "sports"})
    List<Facility> findDistinctBySports_IdAndStatus(Integer sportId, String status);

    @EntityGraph(attributePaths = {"sport", "sports"})
    List<Facility> findDistinctByStatusAndSports_Status(String status, String sportStatus);

    @EntityGraph(attributePaths = {"sport", "sports"})
    List<Facility> findAll();

    @EntityGraph(attributePaths = {"sport", "sports"})
    Optional<Facility> findById(Integer id);
}
