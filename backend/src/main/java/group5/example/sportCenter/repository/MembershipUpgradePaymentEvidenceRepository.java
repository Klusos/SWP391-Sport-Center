package group5.example.sportCenter.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.MembershipUpgradePaymentEvidence;

public interface MembershipUpgradePaymentEvidenceRepository
        extends JpaRepository<MembershipUpgradePaymentEvidence, Integer> {
    Optional<MembershipUpgradePaymentEvidence> findByPayment_Id(Integer paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MembershipUpgradePaymentEvidence e where e.payment.id = :paymentId")
    Optional<MembershipUpgradePaymentEvidence> findByPaymentIdForUpdate(@Param("paymentId") Integer paymentId);
}
