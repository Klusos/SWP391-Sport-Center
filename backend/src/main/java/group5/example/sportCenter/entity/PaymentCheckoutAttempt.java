package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable VNPay attempt record. It stores reconciliation evidence, never credentials or card data. */
@Entity
@Table(name = "payment_checkout_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "ux_payment_checkout_attempt_reference", columnNames = "transaction_reference")
}, indexes = {
        @Index(name = "ix_payment_checkout_attempt_payment_created", columnList = "payment_id, created_at, id")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentCheckoutAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_checkout_attempt_payment"))
    private Payment payment;

    /**
     * Membership created by this attempt when it is the winning renewal capture.
     * SQL Server filtered unique indexes enforce one applied attempt per Membership
     * and one winning attempt per parent Payment while allowing all non-winning
     * attempts to remain NULL.
     */
    // The SQL Server filtered unique indexes below enforce logical one-to-one only
    // for non-NULL winners. ManyToOne avoids Hibernate generating an unconditional
    // unique constraint that would reject multiple NULL (non-winning) attempts.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_membership_id",
            foreignKey = @ForeignKey(name = "fk_payment_checkout_attempt_applied_membership"))
    private Membership appliedMembership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckoutProvider provider;

    /** VNPAY transaction reference; renewal references use the disjoint RNL namespace. */
    @Column(name = "transaction_reference", nullable = false, length = 40)
    private String transactionReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutAttemptStatus status;

    /** Provider's transaction identifier, when supplied; no raw callback payload is retained. */
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    /** Amount reported in the verified callback, retained even when it mismatches the snapshot. */
    @Column(name = "provider_amount", precision = 12, scale = 2)
    private BigDecimal providerAmount;

    @Column(name = "provider_response_code", length = 20)
    private String providerResponseCode;

    /** First server timestamp at which a valid signed success callback is processed. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Nationalized
    @Column(name = "reconciliation_reason", length = 500)
    private String reconciliationReason;

}
