package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import group5.example.sportCenter.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    // Stable lock anchor, including when no assessment or level exists yet.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForAssessmentUpdate(@Param("id") Integer id);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Integer id);
    List<User> findByRole(String role);
    List<User> findByStatus(String status);
}
