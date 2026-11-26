package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.UserRepository;

class PtBookingOutcomeServiceTest {
    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");

    @Test
    void assignedCoachMayRecordAtTheExactSessionEndBoundary() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));

        var response = fixture.service.recordCoachOutcome(fixture.jwt, 21, "COMPLETED");

        assertEquals("COMPLETED", response.status());
        verify(fixture.auditLogs).record("RECORD", "PT_BOOKING_OUTCOME", 21,
                "previous=CONFIRMED;new=COMPLETED");
    }

    @Test
    void assignedCoachCannotRecordBeforeTheSessionEndBoundary() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0).plusNanos(1));

        assertThrows(ResponseStatusException.class,
                () -> fixture.service.recordCoachOutcome(fixture.jwt, 21, "NO_SHOW"));
        assertEquals(PersonalTrainingBookingStatus.CONFIRMED, fixture.booking.getStatus());
        verifyNoInteractions(fixture.auditLogs);
    }

    @Test
    void assignedCoachCanRecordNoShowAndIdenticalRetryDoesNotDuplicateAudit() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));

        assertEquals("NO_SHOW", fixture.service.recordCoachOutcome(fixture.jwt, 21, "NO_SHOW").status());
        assertEquals("NO_SHOW", fixture.service.recordCoachOutcome(fixture.jwt, 21, "NO_SHOW").status());

        assertEquals(PersonalTrainingBookingStatus.NO_SHOW, fixture.booking.getStatus());
        verify(fixture.auditLogs).record("RECORD", "PT_BOOKING_OUTCOME", 21,
                "previous=CONFIRMED;new=NO_SHOW");
        verify(fixture.bookings).save(fixture.booking);
    }

    @Test
    void conflictingCoachRetryIsRejectedWithoutOverwritingOutcome() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        fixture.booking.setStatus(PersonalTrainingBookingStatus.NO_SHOW);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.recordCoachOutcome(fixture.jwt, 21, "COMPLETED"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(PersonalTrainingBookingStatus.NO_SHOW, fixture.booking.getStatus());
        verify(fixture.bookings, never()).save(any());
        verifyNoInteractions(fixture.auditLogs);
    }

    @Test
    void memberCannotRecordCoachOutcome() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        fixture.actor.setRole("MEMBER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.recordCoachOutcome(fixture.jwt, 21, "COMPLETED"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(fixture.bookings, never()).findByIdForLifecycleUpdate(any());
        verifyNoInteractions(fixture.auditLogs);
    }

    @Test
    void activeCoachNotAssignedToBookingCannotRecordOutcome() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        Coach other = new Coach();
        other.setId(99);
        other.setStatus("ACTIVE");
        other.setUser(fixture.actor);
        when(fixture.coaches.findByUserId(10)).thenReturn(Optional.of(other));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.recordCoachOutcome(fixture.jwt, 21, "COMPLETED"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(PersonalTrainingBookingStatus.CONFIRMED, fixture.booking.getStatus());
        verify(fixture.bookings, never()).save(any());
        verifyNoInteractions(fixture.auditLogs);
    }

    @Test
    void adminCorrectionRequiresReasonAndAuditsBeforeAndAfterStatus() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        fixture.actor.setRole("ADMIN");
        fixture.booking.setStatus(PersonalTrainingBookingStatus.NO_SHOW);

        ResponseStatusException missingReason = assertThrows(ResponseStatusException.class,
                () -> fixture.service.correctAsAdmin(fixture.jwt, 21, "COMPLETED", "  "));
        assertEquals(HttpStatus.BAD_REQUEST, missingReason.getStatusCode());
        assertEquals("COMPLETED", fixture.service.correctAsAdmin(
                fixture.jwt, 21, "COMPLETED", "  attendance verified  ").status());

        verify(fixture.auditLogs).record("CORRECT", "PT_BOOKING_OUTCOME", 21,
                "previous=NO_SHOW;new=COMPLETED;reason=\"attendance verified\"");
    }

    @Test
    void adminCannotSilentlyTruncateOversizedCorrectionReason() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        fixture.actor.setRole("ADMIN");
        fixture.booking.setStatus(PersonalTrainingBookingStatus.COMPLETED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service.correctAsAdmin(fixture.jwt, 21, "NO_SHOW", "r".repeat(301)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(PersonalTrainingBookingStatus.COMPLETED, fixture.booking.getStatus());
        verify(fixture.bookings, never()).save(any());
        verifyNoInteractions(fixture.auditLogs);
    }

    @Test
    void fallbackCompletesExactlyAtCutoverAndTwentyFourHourBoundary() {
        Fixture fixture = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        LocalDateTime endTime = fixture.booking.getEndTime();
        LocalDateTime dueNow = endTime.plusHours(24);

        assertEquals(true, fixture.service.autoComplete(21, endTime, dueNow));

        assertEquals(PersonalTrainingBookingStatus.COMPLETED, fixture.booking.getStatus());
        verify(fixture.auditLogs).record("AUTO_COMPLETE", "PT_BOOKING_OUTCOME", 21,
                "previous=CONFIRMED;new=COMPLETED;rule=endTime+24h");
    }

    @Test
    void fallbackExcludesPreCutoverAndNonConfirmedBookings() {
        Fixture preCutover = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        assertEquals(false, preCutover.service.autoComplete(21,
                preCutover.booking.getEndTime().plusNanos(1), preCutover.booking.getEndTime().plusHours(30)));
        verifyNoInteractions(preCutover.auditLogs);

        Fixture cancelled = fixture(Clock.fixed(NOW, HCM), LocalDateTime.of(2026, 11, 22, 10, 0));
        cancelled.booking.setStatus(PersonalTrainingBookingStatus.CANCELLED);
        assertEquals(false, cancelled.service.autoComplete(21,
                cancelled.booking.getEndTime(), cancelled.booking.getEndTime().plusHours(24)));
        assertEquals(PersonalTrainingBookingStatus.CANCELLED, cancelled.booking.getStatus());
        verifyNoInteractions(cancelled.auditLogs);
    }

    private Fixture fixture(Clock clock, LocalDateTime endTime) {
        PersonalTrainingBookingRepository bookings = mock(PersonalTrainingBookingRepository.class);
        UserRepository users = mock(UserRepository.class);
        CoachRepository coaches = mock(CoachRepository.class);
        AuditLogService auditLogs = mock(AuditLogService.class);
        User actor = new User();
        actor.setId(10);
        actor.setStatus("ACTIVE");
        actor.setRole("COACH");
        Coach coach = new Coach();
        coach.setId(4);
        coach.setStatus("ACTIVE");
        coach.setUser(actor);
        PersonalTrainingBooking booking = new PersonalTrainingBooking();
        booking.setId(21);
        booking.setCoach(coach);
        booking.setStatus(PersonalTrainingBookingStatus.CONFIRMED);
        booking.setEndTime(endTime);
        when(users.findById(10)).thenReturn(Optional.of(actor));
        when(coaches.findByUserId(10)).thenReturn(Optional.of(coach));
        when(bookings.findByIdForLifecycleUpdate(21)).thenReturn(Optional.of(booking));
        when(bookings.save(any(PersonalTrainingBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Jwt jwt = Jwt.withTokenValue("dummy-test-token").header("alg", "none")
                .subject("10").issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
        return new Fixture(new PtBookingOutcomeService(bookings, users, coaches, auditLogs, clock),
                auditLogs, booking, jwt, bookings, coaches, actor);
    }

    private record Fixture(PtBookingOutcomeService service, AuditLogService auditLogs,
            PersonalTrainingBooking booking, Jwt jwt, PersonalTrainingBookingRepository bookings,
            CoachRepository coaches, User actor) { }
}
