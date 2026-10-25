package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.TimestampUtcAsJdbcTimestampJdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable quote and period evidence captured when an Upgrade Payment draft is issued. */
@Entity
@Table(name = "membership_upgrade_payment_evidence", uniqueConstraints = @UniqueConstraint(
        name = "ux_membership_upgrade_evidence_payment", columnNames = "payment_id"))
@Getter
@Setter
@NoArgsConstructor
public class MembershipUpgradePaymentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_evidence_payment"))
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_membership_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_evidence_source_membership"))
    private Membership sourceMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_plan_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_evidence_target_plan"))
    private MembershipPlan targetPlan;

    @Column(name = "source_start_date", nullable = false, updatable = false)
    private LocalDate sourceStartDate;

    @Column(name = "source_end_date", nullable = false, updatable = false)
    private LocalDate sourceEndDate;

    @Column(name = "source_purchased_months", updatable = false)
    private Integer sourcePurchasedMonths;

    @Column(name = "original_duration_days", nullable = false, updatable = false)
    private Integer originalDurationDays;

    @Column(name = "remaining_days", nullable = false, updatable = false)
    private Integer remainingDays;

    @Column(name = "source_paid_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal sourcePaidAmount;

    @Column(name = "target_monthly_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal targetMonthlyPrice;

    @Column(name = "remaining_credit", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal remainingCredit;

    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal payableAmount;

    @Column(name = "issuance_business_date", nullable = false, updatable = false)
    private LocalDate issuanceBusinessDate;

    /** UTC instant serialized to DATETIME2(6); timestamps remain absolute without storing local time. */
    @Column(name = "created_at_utc", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant createdAtUtc;

    /** UTC instant serialized to DATETIME2(6); expiry is exclusive (expired at equality). */
    @Column(name = "expires_at_utc", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant expiresAtUtc;

    /** Set exactly once when this draft wins and creates its replacement Membership. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_membership_id", foreignKey = @ForeignKey(name = "fk_upgrade_evidence_applied_membership"))
    private Membership appliedMembership;

    @Column(name = "applied_at_utc", columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant appliedAtUtc;
}
