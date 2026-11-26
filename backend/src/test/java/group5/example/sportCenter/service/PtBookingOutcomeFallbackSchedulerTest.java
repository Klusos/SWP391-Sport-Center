package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;

import org.junit.jupiter.api.Test;

import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;

class PtBookingOutcomeFallbackSchedulerTest {
    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void missingOrInvalidCutoverLeavesFallbackInactive() {
        PersonalTrainingBookingRepository bookings = mock(PersonalTrainingBookingRepository.class);
        PtBookingOutcomeService outcomes = mock(PtBookingOutcomeService.class);
        var missing = new PtBookingOutcomeFallbackScheduler(bookings, outcomes, Clock.systemUTC(), "", true);
        var invalid = new PtBookingOutcomeFallbackScheduler(bookings, outcomes, Clock.systemUTC(), "not-a-date", true);

        assertEquals(0, missing.completeDueOutcomes());
        assertEquals(0, invalid.completeDueOutcomes());
        verifyNoInteractions(bookings, outcomes);
    }

    @Test
    void globalSchedulingOffPreventsFallbackDatabaseWork() {
        PersonalTrainingBookingRepository bookings = mock(PersonalTrainingBookingRepository.class);
        PtBookingOutcomeService outcomes = mock(PtBookingOutcomeService.class);
        var scheduler = new PtBookingOutcomeFallbackScheduler(bookings, outcomes, Clock.systemUTC(),
                "2026-01-01T00:00:00", false);

        assertEquals(0, scheduler.completeDueOutcomes());
        verifyNoInteractions(bookings, outcomes);
    }

    @Test
    void enabledFallbackUsesVietnamTimeCutoverAndBoundedCandidateBatch() {
        PersonalTrainingBookingRepository bookings = mock(PersonalTrainingBookingRepository.class);
        PtBookingOutcomeService outcomes = mock(PtBookingOutcomeService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("UTC"));
        LocalDateTime cutover = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);
        when(bookings.findOutcomeFallbackCandidateIds(cutover, now.minusHours(24), PageRequest.of(0, 100)))
                .thenReturn(List.of(41));
        when(outcomes.autoComplete(41, cutover, now)).thenReturn(true);
        var scheduler = new PtBookingOutcomeFallbackScheduler(bookings, outcomes, clock,
                cutover.toString(), true);

        assertEquals(1, scheduler.completeDueOutcomes());

        verify(bookings).findOutcomeFallbackCandidateIds(cutover, now.minusHours(24), PageRequest.of(0, 100));
        verify(outcomes).autoComplete(41, cutover, now);
    }
}
