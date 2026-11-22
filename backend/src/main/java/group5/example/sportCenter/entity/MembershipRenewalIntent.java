package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable Renewal quote and lifecycle anchor. This mapping does not enable Renewal workflows. */
@Entity
@Table(name = "membership_renewal_intent", uniqueConstraints = {
        @UniqueConstraint(name = "uq_membership_renewal_intent_source", columnNames = "source_membership_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MembershipRenewalIntent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_membership_id", nullable = false, updatable = false)
    private Membership sourceMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_plan_id", nullable = false, updatable = false)
    private MembershipPlan targetPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_source", nullable = false, length = 24, updatable = false)
    private RenewalIntentCreationSource creationSource;

    @Column(name = "auto_renew_opt_in_snapshot", nullable = false, updatable = false)
    private Boolean autoRenewOptInSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent_status", nullable = false, length = 20)
    private RenewalIntentStatus intentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDateTime validUntil;

    @Column(name = "quote_captured_at", nullable = false, updatable = false)
    private LocalDateTime quoteCapturedAt;

    @Column(name = "source_end_date_snapshot", nullable = false, updatable = false)
    private LocalDate sourceEndDateSnapshot;

    @Column(name = "successor_start_date", nullable = false, updatable = false)
    private LocalDate successorStartDate;

    @Column(name = "successor_end_date", nullable = false, updatable = false)
    private LocalDate successorEndDate;

    @Nationalized
    @Column(name = "plan_name_snapshot", nullable = false, length = 100, updatable = false)
    private String planNameSnapshot;

    @Nationalized
    @Column(name = "plan_description_snapshot", columnDefinition = "nvarchar(max)", updatable = false)
    private String planDescriptionSnapshot;

    @Column(name = "plan_price_snapshot", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal planPriceSnapshot;

    @Column(name = "quoted_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal quotedAmount;

    @Column(name = "duration_months_snapshot", nullable = false, updatable = false)
    private Integer durationMonthsSnapshot;

    @Column(name = "course_discount_percent_snapshot", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal courseDiscountPercentSnapshot;

    @Column(name = "pt_discount_percent_snapshot", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal ptDiscountPercentSnapshot;

    @Column(name = "allow_pt_plan_snapshot", nullable = false, updatable = false)
    private Boolean allowPtPlanSnapshot;

    @Column(name = "allow_one_on_one_snapshot", nullable = false, updatable = false)
    private Boolean allowOneOnOneSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_payment_id")
    private Payment settledPayment;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_reason", length = 32)
    private RenewalIntentCloseReason closedReason;
}
