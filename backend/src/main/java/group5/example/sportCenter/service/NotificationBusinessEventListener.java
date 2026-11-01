package group5.example.sportCenter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationBusinessEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationBusinessEventListener.class);
    private final NotificationEventDeliveryService delivery;

    public NotificationBusinessEventListener(NotificationEventDeliveryService delivery) {
        this.delivery = delivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void consultationCreated(NotificationBusinessEvents.ConsultationCreated event) {
        safely("consultation-created", event.referenceId(),
                () -> delivery.consultationCreated(event.referenceId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void consultationCompleted(NotificationBusinessEvents.ConsultationCompleted event) {
        safely("consultation-completed", event.referenceId(),
                () -> delivery.consultationCompleted(event.referenceId(), event.memberId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void consultationCancelled(NotificationBusinessEvents.ConsultationCancelled event) {
        safely("consultation-cancelled", event.referenceId(),
                () -> delivery.consultationCancelled(event.referenceId(), event.memberId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void ptBookingConfirmed(NotificationBusinessEvents.PtBookingConfirmed event) {
        safely("pt-booking-confirmed", event.referenceId(),
                () -> delivery.ptBookingConfirmed(event.referenceId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void courseCoachAssigned(NotificationBusinessEvents.CourseCoachAssigned event) {
        safely("course-coach-assigned", event.referenceId(),
                () -> delivery.courseCoachAssigned(event.referenceId(), event.coachId()));
    }

    private void safely(String eventName, Integer referenceId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Notification delivery failed for {} event reference {}", eventName, referenceId, exception);
        }
    }
}
