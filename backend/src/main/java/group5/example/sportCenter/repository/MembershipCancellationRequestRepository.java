package group5.example.sportCenter.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.MembershipCancellationRequest;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import jakarta.persistence.LockModeType;

public interface MembershipCancellationRequestRepository extends JpaRepository<MembershipCancellationRequest, Integer> {

    boolean existsByMembershipIdAndStatus(Integer membershipId, MembershipCancellationRequestStatus status);

    Optional<MembershipCancellationRequest> findFirstByMembershipIdAndStatus(
            Integer membershipId, MembershipCancellationRequestStatus status);

    List<MembershipCancellationRequest> findByMemberIdOrderByRequestedAtDesc(Integer memberId);

    List<MembershipCancellationRequest> findByStatusOrderByRequestedAtAsc(MembershipCancellationRequestStatus status);

    /** Yêu cầu PENDING của gói đã bắt đầu hoặc không còn ACTIVE: không thể duyệt nữa. */
    @Query("select r from MembershipCancellationRequest r join r.membership m "
            + "where r.status = group5.example.sportCenter.entity.MembershipCancellationRequestStatus.PENDING "
            + "and (m.startDate <= :today or m.status <> 'ACTIVE')")
    List<MembershipCancellationRequest> findPendingNoLongerApprovable(@Param("today") LocalDate today);

    /** Khoá dòng yêu cầu để hai nhân viên không cùng duyệt/từ chối một yêu cầu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MembershipCancellationRequest r where r.id = :id")
    Optional<MembershipCancellationRequest> findByIdForUpdate(@Param("id") Integer id);
}
