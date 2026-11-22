package group5.example.sportCenter.repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.MembershipRenewalIntent;
import jakarta.persistence.LockModeType;

public interface MembershipRenewalIntentRepository extends JpaRepository<MembershipRenewalIntent, Integer> {
    Optional<MembershipRenewalIntent> findBySourceMembershipId(Integer sourceMembershipId);

    @Query("select case when count(intent) > 0 then true else false end from MembershipRenewalIntent intent "
            + "where intent.sourceMembership.member.id = :memberId and intent.intentStatus = "
            + "group5.example.sportCenter.entity.RenewalIntentStatus.OPEN")
    boolean existsOpenByMemberId(@Param("memberId") Integer memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from MembershipRenewalIntent intent where intent.sourceMembership.id = :sourceMembershipId")
    Optional<MembershipRenewalIntent> findBySourceMembershipIdForUpdate(
            @Param("sourceMembershipId") Integer sourceMembershipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from MembershipRenewalIntent intent where intent.id = :id")
    Optional<MembershipRenewalIntent> findByIdForUpdate(@Param("id") Integer id);

    @Query("select intent.id from MembershipRenewalIntent intent where intent.intentStatus = "
            + "group5.example.sportCenter.entity.RenewalIntentStatus.OPEN and intent.validUntil <= :now "
            + "order by intent.validUntil, intent.id")
    List<Integer> findExpiredOpenIds(@Param("now") LocalDateTime now);

    @Query("select intent.id from MembershipRenewalIntent intent where intent.intentStatus = "
            + "group5.example.sportCenter.entity.RenewalIntentStatus.OPEN and intent.validUntil > :now "
            + "and intent.validUntil <= :through "
            + "order by intent.validUntil, intent.id")
    List<Integer> findOpenIdsForReminder(@Param("now") LocalDateTime now,
            @Param("through") LocalDateTime through);

    @EntityGraph(attributePaths = {"sourceMembership", "sourceMembership.member"})
    Optional<MembershipRenewalIntent> findWithSourceMembershipMemberById(Integer id);

    @EntityGraph(attributePaths = {"sourceMembership", "sourceMembership.member", "targetPlan", "settledPayment"})
    List<MembershipRenewalIntent> findAllBySourceMembership_Member_IdOrderByCreatedAtDesc(Integer memberId);
}
