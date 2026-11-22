package group5.example.sportCenter.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Membership")
@Getter @Setter @NoArgsConstructor
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planId", nullable = false)
    private MembershipPlan plan;

    @Column(nullable = false)
    private Boolean autoRenew;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 30)
    private String status;

    /** Payment đã mua gói này (MEMBERSHIP hoặc UPGRADE); null với gói cũ hoặc gói do ADMIN gán tay. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchasePaymentId")
    private Payment purchasePayment;

    private LocalDateTime cancelledAt;

    @Nationalized
    @Column(length = 500)
    private String cancelReason;

    @Column(name = "benefit_snapshot_status", length = 16, updatable = false)
    private String benefitSnapshotStatus;

    @Column(name = "benefit_snapshot_source", length = 24, updatable = false)
    private String benefitSnapshotSource;

    @Nationalized
    @Column(name = "plan_name_snapshot", length = 100, updatable = false)
    private String planNameSnapshot;

    @Nationalized
    @Column(name = "plan_description_snapshot", columnDefinition = "nvarchar(max)", updatable = false)
    private String planDescriptionSnapshot;

    @Column(name = "plan_price_snapshot", precision = 12, scale = 2, updatable = false)
    private BigDecimal planPriceSnapshot;

    @Column(name = "plan_duration_months_snapshot", updatable = false)
    private Integer planDurationMonthsSnapshot;

    @Column(name = "course_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal courseDiscountPercentSnapshot;

    @Column(name = "pt_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal ptDiscountPercentSnapshot;

    @Column(name = "allow_pt_plan_snapshot", updatable = false)
    private Boolean allowPtPlanSnapshot;

    @Column(name = "allow_one_on_one_snapshot", updatable = false)
    private Boolean allowOneOnOneSnapshot;

    @Column(name = "paid_value_basis_status", length = 20, updatable = false)
    private String paidValueBasisStatus;

    @Column(name = "paid_value_basis_amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal paidValueBasisAmount;

    @Column(name = "paid_value_basis_source", length = 24, updatable = false)
    private String paidValueBasisSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewal_intent_id", updatable = false)
    private MembershipRenewalIntent renewalIntent;

    public boolean hasCompleteVerifiedBenefitSnapshot() {
        return "COMPLETE".equalsIgnoreCase(benefitSnapshotStatus)
                && benefitSnapshotSource != null
                && planNameSnapshot != null
                && planPriceSnapshot != null
                && planDurationMonthsSnapshot != null
                && courseDiscountPercentSnapshot != null
                && ptDiscountPercentSnapshot != null
                && allowPtPlanSnapshot != null
                && allowOneOnOneSnapshot != null;
    }

    /** Only verified, positive paid-value evidence may be used for a future Upgrade credit. */
    public Optional<BigDecimal> verifiedPaidValueBasis() {
        if (!"VERIFIED".equalsIgnoreCase(paidValueBasisStatus)
                || paidValueBasisAmount == null
                || paidValueBasisAmount.signum() <= 0
                || paidValueBasisSource == null
                || !java.util.Set.of("PURCHASE_PAYMENT", "UPGRADE_CHAIN", "RENEWAL_PAYMENT")
                        .contains(paidValueBasisSource)) {
            return Optional.empty();
        }
        return Optional.of(paidValueBasisAmount);
    }
}
