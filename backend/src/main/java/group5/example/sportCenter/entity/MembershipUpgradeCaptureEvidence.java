package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.TimestampUtcAsJdbcTimestampJdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only verified provider evidence. Do not store signatures or raw callback payloads. */
@Entity
@Table(name = "membership_upgrade_capture_evidence", uniqueConstraints = @UniqueConstraint(
        name = "ux_upgrade_capture_evidence_key", columnNames = "evidence_key"))
@Getter @Setter @NoArgsConstructor
public class MembershipUpgradeCaptureEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upgrade_evidence_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_capture_parent"))
    private MembershipUpgradePaymentEvidence upgradeEvidence;

    /** The exact persisted UGP attempt correlated to this callback; nullable only for pre-9.5 history. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_attempt_id", foreignKey = @ForeignKey(name = "fk_upgrade_capture_attempt"))
    private MembershipUpgradeCheckoutAttempt checkoutAttempt;

    @Column(name = "evidence_key", nullable = false, length = 64, updatable = false)
    private String evidenceKey;

    @Column(name = "provider_transaction_id", nullable = false, length = 100, updatable = false)
    private String providerTransactionId;

    @Column(name = "transaction_code", nullable = false, length = 40, updatable = false)
    private String transactionCode;

    @Column(name = "provider_response_code", nullable = false, length = 10, updatable = false)
    private String providerResponseCode;

    @Column(name = "reported_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal reportedAmount;

    /** Null means provider capture time was not present/parseable; settlement must reconcile. */
    @Column(name = "captured_at_utc", updatable = false, columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant capturedAtUtc;

    @Column(name = "received_at_utc", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant receivedAtUtc;

    @Column(name = "outcome", nullable = false, length = 30)
    private String outcome;

    @Column(name = "reconciliation_reason", length = 500)
    private String reconciliationReason;
}
