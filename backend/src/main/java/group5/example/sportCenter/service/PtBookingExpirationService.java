package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;

/**
 * Serializes payment confirmation and hold expiration by locking the booking row.
 * Payment confirmation is an internal integration entry point: callers must invoke it
 * only after Flow 3 has independently verified the matching payment.
 */
@Service
public class PtBookingExpirationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PersonalTrainingBookingRepository bookings;
    private final Clock clock;

    public PtBookingExpirationService(PersonalTrainingBookingRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.pt.expiration-sweep-interval-ms:60000}")
    @Transactional
    public int expireDueBookings() {
        LocalDateTime now = now();
        int expired = 0;
        for (Integer id : bookings.findExpiredPendingIds(now)) {
            PersonalTrainingBooking booking = bookings.findByIdForLifecycleUpdate(id).orElse(null);
            if (booking != null && booking.getStatus() == PersonalTrainingBookingStatus.PENDING_PAYMENT
                    && booking.getPaymentExpiresAt() != null && !booking.getPaymentExpiresAt().isAfter(now)) {
                booking.setStatus(PersonalTrainingBookingStatus.EXPIRED);
                expired++;
            }
        }
        return expired;
    }

    /**
     * Call only after trusted payment verification. Returns false when the hold has
     * expired or the booking is terminal; late payment handling belongs to Flow 3.
     */
    @Transactional
    public boolean confirmAfterVerifiedPayment(Integer bookingId) {
        PersonalTrainingBooking booking = bookings.findByIdForLifecycleUpdate(bookingId).orElse(null);
        if (booking == null) return false;

        if (booking.getStatus() == PersonalTrainingBookingStatus.CONFIRMED) return true;
        if (booking.getStatus() != PersonalTrainingBookingStatus.PENDING_PAYMENT) return false;

        LocalDateTime now = now();
        if (booking.getPaymentExpiresAt() == null || !booking.getPaymentExpiresAt().isAfter(now)) {
            booking.setStatus(PersonalTrainingBookingStatus.EXPIRED);
            return false;
        }

        booking.setStatus(PersonalTrainingBookingStatus.CONFIRMED);
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }
}
