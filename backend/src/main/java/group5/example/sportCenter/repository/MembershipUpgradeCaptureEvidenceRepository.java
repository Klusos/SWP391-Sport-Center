package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.MembershipUpgradeCaptureEvidence;
import jakarta.persistence.LockModeType;

public interface MembershipUpgradeCaptureEvidenceRepository
        extends JpaRepository<MembershipUpgradeCaptureEvidence, Integer> {
    Optional<MembershipUpgradeCaptureEvidence> findByEvidenceKey(String evidenceKey);

    java.util.List<MembershipUpgradeCaptureEvidence> findByUpgradeEvidence_Payment_Id(Integer paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MembershipUpgradeCaptureEvidence e where e.upgradeEvidence.payment.id = :paymentId order by e.id")
    java.util.List<MembershipUpgradeCaptureEvidence> findByPaymentIdForUpdate(@Param("paymentId") Integer paymentId);
}
