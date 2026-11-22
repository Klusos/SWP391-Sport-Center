package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MembershipResponse {

    private Integer id;

    private Integer memberId;
    private String memberName;

    private Integer planId;
    private String planName;
    private String planDescription;
    private BigDecimal planPrice;
    private Integer durationMonths;
    private BigDecimal courseDiscountPercent;
    private BigDecimal personalTrainingDiscountPercent;
    private Boolean allowPersonalTrainingPlan;
    private Boolean allowOneOnOneCoaching;
    /** VERIFIED_SNAPSHOT or LEGACY_PLAN_FALLBACK. Does not describe paid-value provenance. */
    private String benefitSource;

    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoRenew;
    private String status;
    private String cancelReason;
}
