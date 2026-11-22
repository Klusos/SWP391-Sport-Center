package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.MembershipUpgradePath;
import jakarta.persistence.LockModeType;

public interface MembershipUpgradePathRepository extends JpaRepository<MembershipUpgradePath, Integer> {
    @EntityGraph(attributePaths = { "fromPlan", "toPlan" })
    List<MembershipUpgradePath> findAllByOrderByFromPlanIdAscToPlanIdAsc();

    @EntityGraph(attributePaths = { "fromPlan", "toPlan" })
    List<MembershipUpgradePath> findByFromPlanIdAndActiveTrueOrderByToPlanPriceAsc(Integer fromPlanId);

    @EntityGraph(attributePaths = { "fromPlan", "toPlan" })
    Optional<MembershipUpgradePath> findByFromPlanIdAndToPlanIdAndActiveTrue(Integer fromPlanId, Integer toPlanId);

    Optional<MembershipUpgradePath> findByFromPlanIdAndToPlanId(Integer fromPlanId, Integer toPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MembershipUpgradePath p where p.fromPlan.id = :fromPlanId "
            + "and p.toPlan.id = :toPlanId and p.active = true")
    Optional<MembershipUpgradePath> findActiveForQuoteUpdate(
            @Param("fromPlanId") Integer fromPlanId, @Param("toPlanId") Integer toPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "fromPlan", "toPlan" })
    @Query("select p from MembershipUpgradePath p where p.id = :id")
    Optional<MembershipUpgradePath> findByIdForUpdate(@Param("id") Integer id);
}
