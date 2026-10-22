package group5.example.sportCenter.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.ForeignKey;
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

    /** Null for legacy/manual memberships whose purchase cannot be verified. */
    @Column(name = "purchased_months")
    private Integer purchasedMonths;

    /** Immutable snapshot of the verified amount paid for this membership. */
    @Column(name = "paid_amount", precision = 12, scale = 2)
    private java.math.BigDecimal paidAmount;

    // The SQL Server migration enforces one non-null source payment per membership
    // with a filtered unique index; ManyToOne avoids Hibernate generating an
    // incompatible unfiltered UNIQUE constraint for the many legacy NULL rows.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_id", foreignKey = @ForeignKey(name = "fk_membership_source_payment"))
    private Payment sourcePayment;
}
