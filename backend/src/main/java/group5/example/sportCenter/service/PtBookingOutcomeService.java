package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.PtBookingOutcomeResponse;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PtBookingOutcomeService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PersonalTrainingBookingRepository bookings;
    private final UserRepository users;
    private final CoachRepository coaches;
    private final AuditLogService auditLogs;
    private final Clock clock;

    public PtBookingOutcomeService(PersonalTrainingBookingRepository bookings, UserRepository users,
            CoachRepository coaches, AuditLogService auditLogs, Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.coaches = coaches;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public PtBookingOutcomeResponse recordCoachOutcome(Jwt jwt, Integer bookingId, String requested) {
        User actor = activeActor(jwt);
        if (!"COACH".equalsIgnoreCase(actor.getRole())) forbidden("Only active COACH accounts can record PT outcomes");
        PersonalTrainingBooking booking = lockBooking(bookingId);
        var coach = coaches.findByUserId(actor.getId())
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) && c.getId().equals(booking.getCoach().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the active coach assigned to this booking can record its outcome"));
        // Keep an explicit reference so assignment is checked after the booking lock.
        if (coach.getId() == null) forbidden("Assigned coach is unavailable");
        PersonalTrainingBookingStatus target = parseOutcome(requested);
        if (booking.getStatus() == target) return response(booking);
        if (booking.getStatus() != PersonalTrainingBookingStatus.CONFIRMED) conflict("Only CONFIRMED bookings can receive a first outcome");
        if (LocalDateTime.now(clock.withZone(BUSINESS_ZONE)).isBefore(booking.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PT outcome can be recorded only after the session ends");
        }
        change(booking, target, "RECORD", "");
        return response(booking);
    }

    @Transactional(readOnly = true)
    public PtBookingOutcomeResponse getForAdmin(Jwt jwt, Integer bookingId) {
        User actor = activeActor(jwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) forbidden("Only active ADMIN accounts can view PT outcomes");
        PersonalTrainingBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT booking not found"));
        return response(booking);
    }

    @Transactional
    public PtBookingOutcomeResponse correctAsAdmin(Jwt jwt, Integer bookingId, String requested, String rawReason) {
        User actor = activeActor(jwt);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole())) forbidden("Only active ADMIN accounts can correct PT outcomes");
        String reason = checkedReason(rawReason);
        PersonalTrainingBooking booking = lockBooking(bookingId);
        PersonalTrainingBookingStatus target = parseOutcome(requested);
        if (booking.getStatus() == target) return response(booking);
        if (!isOutcome(booking.getStatus())) conflict("Admin corrections require an existing COMPLETED or NO_SHOW outcome");
        String suffix = ";reason=\"" + escape(reason) + "\"";
        if (("previous=" + booking.getStatus().name() + ";new=" + target.name() + suffix).length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Correction reason is too long after audit-safe escaping");
        }
        change(booking, target, "CORRECT", suffix);
        return response(booking);
    }

    /** Called through a separate Spring proxy for one-row atomic fallback transactions. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoComplete(Integer bookingId, LocalDateTime cutover, LocalDateTime now) {
        PersonalTrainingBooking booking = bookings.findByIdForLifecycleUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != PersonalTrainingBookingStatus.CONFIRMED
                || booking.getEndTime().isBefore(cutover)
                || booking.getEndTime().isAfter(now.minusHours(24))) return false;
        change(booking, PersonalTrainingBookingStatus.COMPLETED, "AUTO_COMPLETE", ";rule=endTime+24h");
        return true;
    }

    private User activeActor(Jwt jwt) {
        final Integer id;
        try { id = Integer.valueOf(jwt.getSubject()); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user"); }
        User actor = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) forbidden("Inactive accounts cannot record PT outcomes");
        return actor;
    }

    private PersonalTrainingBooking lockBooking(Integer id) {
        return bookings.findByIdForLifecycleUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT booking not found"));
    }

    private void change(PersonalTrainingBooking booking, PersonalTrainingBookingStatus target,
            String action, String suffix) {
        PersonalTrainingBookingStatus previous = booking.getStatus();
        booking.setStatus(target);
        bookings.save(booking);
        String description = "previous=" + previous.name() + ";new=" + target.name() + suffix;
        auditLogs.record(action, "PT_BOOKING_OUTCOME", booking.getId(), description);
    }

    private static String checkedReason(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A correction reason is required");
        String reason = raw.trim();
        if (reason.length() > 300 || reason.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction reason must be at most 300 characters without control characters");
        }
        return reason;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace(";", "\\;");
    }

    private static PersonalTrainingBookingStatus parseOutcome(String value) {
        try {
            PersonalTrainingBookingStatus status = PersonalTrainingBookingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (isOutcome(status)) return status;
        } catch (RuntimeException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Outcome must be COMPLETED or NO_SHOW");
    }

    private static boolean isOutcome(PersonalTrainingBookingStatus status) {
        return status == PersonalTrainingBookingStatus.COMPLETED || status == PersonalTrainingBookingStatus.NO_SHOW;
    }

    private static PtBookingOutcomeResponse response(PersonalTrainingBooking booking) {
        return new PtBookingOutcomeResponse(booking.getId(), booking.getStatus().name(), booking.getEndTime());
    }

    private static void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
