package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Báo giá nâng cấp gắn với một Payment UPGRADE. Chốt gói nguồn, gói đích, số ngày còn lại và hạn báo giá
 * lúc tạo thanh toán; resultMembership chỉ có sau khi thanh toán thành công và gói mới được tạo.
 */
@Entity
@Table(name = "membership_upgrade")
@Getter
@Setter
@NoArgsConstructor
public class MembershipUpgrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paymentId", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sourceMembershipId", nullable = false)
    private Membership sourceMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "targetPlanId", nullable = false)
    private MembershipPlan targetPlan;

    @Column(nullable = false)
    private Integer remainingDays;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resultMembershipId")
    private Membership resultMembership;

    @Column(name = "source_period_start_date", updatable = false)
    private LocalDate sourcePeriodStartDate;

    @Column(name = "source_period_end_date", updatable = false)
    private LocalDate sourcePeriodEndDate;

    @Column(name = "credit_valuation_date", updatable = false)
    private LocalDate creditValuationDate;

    @Column(name = "source_total_days", updatable = false)
    private Integer sourceTotalDays;

    @Column(name = "source_unused_days", updatable = false)
    private Integer sourceUnusedDays;

    @Column(name = "source_paid_value_basis_amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal sourcePaidValueBasisAmount;

    @Nationalized
    @Column(name = "target_plan_name_snapshot", length = 100, updatable = false)
    private String targetPlanNameSnapshot;

    @Nationalized
    @Column(name = "target_plan_description_snapshot", columnDefinition = "nvarchar(max)", updatable = false)
    private String targetPlanDescriptionSnapshot;

    @Column(name = "target_plan_price_snapshot", precision = 12, scale = 2, updatable = false)
    private BigDecimal targetPlanPriceSnapshot;

    @Column(name = "target_duration_months_snapshot", updatable = false)
    private Integer targetDurationMonthsSnapshot;

    @Column(name = "target_course_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal targetCourseDiscountPercentSnapshot;

    @Column(name = "target_pt_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal targetPtDiscountPercentSnapshot;

    @Column(name = "target_allow_pt_plan_snapshot", updatable = false)
    private Boolean targetAllowPtPlanSnapshot;

    @Column(name = "target_allow_one_on_one_snapshot", updatable = false)
    private Boolean targetAllowOneOnOneSnapshot;

    @Column(name = "applied_credit_amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal appliedCreditAmount;

    public boolean hasCompleteFrozenQuote() {
        return sourcePeriodStartDate != null
                && sourcePeriodEndDate != null
                && sourcePeriodEndDate.isAfter(sourcePeriodStartDate)
                && creditValuationDate != null
                && !creditValuationDate.isBefore(sourcePeriodStartDate)
                && creditValuationDate.isBefore(sourcePeriodEndDate)
                && sourceTotalDays != null && sourceTotalDays > 0
                && sourceUnusedDays != null && sourceUnusedDays >= 3 && sourceUnusedDays <= sourceTotalDays
                && java.time.temporal.ChronoUnit.DAYS.between(sourcePeriodStartDate, sourcePeriodEndDate) == sourceTotalDays
                && java.time.temporal.ChronoUnit.DAYS.between(creditValuationDate, sourcePeriodEndDate) == sourceUnusedDays
                && remainingDays != null && remainingDays.equals(sourceUnusedDays)
                && sourcePaidValueBasisAmount != null && sourcePaidValueBasisAmount.signum() > 0
                && targetPlanNameSnapshot != null
                && targetPlanPriceSnapshot != null && targetPlanPriceSnapshot.signum() > 0
                && targetDurationMonthsSnapshot != null && targetDurationMonthsSnapshot > 0
                && targetCourseDiscountPercentSnapshot != null
                && targetCourseDiscountPercentSnapshot.signum() >= 0
                && targetCourseDiscountPercentSnapshot.compareTo(BigDecimal.valueOf(100)) <= 0
                && targetPtDiscountPercentSnapshot != null
                && targetPtDiscountPercentSnapshot.signum() >= 0
                && targetPtDiscountPercentSnapshot.compareTo(BigDecimal.valueOf(100)) <= 0
                && targetAllowPtPlanSnapshot != null
                && targetAllowOneOnOneSnapshot != null
                && appliedCreditAmount != null && appliedCreditAmount.signum() >= 0
                && appliedCreditAmount.compareTo(sourcePaidValueBasisAmount) <= 0
                && appliedCreditAmount.compareTo(targetPlanPriceSnapshot) <= 0
                && appliedCreditAmount.stripTrailingZeros().scale() <= 0
                && expiresAt != null && createdAt != null && expiresAt.isAfter(createdAt);
    }
}
