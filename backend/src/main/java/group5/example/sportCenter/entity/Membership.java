package group5.example.sportCenter.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}