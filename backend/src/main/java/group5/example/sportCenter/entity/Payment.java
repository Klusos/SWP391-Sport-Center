package group5.example.sportCenter.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Payment")
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
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose;

    /** MembershipPlan.id, CourseRegistration.id hoặc PersonalTrainingBooking.id tuỳ theo purpose. */
    @Column(nullable = false)
    private Integer referenceId;

    /** Chỉ dùng cho purpose = MEMBERSHIP. */
    private Boolean autoRenew;

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

    /** Chỉ có khi status = REFUNDED: lúc và người xác nhận đã trả tiền lại cho member. */
    private LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refundedBy")
    private User refundedBy;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
