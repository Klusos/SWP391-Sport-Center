package group5.example.sportCenter.dto;

import java.math.BigDecimal;

public record MembershipPlanUpgradeResponse(
        Integer id,
        Integer fromPlanId,
        String fromPlanName,
        BigDecimal fromMonthlyPrice,
        Integer toPlanId,
        String toPlanName,
        BigDecimal toMonthlyPrice) {}
