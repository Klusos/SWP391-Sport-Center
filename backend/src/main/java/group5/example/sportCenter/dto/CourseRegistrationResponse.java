package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseRegistrationResponse(
        Integer id,
        Integer memberId,
        Integer courseId,
        String courseName,
        Integer courseOfferingId,
        String courseOfferingName,
        Integer classId,
        String className,
        Integer sportId,
        String requiredLevel,
        String status,
        LocalDateTime registeredAt,
        LocalDateTime cancelledAt,
        BigDecimal originalPrice,
        BigDecimal membershipDiscount,
        BigDecimal finalPrice) {
}
