package group5.example.sportCenter.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import group5.example.sportCenter.entity.MembershipUpgrade;

public interface MembershipUpgradeRepository extends JpaRepository<MembershipUpgrade, Integer> {
    @EntityGraph(attributePaths = { "sourceMembership", "targetPlan" })
    Optional<MembershipUpgrade> findByPaymentId(Integer paymentId);

    @EntityGraph(attributePaths = { "sourceMembership", "sourceMembership.member", "sourceMembership.plan",
            "sourceMembership.purchasePayment", "sourceMembership.renewalIntent", "targetPlan", "payment",
            "payment.member", "resultMembership", "resultMembership.plan", "resultMembership.member",
            "resultMembership.purchasePayment" })
    List<MembershipUpgrade> findAllByResultMembership_Id(Integer resultMembershipId);
}
