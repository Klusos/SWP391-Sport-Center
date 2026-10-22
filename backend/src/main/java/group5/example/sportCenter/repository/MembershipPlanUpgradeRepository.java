package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.MembershipPlanUpgrade;

public interface MembershipPlanUpgradeRepository extends JpaRepository<MembershipPlanUpgrade, Integer> {
    List<MembershipPlanUpgrade> findByToPlan_Id(Integer toPlanId);
    List<MembershipPlanUpgrade> findAllByOrderByIdAsc();
    void deleteByToPlan_Id(Integer toPlanId);
}
