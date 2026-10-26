package group5.example.sportCenter.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import group5.example.sportCenter.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.dto.PendingCoursePaymentResponse;
import group5.example.sportCenter.dto.PendingPtPaymentResponse;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Page<Payment> findByStatusAndMethodAndPurpose(PaymentStatus status, PaymentMethod method, PaymentPurpose purpose, Pageable pageable);

    // Scalar projection avoids lazy-loading member/course/class entities for each queue row.
    // referenceId is purpose-scoped; it is not a mapped Payment -> registration association.
    @Query(value = """
        select new group5.example.sportCenter.dto.PendingCoursePaymentResponse(
            p.id, m.id, m.name, r.id, c.id, c.name, cl.id, cl.name,
            p.amount, p.status, r.status, r.paymentDeadline, p.createdAt)
        from Payment p join p.member m, CourseRegistration r
        join r.courseClass cl join cl.courseOffering o join o.course c
        where p.referenceId = r.id and m.id = r.member.id
          and p.method = group5.example.sportCenter.entity.PaymentMethod.COUNTER
          and p.purpose = group5.example.sportCenter.entity.PaymentPurpose.COURSE_REGISTRATION
          and p.status = group5.example.sportCenter.entity.PaymentStatus.PENDING
          and r.status = group5.example.sportCenter.entity.CourseRegistrationStatus.PENDING_PAYMENT
          and r.paymentDeadline > :now
        order by p.createdAt asc, p.id asc
        """, countQuery = """
        select count(p.id)
        from Payment p join p.member m, CourseRegistration r
        join r.courseClass cl join cl.courseOffering o join o.course c
        where p.referenceId = r.id and m.id = r.member.id
          and p.method = group5.example.sportCenter.entity.PaymentMethod.COUNTER
          and p.purpose = group5.example.sportCenter.entity.PaymentPurpose.COURSE_REGISTRATION
          and p.status = group5.example.sportCenter.entity.PaymentStatus.PENDING
          and r.status = group5.example.sportCenter.entity.CourseRegistrationStatus.PENDING_PAYMENT
          and r.paymentDeadline > :now
        """)
    Page<PendingCoursePaymentResponse> findPendingCoursePayments(@Param("now") LocalDateTime now, Pageable pageable);

    @Query(value = """
        select new group5.example.sportCenter.dto.PendingPtPaymentResponse(
            p.id, b.id, m.id, m.name, b.sport.id, b.sport.name, b.coach.id, b.coach.user.name,
            b.startTime, b.endTime, p.amount, p.status, b.status, b.paymentExpiresAt, p.createdAt)
        from Payment p join p.member m, PersonalTrainingBooking b
        where p.referenceId = b.id and m.id = b.member.id
          and p.method = group5.example.sportCenter.entity.PaymentMethod.COUNTER
          and p.purpose = group5.example.sportCenter.entity.PaymentPurpose.PT_BOOKING
          and p.status = group5.example.sportCenter.entity.PaymentStatus.PENDING
          and b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT
          and b.paymentExpiresAt > :now
        order by p.createdAt asc, p.id asc
        """, countQuery = """
        select count(p.id)
        from Payment p join p.member m, PersonalTrainingBooking b
        where p.referenceId = b.id and m.id = b.member.id
          and p.method = group5.example.sportCenter.entity.PaymentMethod.COUNTER
          and p.purpose = group5.example.sportCenter.entity.PaymentPurpose.PT_BOOKING
          and p.status = group5.example.sportCenter.entity.PaymentStatus.PENDING
          and b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT
          and b.paymentExpiresAt > :now
        """)
    Page<PendingPtPaymentResponse> findPendingPtPayments(@Param("now") LocalDateTime now, Pageable pageable);

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

    boolean existsByMember_IdAndPurposeAndReferenceIdAndStatus(
            Integer memberId, PaymentPurpose purpose, Integer referenceId, PaymentStatus status);

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

    /** Daily verified revenue, grouped by purpose for DAY/MONTH/YEAR report buckets. */
    @Query("""
        SELECT FUNCTION('year', p.paidAt), FUNCTION('month', p.paidAt), FUNCTION('day', p.paidAt),
               p.purpose, SUM(p.amount)
        FROM Payment p
        WHERE p.status = group5.example.sportCenter.entity.PaymentStatus.SUCCESS
          AND p.paidAt >= :from AND p.paidAt < :to
        GROUP BY FUNCTION('year', p.paidAt), FUNCTION('month', p.paidAt), FUNCTION('day', p.paidAt), p.purpose
        """)
    List<Object[]> revenueBySettlementDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

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
