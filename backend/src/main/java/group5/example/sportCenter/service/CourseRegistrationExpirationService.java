package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.repository.CourseRegistrationRepository;

/** Bounded, idempotent cleanup; correctness still comes from deadline-aware reads and row locks. */
@Service
public class CourseRegistrationExpirationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int BATCH_SIZE = 200;

    private final CourseRegistrationRepository registrations;
    private final Clock clock;

    public CourseRegistrationExpirationService(CourseRegistrationRepository registrations, Clock clock) {
        this.registrations = registrations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.course-registration.expiration-sweep-interval-ms:60000}")
    @Transactional
    public int expireDueRegistrations() {
        LocalDateTime now = now();
        int expired = 0;
        for (Integer id : registrations.findDueIds(CourseRegistrationStatus.PENDING_PAYMENT,
                now, PageRequest.of(0, BATCH_SIZE))) {
            CourseRegistration registration = registrations.findByIdForUpdate(id).orElse(null);
            if (expireIfDue(registration, now)) expired++;
        }
        return expired;
    }

    @Transactional
    public boolean expireIfDue(CourseRegistration registration, LocalDateTime now) {
        if (registration == null || registration.getStatus() != CourseRegistrationStatus.PENDING_PAYMENT
                || registration.getPaymentDeadline() == null
                || registration.getPaymentDeadline().isAfter(now)) return false;
        registration.setStatus(CourseRegistrationStatus.EXPIRED);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireForClass(Integer classId, LocalDateTime now) {
        int expired = 0;
        for (Integer id : registrations.findDueIdsByClass(classId, CourseRegistrationStatus.PENDING_PAYMENT, now)) {
            CourseRegistration registration = registrations.findByIdForUpdate(id).orElse(null);
            if (expireIfDue(registration, now)) expired++;
        }
        return expired;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireForMember(Integer memberId, LocalDateTime now) {
        int expired = 0;
        for (Integer id : registrations.findDueIdsByMember(memberId, CourseRegistrationStatus.PENDING_PAYMENT, now)) {
            CourseRegistration registration = registrations.findByIdForUpdate(id).orElse(null);
            if (expireIfDue(registration, now)) expired++;
        }
        return expired;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }
}
