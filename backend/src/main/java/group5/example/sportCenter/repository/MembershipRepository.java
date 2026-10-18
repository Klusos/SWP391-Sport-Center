package group5.example.sportCenter.repository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import group5.example.sportCenter.entity.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Integer> {
    List<Membership> findByMemberId(Integer memberId);
    List<Membership> findByPlanId(Integer planId);
    List<Membership> findByStatus(String status);

    // Gói đang còn hiệu lực của một member
    Optional<Membership> findFirstByMemberIdAndStatusAndEndDateGreaterThan(
            Integer memberId, String status, LocalDate today);

    // Tất cả gói ACTIVE chưa hết hạn (kể cả gói gia hạn bắt đầu trong tương lai)
    List<Membership> findByMemberIdAndStatusAndEndDateGreaterThan(
            Integer memberId, String status, LocalDate today);

    List<Membership> findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
            Integer memberId, String status, LocalDate registrationDate, LocalDate registrationDateExclusiveEnd);

    @Query("select m from Membership m join fetch m.plan where m.member.id = :memberId "
            + "and m.status = 'ACTIVE' and m.startDate <= :sessionDate and m.endDate > :sessionDate")
    List<Membership> findActiveAndValidForPt(@Param("memberId") Integer memberId,
            @Param("sessionDate") LocalDate sessionDate);

    // Các gói sắp hết hạn (phục vụ nhắc gia hạn / report)
    List<Membership> findByStatusAndEndDateBetween(String status, LocalDate from, LocalDate to);
    
    // Các gói đã qua ngày hết hạn    
    List<Membership> findByStatusAndEndDateLessThanEqual(String status, LocalDate date);
}
