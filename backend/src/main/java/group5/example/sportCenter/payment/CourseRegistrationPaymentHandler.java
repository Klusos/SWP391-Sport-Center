package group5.example.sportCenter.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.PaymentRepository;

/**
 * Thanh toán cho một CourseRegistration đã tạo ở Flow 2 ("Confirm Class Registration" trong sơ đồ).
 * Đăng ký và thanh toán là hai thao tác riêng: Flow 2 vẫn tạo đăng ký CONFIRMED, Flow 3 thu đúng
 * finalPrice đã chụp lại lúc đăng ký và ghi nhận đăng ký đó đã được thanh toán.
 */
@Component
public class CourseRegistrationPaymentHandler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CourseRegistrationRepository registrations;
    private final PaymentRepository payments;
    private final Clock clock;

    public CourseRegistrationPaymentHandler(CourseRegistrationRepository registrations, PaymentRepository payments,
            Clock clock) {
        this.registrations = registrations;
        this.payments = payments;
        this.clock = clock;
    }

    /** Kiểm tra đăng ký có thể thanh toán và trả về số tiền phải trả. */
    public BigDecimal quote(User member, Integer registrationId) {
        CourseRegistration registration = registrations.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course registration not found with id: " + registrationId));
        if (!registration.getMember().getId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course registration belongs to another member");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (registration.getStatus() != CourseRegistrationStatus.PENDING_PAYMENT
                || registration.getPaymentDeadline() == null
                || !registration.getPaymentDeadline().isAfter(now)) {
            throw new IllegalArgumentException("Course registration payment hold is no longer active");
        }
        if (payments.existsByPurposeAndReferenceIdAndStatus(
                PaymentPurpose.COURSE_REGISTRATION, registrationId, PaymentStatus.SUCCESS)) {
            throw new IllegalArgumentException("Course registration has already been paid");
        }
        if (registration.getFinalPrice().signum() <= 0) {
            throw new IllegalArgumentException("Course registration has nothing to pay");
        }
        return registration.getFinalPrice();
    }

    /** Trả về lý do cần hoàn tiền nếu không áp dụng được; rỗng nếu thành công. */
    public Optional<String> apply(Payment payment) {
        CourseRegistration registration = registrations.findByIdForPaymentUpdate(payment.getReferenceId()).orElse(null);
        if (registration == null) {
            return Optional.of("Course registration is no longer active");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (registration.getStatus() == CourseRegistrationStatus.PENDING_PAYMENT
                && registration.getPaymentDeadline() != null
                && registration.getPaymentDeadline().isAfter(now)) {
            // A payment is scoped to this registration; this row lock serializes success
            // callbacks, cancellation, and expiration for the same hold.
            if (payments.existsByPurposeAndReferenceIdAndStatusAndIdNot(
                    PaymentPurpose.COURSE_REGISTRATION, registration.getId(), PaymentStatus.SUCCESS, payment.getId())) {
                return Optional.of("Course registration was already paid by another payment");
            }
            registration.setStatus(CourseRegistrationStatus.CONFIRMED);
            return Optional.empty();
        }
        if (registration.getStatus() == CourseRegistrationStatus.PENDING_PAYMENT
                && registration.getPaymentDeadline() != null
                && !registration.getPaymentDeadline().isAfter(now)) {
            registration.setStatus(CourseRegistrationStatus.EXPIRED);
        }
        if (registration.getStatus() != CourseRegistrationStatus.PENDING_PAYMENT) {
            return Optional.of("Course registration hold expired or is no longer payable; review late settlement manually");
        }
        return Optional.of("Course registration payment deadline is unavailable; review payment manually");
    }
}
