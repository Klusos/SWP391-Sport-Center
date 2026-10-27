package group5.example.sportCenter.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Báo giá nâng cấp gắn với một Payment UPGRADE. Chốt gói nguồn, gói đích, số ngày còn lại và hạn báo giá
 * lúc tạo thanh toán; resultMembership chỉ có sau khi thanh toán thành công và gói mới được tạo.
 */
@Entity
@Table(name = "membership_upgrade")
@Getter
@Setter
@NoArgsConstructor
public class MembershipUpgrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paymentId", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sourceMembershipId", nullable = false)
    private Membership sourceMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "targetPlanId", nullable = false)
    private MembershipPlan targetPlan;

    @Column(nullable = false)
    private Integer remainingDays;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resultMembershipId")
    private Membership resultMembership;
}
