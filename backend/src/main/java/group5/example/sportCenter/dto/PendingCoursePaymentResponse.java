package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.PaymentStatus;

/** Staff queue projection; excludes gateway data and unrelated member details. */
public record PendingCoursePaymentResponse(
        Integer paymentId,
        Integer memberId,
        String memberName,
        Integer registrationId,
        Integer courseId,
        String courseName,
        Integer classId,
        String className,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        CourseRegistrationStatus registrationStatus,
        LocalDateTime paymentDeadline,
        LocalDateTime paymentCreatedAt) {
}
