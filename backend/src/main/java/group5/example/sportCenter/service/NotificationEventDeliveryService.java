package group5.example.sportCenter.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Persists event notifications in a new transaction after the originating business commit. */
@Service
public class NotificationEventDeliveryService {

    private final NotificationService notifications;
    private final UserRepository users;
    private final PersonalTrainingBookingRepository bookings;
    private final CourseClassRepository classes;

    public NotificationEventDeliveryService(
            NotificationService notifications,
            UserRepository users,
            PersonalTrainingBookingRepository bookings,
            CourseClassRepository classes) {
        this.notifications = notifications;
        this.users = users;
        this.bookings = bookings;
        this.classes = classes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consultationCreated(Integer consultationId) {
        for (Integer userId : users.findActiveUserIdsByRole("RECEPTIONIST")) {
            notifications.createForUser(userId, "New consultation request",
                    "A new consultation request #" + consultationId + " is waiting for review.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consultationCompleted(Integer consultationId, Integer memberId) {
        notifyActiveRole(memberId, "MEMBER", "Consultation completed",
                "Your consultation request #" + consultationId + " has been completed.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consultationCancelled(Integer consultationId, Integer memberId) {
        notifyActiveRole(memberId, "MEMBER", "Consultation cancelled",
                "Your consultation request #" + consultationId + " has been cancelled.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ptBookingConfirmed(Integer bookingId) {
        PersonalTrainingBooking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != PersonalTrainingBookingStatus.CONFIRMED) {
            return;
        }

        notifyActiveAccount(booking.getMember(), "MEMBER", "PT booking confirmed",
                "Your personal training booking #" + bookingId + " has been confirmed.");

        Coach coach = booking.getCoach();
        if (coach != null && "ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            notifyActiveAccount(coach.getUser(), "COACH", "New PT session assigned",
                    "Personal training booking #" + bookingId + " has been confirmed and assigned to you.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void courseCoachAssigned(Integer courseClassId, Integer coachId) {
        CourseClass courseClass = classes.findById(courseClassId).orElse(null);
        if (courseClass == null || courseClass.getCoach() == null
                || !coachId.equals(courseClass.getCoach().getId())) {
            return;
        }
        Coach coach = courseClass.getCoach();
        if ("ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            notifyActiveAccount(coach.getUser(), "COACH", "Class assignment",
                    "You have been assigned to class #" + courseClassId + ".");
        }
    }

    private void notifyActiveRole(Integer userId, String role, String title, String message) {
        if (userId == null) {
            return;
        }
        users.findById(userId).filter(user -> eligible(user, role))
                .ifPresent(user -> notifications.createForUser(user.getId(), title, message));
    }

    private void notifyActiveAccount(User user, String role, String title, String message) {
        if (user != null && eligible(user, role)) {
            notifications.createForUser(user.getId(), title, message);
        }
    }

    private boolean eligible(User user, String role) {
        return "ACTIVE".equalsIgnoreCase(user.getStatus())
                && role.equals(user.getRole().toUpperCase(Locale.ROOT));
    }
}
