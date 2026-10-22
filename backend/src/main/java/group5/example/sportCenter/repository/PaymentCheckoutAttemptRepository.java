package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.PaymentCheckoutAttempt;
import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import jakarta.persistence.LockModeType;

public interface PaymentCheckoutAttemptRepository extends JpaRepository<PaymentCheckoutAttempt, Integer> {

    interface RenewalAttemptLockKeys {
        Integer getMemberId();
        Integer getSourceMembershipId();
        Integer getPaymentId();
        Integer getAttemptId();
    }

    @Query("select p.member.id as memberId, p.renewalMembership.id as sourceMembershipId, "
            + "p.id as paymentId, a.id as attemptId from PaymentCheckoutAttempt a join a.payment p "
            + "where a.transactionReference = :reference")
    Optional<RenewalAttemptLockKeys> findRenewalLockKeysByTransactionReference(@Param("reference") String reference);

    Optional<PaymentCheckoutAttempt> findByTransactionReference(String transactionReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentCheckoutAttempt a where a.transactionReference = :reference")
    Optional<PaymentCheckoutAttempt> findByTransactionReferenceForUpdate(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentCheckoutAttempt a where a.payment.id = :paymentId order by a.createdAt, a.id")
    List<PaymentCheckoutAttempt> findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(@Param("paymentId") Integer paymentId);

    List<PaymentCheckoutAttempt> findByPayment_IdOrderByCreatedAtAscIdAsc(Integer paymentId);

    Optional<PaymentCheckoutAttempt> findFirstByPayment_IdAndAppliedMembershipIsNotNull(Integer paymentId);

    long countByPayment_IdAndStatus(Integer paymentId, CheckoutAttemptStatus status);
}
