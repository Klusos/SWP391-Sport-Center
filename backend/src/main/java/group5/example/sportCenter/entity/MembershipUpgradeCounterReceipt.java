package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Nationalized;

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

/** Authenticated staff receipt for a counter Upgrade; never treated as VNPay evidence. */
@Entity
@Table(name = "membership_upgrade_counter_receipt", uniqueConstraints =
        @UniqueConstraint(name = "ux_upgrade_counter_receipt_payment", columnNames = "payment_id"))
@Getter @Setter @NoArgsConstructor
public class MembershipUpgradeCounterReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_counter_receipt_payment"))
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_upgrade_counter_receipt_staff"))
    private User staff;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "received_at_utc", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    @JdbcType(TimestampUtcAsJdbcTimestampJdbcType.class)
    private Instant receivedAtUtc;

    @Nationalized
    @Column(name = "staff_note", length = 500, updatable = false)
    private String staffNote;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Nationalized
    @Column(name = "reconciliation_reason", length = 500)
    private String reconciliationReason;
}
