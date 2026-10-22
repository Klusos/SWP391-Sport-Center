package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.MembershipUpgradeCounterReceipt;

public interface MembershipUpgradeCounterReceiptRepository extends JpaRepository<MembershipUpgradeCounterReceipt, Integer> {
    Optional<MembershipUpgradeCounterReceipt> findByPayment_Id(Integer paymentId);
}
