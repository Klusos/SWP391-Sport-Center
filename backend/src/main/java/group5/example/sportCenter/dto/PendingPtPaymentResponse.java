package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;

/** Staff queue projection with only the member and appointment fields needed for counter service. */
public record PendingPtPaymentResponse(
        Integer paymentId,
        Integer bookingId,
        Integer memberId,
        String memberName,
        Integer sportId,
        String sportName,
        Integer coachId,
        String coachName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        PersonalTrainingBookingStatus bookingStatus,
        LocalDateTime paymentDeadline,
        LocalDateTime paymentCreatedAt) {
}
