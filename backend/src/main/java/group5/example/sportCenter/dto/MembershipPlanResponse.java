package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MembershipPlanResponse {

    private Integer id;
    private String name;
    private String description;
    private BigDecimal monthlyPrice;
    private java.util.Set<Integer> upgradeFromPlanIds;

    private BigDecimal courseDiscountPercent;
    private BigDecimal personalTrainingDiscountPercent;

    private Boolean allowPersonalTrainingPlan;
    private Boolean allowOneOnOneCoaching;

    private String status;
}
