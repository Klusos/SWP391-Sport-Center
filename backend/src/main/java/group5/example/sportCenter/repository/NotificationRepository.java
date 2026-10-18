package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Integer>, NotificationRepositoryCustom {

    Page<Notification> findByUserId(Integer userId, Pageable pageable);

    long countByUserIdAndReadFalse(Integer userId);

    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllUnreadAsReadForUser(@Param("userId") Integer userId);
}
