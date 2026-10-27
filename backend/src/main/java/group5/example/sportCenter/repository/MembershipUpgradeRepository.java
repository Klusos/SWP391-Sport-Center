package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.MembershipUpgrade;

public interface MembershipUpgradeRepository extends JpaRepository<MembershipUpgrade, Integer> {
    Optional<MembershipUpgrade> findByPaymentId(Integer paymentId);
}
