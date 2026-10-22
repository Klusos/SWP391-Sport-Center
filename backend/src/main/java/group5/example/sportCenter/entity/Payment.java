package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.Check;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Payment")
@Check(name = "ck_payment_method", constraints = "(method IS NOT NULL AND method IN ('ONLINE', 'COUNTER')) "
        + "OR (method IS NULL AND purpose = 'MEMBERSHIP' AND status IN ('PENDING', 'CANCELLED') "
        + "AND renewal_membership_id IS NOT NULL AND purchased_months IS NOT NULL AND purchased_months = 1 "
        + "AND monthly_price_snapshot IS NOT NULL AND monthly_price_snapshot > 0 "
        + "AND amount = monthly_price_snapshot)")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose;

    /** MembershipPlan.id, CourseRegistration.id hoặc PersonalTrainingBooking.id tuỳ theo purpose. */
    @Column(nullable = false)
    private Integer referenceId;

    /** Chỉ dùng cho purpose = MEMBERSHIP. */
    private Boolean autoRenew;

    /** Present for 9.2A membership purchases; null preserves the old pending-payment contract. */
    @Column(name = "purchased_months")
    private Integer purchasedMonths;

    /** Monthly catalog price captured when a new-contract membership payment was created. */
    @Column(name = "monthly_price_snapshot", precision = 12, scale = 2)
    private BigDecimal monthlyPriceSnapshot;

    /** Explicit member choice for the next period created by a renewal; null means not chosen. */
    @Column(name = "renewal_auto_renew_choice")
    private Boolean renewalAutoRenewChoice;

    /** Set only for a future automatically prepared renewal request; ordinary payments remain null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewal_membership_id", foreignKey = @ForeignKey(name = "fk_payment_renewal_membership"))
    private Membership renewalMembership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, unique = true, length = 40)
    private String transactionCode;

    /** Receptionist/Admin xử lý thanh toán tại quầy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processedBy")
    private User processedBy;

    @Nationalized
    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
