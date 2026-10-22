package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.MembershipPlanUpgrade;

public interface MembershipPlanUpgradeRepository extends JpaRepository<MembershipPlanUpgrade, Integer> {
    List<MembershipPlanUpgrade> findByToPlan_Id(Integer toPlanId);
    @EntityGraph(attributePaths = {"fromPlan", "toPlan"})
    List<MembershipPlanUpgrade> findByFromPlan_IdOrderByIdAsc(Integer fromPlanId);
    @EntityGraph(attributePaths = {"fromPlan", "toPlan"})
    List<MembershipPlanUpgrade> findAllByOrderByIdAsc();
    void deleteByToPlan_Id(Integer toPlanId);
    boolean existsByFromPlan_IdAndToPlan_Id(Integer fromPlanId, Integer toPlanId);

}
