package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import group5.example.sportCenter.entity.MembershipUpgrade;

public interface MembershipUpgradeRepository extends JpaRepository<MembershipUpgrade, Integer> {
    @EntityGraph(attributePaths = { "sourceMembership", "targetPlan" })
    Optional<MembershipUpgrade> findByPaymentId(Integer paymentId);
}
