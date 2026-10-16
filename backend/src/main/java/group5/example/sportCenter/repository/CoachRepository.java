package group5.example.sportCenter.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.Coach;

public interface CoachRepository
        extends JpaRepository<Coach, Integer> {

    Optional<Coach> findByUserId(Integer userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coach c where c.id = :id")
    Optional<Coach> findByIdForBookingUpdate(@Param("id") Integer id);

    boolean existsByUserId(Integer userId);
}
