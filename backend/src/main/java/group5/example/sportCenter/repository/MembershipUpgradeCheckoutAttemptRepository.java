package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.MembershipUpgradeCheckoutAttempt;
import jakarta.persistence.LockModeType;

public interface MembershipUpgradeCheckoutAttemptRepository extends JpaRepository<MembershipUpgradeCheckoutAttempt, Integer> {
    Optional<MembershipUpgradeCheckoutAttempt> findByTransactionReference(String reference);
    Optional<MembershipUpgradeCheckoutAttempt> findByProviderTransactionId(String providerTransactionId);
    List<MembershipUpgradeCheckoutAttempt> findByPayment_IdOrderByCreatedAtUtcDescIdDesc(Integer paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MembershipUpgradeCheckoutAttempt a where a.payment.id = :paymentId order by a.id")
    List<MembershipUpgradeCheckoutAttempt> findByPaymentIdForUpdate(@Param("paymentId") Integer paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MembershipUpgradeCheckoutAttempt a where a.transactionReference = :reference")
    Optional<MembershipUpgradeCheckoutAttempt> findByReferenceForUpdate(@Param("reference") String reference);
}
