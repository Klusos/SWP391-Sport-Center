package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.service.PtBookingExpirationService;

/**
 * Thanh toán cho PersonalTrainingBooking đang PENDING_PAYMENT.
 * Chỉ confirm booking khi thanh toán đã được xác minh và còn trong thời gian giữ chỗ;
 * tiền về muộn được ghi nhận là REFUND_REQUIRED, booking giữ nguyên EXPIRED.
 */
@Component
public class PtBookingPaymentHandler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PersonalTrainingBookingRepository bookings;
    private final PaymentRepository payments;
    private final PtBookingExpirationService lifecycle;
    private final Clock clock;

    public PtBookingPaymentHandler(PersonalTrainingBookingRepository bookings, PaymentRepository payments,
                                   PtBookingExpirationService lifecycle, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** Kiểm tra booking có thể thanh toán và trả về số tiền phải trả. */
    public BigDecimal quote(User member, Integer bookingId) {
        PersonalTrainingBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("PT booking not found with id: " + bookingId));
        if (!booking.getMember().getId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This PT booking belongs to another member");
        }
        if (booking.getStatus() != PersonalTrainingBookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("PT booking is not awaiting payment: " + booking.getStatus());
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (booking.getPaymentExpiresAt() == null || !booking.getPaymentExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("PT booking payment hold has expired");
        }
        if (payments.existsByPurposeAndReferenceIdAndStatus(
                PaymentPurpose.PT_BOOKING, bookingId, PaymentStatus.SUCCESS)) {
            throw new IllegalArgumentException("PT booking has already been paid");
        }
        return booking.getFinalPrice();
    }

    /** Trả về lý do cần hoàn tiền nếu không áp dụng được; rỗng nếu booking đã được confirm. */
    public Optional<String> apply(Payment payment) {
        // Khoá booking trước khi kiểm tra trùng thanh toán; confirmAfterVerifiedPayment khoá lại cùng dòng.
        if (bookings.findByIdForLifecycleUpdate(payment.getReferenceId()).isEmpty()) {
            return Optional.of("PT booking no longer exists");
        }
        if (payments.existsByPurposeAndReferenceIdAndStatusAndIdNot(
                PaymentPurpose.PT_BOOKING, payment.getReferenceId(), PaymentStatus.SUCCESS, payment.getId())) {
            return Optional.of("PT booking was already paid by another payment");
        }
        if (!lifecycle.confirmAfterVerifiedPayment(payment.getReferenceId())) {
            return Optional.of("PT booking payment hold expired before payment completed");
        }
        return Optional.empty();
    }
}
