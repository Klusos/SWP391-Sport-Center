package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

/** Persisted UGP-scoped VNPay attempt. Kept separate from Renewal's RNL attempt model. */
@Entity
@Table(name = "membership_upgrade_checkout_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "ux_upgrade_checkout_reference", columnNames = "transaction_reference")
})
@Getter @Setter @NoArgsConstructor
public class MembershipUpgradeCheckoutAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_checkout_payment"))
    private Payment payment;

    @Column(name = "transaction_reference", nullable = false, length = 40, updatable = false)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CheckoutProvider provider;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "expires_at_utc", nullable = false, updatable = false)
    private Instant expiresAtUtc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutAttemptStatus status;

    /** Signed URL is only returned by the owner-authorized issuance API. */
    @Column(name = "checkout_url", nullable = false, length = 2048, updatable = false)
    private String checkoutUrl;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
}
