package group5.example.sportCenter.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * blockedReason khác null khi member chưa thể nâng cấp; khi đó options rỗng.
 * Giá trị: NO_ACTIVE_MEMBERSHIP, MULTIPLE_ACTIVE_MEMBERSHIPS, QUEUED_MEMBERSHIP, PENDING_PAYMENT, TOO_FEW_DAYS_LEFT.
 */
public record MembershipUpgradeOptionsResponse(
        Integer currentMembershipId,
        Integer currentPlanId,
        String currentPlanName,
        LocalDate currentStartDate,
        LocalDate currentEndDate,
        Integer remainingDays,
        String blockedReason,
        List<MembershipUpgradeOptionResponse> options) {
}
