package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** purchasePaymentId/purchaseAmount: khoản đã mua gói, sẽ được hoàn 100% nếu yêu cầu được duyệt. */
public record MembershipCancellationRequestResponse(
        Integer id,
        Integer membershipId,
        Integer memberId,
        String memberName,
        String planName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        LocalDateTime requestedAt,
        String decidedByName,
        LocalDateTime decidedAt,
        String decisionNote,
        Integer purchasePaymentId,
        BigDecimal purchaseAmount) {
}
