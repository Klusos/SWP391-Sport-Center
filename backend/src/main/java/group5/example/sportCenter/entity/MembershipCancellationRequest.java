package group5.example.sportCenter.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu huỷ gói chưa bắt đầu do member gửi; nhân viên duyệt (huỷ gói + chờ hoàn tiền) hoặc từ chối.
 * Mỗi gói có tối đa một yêu cầu PENDING (filtered unique index trong phase11-3).
 */
@Entity
@Table(name = "membership_cancellation_request")
@Getter
@Setter
@NoArgsConstructor
public class MembershipCancellationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membershipId", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;

    @Nationalized
    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipCancellationRequestStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** Nhân viên duyệt/từ chối, hoặc chính member khi rút yêu cầu; null khi hệ thống tự đóng (EXPIRED). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidedBy")
    private User decidedBy;

    private LocalDateTime decidedAt;

    @Nationalized
    @Column(length = 500)
    private String decisionNote;
}
