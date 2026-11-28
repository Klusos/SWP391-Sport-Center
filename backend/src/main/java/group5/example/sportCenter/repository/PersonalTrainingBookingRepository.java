package group5.example.sportCenter.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.PersonalTrainingBooking;

public interface PersonalTrainingBookingRepository extends JpaRepository<PersonalTrainingBooking, Integer> {
    @Query("select count(b) from PersonalTrainingBooking b where b.coach.id = :coachId and "
            + "(b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED "
            + "or (b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT "
            + "and b.paymentExpiresAt > :now))")
    long countUnfinishedForCoach(@Param("coachId") Integer coachId, @Param("now") LocalDateTime now);
    @Query("select b from PersonalTrainingBooking b join fetch b.member join fetch b.coach c "
            + "join fetch c.user join fetch b.sport where b.member.id = :memberId order by b.startTime desc")
    List<PersonalTrainingBooking> findAllForMember(@Param("memberId") Integer memberId);

    @Query("select b from PersonalTrainingBooking b join fetch b.member join fetch b.coach c "
            + "join fetch c.user join fetch b.sport where c.id = :coachId order by b.startTime")
    List<PersonalTrainingBooking> findAllForCoach(@Param("coachId") Integer coachId);

    @Query("select b from PersonalTrainingBooking b join fetch b.member join fetch b.coach c "
            + "join fetch c.user join fetch b.sport order by b.startTime desc, b.id desc")
    List<PersonalTrainingBooking> findAllForAdmin();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PersonalTrainingBooking b where b.id = :id")
    Optional<PersonalTrainingBooking> findByIdForLifecycleUpdate(@Param("id") Integer id);

    /** Scalar read so the booking entity is first loaded under its row lock. */
    @Query("select b.member.id from PersonalTrainingBooking b where b.id = :id")
    Optional<Integer> findMemberIdById(@Param("id") Integer id);

    @Query("select b.id from PersonalTrainingBooking b where b.status = "
            + "group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT "
            + "and b.paymentExpiresAt <= :now")
    List<Integer> findExpiredPendingIds(@Param("now") LocalDateTime now);

    @Query("select b.id from PersonalTrainingBooking b where b.status = "
            + "group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED "
            + "and b.endTime >= :cutover and b.endTime <= :dueThrough order by b.endTime, b.id")
    List<Integer> findOutcomeFallbackCandidateIds(@Param("cutover") LocalDateTime cutover,
            @Param("dueThrough") LocalDateTime dueThrough, Pageable pageable);

    @Query("select count(b) > 0 from PersonalTrainingBooking b where b.coach.id = :coachId "
            + "and b.status in (group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT, "
            + "group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED) "
            + "and (b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED "
            + "or b.paymentExpiresAt > :now) "
            + "and b.startTime < :endTime and b.endTime > :startTime")
    boolean existsCoachOverlap(@Param("coachId") Integer coachId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("now") LocalDateTime now);

    @Query("select count(b) > 0 from PersonalTrainingBooking b where b.member.id = :memberId "
            + "and b.status in (group5.example.sportCenter.entity.PersonalTrainingBookingStatus.PENDING_PAYMENT, "
            + "group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED) "
            + "and (b.status = group5.example.sportCenter.entity.PersonalTrainingBookingStatus.CONFIRMED "
            + "or b.paymentExpiresAt > :now) "
            + "and b.startTime < :endTime and b.endTime > :startTime")
    boolean existsMemberOverlap(@Param("memberId") Integer memberId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("now") LocalDateTime now);
}
