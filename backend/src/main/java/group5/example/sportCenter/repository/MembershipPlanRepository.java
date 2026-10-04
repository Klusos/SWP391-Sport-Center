package group5.example.sportCenter.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.MembershipPlan;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Integer> {
    List<MembershipPlan> findByStatus(String status);
    boolean existsByName(String name);
}