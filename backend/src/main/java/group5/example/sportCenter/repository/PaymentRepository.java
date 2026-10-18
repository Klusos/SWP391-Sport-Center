package group5.example.sportCenter.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    List<Payment> findByMemberIdOrderByCreatedAtDesc(Integer memberId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    /** Khoá dòng payment để counter/callback xử lý song song không áp dụng 2 lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.transactionCode = :transactionCode")
    Optional<Payment> findByTransactionCodeForUpdate(@Param("transactionCode") String transactionCode);

    boolean existsByPurposeAndReferenceIdAndStatus(PaymentPurpose purpose, Integer referenceId, PaymentStatus status);

    boolean existsByPurposeAndReferenceIdAndStatusAndIdNot(
            PaymentPurpose purpose, Integer referenceId, PaymentStatus status, Integer id);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.paidAt >= :from AND p.paidAt < :to
        """)
    BigDecimal sumRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT p.purpose, COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.paidAt >= :from AND p.paidAt < :to
        GROUP BY p.purpose
        """)
    List<Object[]> revenueByPurpose(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT p.method, COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.paidAt >= :from AND p.paidAt < :to
        GROUP BY p.method
        """)
    List<Object[]> revenueByMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Member Report: [memberId, memberName, số lần thanh toán membership, tổng tiền]. */
    @Query("""
        SELECT p.member.id, p.member.name, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.purpose = group5.example.sportCenter.entity.PaymentPurpose.MEMBERSHIP
          AND p.paidAt >= :from AND p.paidAt < :to
        GROUP BY p.member.id, p.member.name
        """)
    List<Object[]> memberReport(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Class Registration Report: [courseOfferingId, tên offering, số lượt đăng ký đã thanh toán, tổng tiền]. */
    @Query("""
        SELECT o.id, o.name, COUNT(p), COALESCE(SUM(p.amount), 0)
        FROM Payment p, CourseRegistration r JOIN r.courseClass c JOIN c.courseOffering o
        WHERE r.id = p.referenceId
          AND p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.purpose = group5.example.sportCenter.entity.PaymentPurpose.COURSE_REGISTRATION
          AND p.paidAt >= :from AND p.paidAt < :to
        GROUP BY o.id, o.name
        """)
    List<Object[]> classRegistrationReport(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
