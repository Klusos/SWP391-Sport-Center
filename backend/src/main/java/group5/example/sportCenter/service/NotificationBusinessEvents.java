package group5.example.sportCenter.service;

/** Internal, ID-only events published from successful transactional state changes. */
public final class NotificationBusinessEvents {
    private NotificationBusinessEvents() {
    }

    public interface BusinessEvent {
        Integer referenceId();
    }

    public record ConsultationCreated(Integer referenceId) implements BusinessEvent {
    }

    public record ConsultationCompleted(Integer referenceId, Integer memberId) implements BusinessEvent {
    }

    public record ConsultationCancelled(Integer referenceId, Integer memberId) implements BusinessEvent {
    }

    public record PtBookingConfirmed(Integer referenceId) implements BusinessEvent {
    }

    public record CourseCoachAssigned(Integer referenceId, Integer coachId) implements BusinessEvent {
    }
}
