package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MembershipUpgradePathResponse(
        Integer id,
        Integer fromPlanId,
        String fromPlanName,
        BigDecimal fromPlanPrice,
        String fromPlanStatus,
        Integer toPlanId,
        String toPlanName,
        BigDecimal toPlanPrice,
        String toPlanStatus,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
