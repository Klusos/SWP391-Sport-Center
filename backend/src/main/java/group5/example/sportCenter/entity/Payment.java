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

    @Nationalized
    @Column(name = "membership_plan_name_snapshot", length = 100, updatable = false)
    private String membershipPlanNameSnapshot;

    @Nationalized
    @Column(name = "membership_plan_description_snapshot", columnDefinition = "nvarchar(max)", updatable = false)
    private String membershipPlanDescriptionSnapshot;

    @Column(name = "membership_plan_price_snapshot", precision = 12, scale = 2, updatable = false)
    private BigDecimal membershipPlanPriceSnapshot;

    @Column(name = "membership_duration_months_snapshot", updatable = false)
    private Integer membershipDurationMonthsSnapshot;

    @Column(name = "membership_course_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal membershipCourseDiscountPercentSnapshot;

    @Column(name = "membership_pt_discount_percent_snapshot", precision = 5, scale = 2, updatable = false)
    private BigDecimal membershipPtDiscountPercentSnapshot;

    @Column(name = "membership_allow_pt_plan_snapshot", updatable = false)
    private Boolean membershipAllowPtPlanSnapshot;

    @Column(name = "membership_allow_one_on_one_snapshot", updatable = false)
    private Boolean membershipAllowOneOnOneSnapshot;

    @Column(name = "membership_quote_captured_at", updatable = false)
    private LocalDateTime membershipQuoteCapturedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewal_intent_id", updatable = false)
    private MembershipRenewalIntent renewalIntent;

    @Column(name = "attempt_valid_until")
    private LocalDateTime attemptValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_attempt_close_reason", length = 32)
    private RenewalAttemptCloseReason renewalAttemptCloseReason;

    @Column(name = "renewal_settlement_accepted_at")
    private LocalDateTime renewalSettlementAcceptedAt;

    @Column(name = "renewal_provider_transaction_reference", length = 64)
    private String renewalProviderTransactionReference;

    @Column(name = "renewal_verified_collection_amount", precision = 12, scale = 2)
    private BigDecimal renewalVerifiedCollectionAmount;

    @Column(name = "renewal_verified_collection_currency", length = 3)
    private String renewalVerifiedCollectionCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_paid_time_evidence_type", length = 24)
    private RenewalPaidTimeEvidenceType renewalPaidTimeEvidenceType;

    @Column(name = "renewal_collection_verified_at")
    private LocalDateTime renewalCollectionVerifiedAt;

    @Column(name = "renewal_collection_conflict_detected_at")
    private LocalDateTime renewalCollectionConflictDetectedAt;

    public boolean hasRecordedRenewalCollection() {
        return renewalCollectionVerifiedAt != null;
    }

    public boolean hasCompleteMembershipQuote() {
        return membershipQuoteCapturedAt != null
                && membershipPlanNameSnapshot != null
                && membershipPlanPriceSnapshot != null
                && membershipPlanPriceSnapshot.signum() >= 0
                && membershipDurationMonthsSnapshot != null
                && membershipDurationMonthsSnapshot > 0
                && membershipCourseDiscountPercentSnapshot != null
                && membershipPtDiscountPercentSnapshot != null
                && membershipAllowPtPlanSnapshot != null
                && membershipAllowOneOnOneSnapshot != null;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
