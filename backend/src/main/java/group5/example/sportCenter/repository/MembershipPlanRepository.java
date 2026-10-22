package group5.example.sportCenter.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import group5.example.sportCenter.entity.MembershipPlan;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Integer> {
    List<MembershipPlan> findByStatus(String status);
    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MembershipPlan p where p.id = :id")
    java.util.Optional<MembershipPlan> findByIdForUpgradeUpdate(@Param("id") Integer id);
}
