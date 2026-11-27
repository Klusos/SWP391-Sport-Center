package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.repository.ClassSessionRepository;

@ExtendWith(MockitoExtension.class)
class ClassSessionCompletionServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 12, 0);
    @Mock private ClassSessionRepository sessions;
    @Mock private AuditLogService auditLogs;
    private ClassSessionCompletionService service;
    private ClassSession elapsed;
    private ClassSession future;
    private ClassSession cancelled;

    @BeforeEach
    void setUp() {
        service = new ClassSessionCompletionService(sessions, auditLogs,
                Clock.fixed(Instant.parse("2026-11-27T05:00:00Z"), ZoneId.of("UTC")));
        elapsed = session(1, "SCHEDULED", NOW.minusMinutes(1));
        future = session(2, "SCHEDULED", NOW.plusMinutes(1));
        cancelled = session(3, "CANCELLED", NOW.minusHours(1));
    }

    @Test
    void completesAtOrAfterPersistedEndAndAuditsOnceAcrossRepeatedRuns() {
        when(sessions.findElapsedScheduledIds(NOW)).thenReturn(List.of(1, 2, 3));
        when(sessions.findByIdForAttendanceUpdate(1)).thenReturn(Optional.of(elapsed));
        when(sessions.findByIdForAttendanceUpdate(2)).thenReturn(Optional.of(future));
        when(sessions.findByIdForAttendanceUpdate(3)).thenReturn(Optional.of(cancelled));
        when(sessions.saveAndFlush(any(ClassSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, service.completeElapsedScheduledSessions());
        assertEquals("COMPLETED", elapsed.getStatus());
        assertEquals(0, service.completeElapsedScheduledSessions());
        verify(auditLogs).record(eq("STATUS_CHANGE"), eq("CLASS_SESSION"), eq(1), any());
        verify(auditLogs, never()).record(eq("STATUS_CHANGE"), eq("CLASS_SESSION"), eq(2), any());
        verify(auditLogs, never()).record(eq("STATUS_CHANGE"), eq("CLASS_SESSION"), eq(3), any());
    }

    @Test
    void treatsExactEndAsElapsedAndSkipsCandidateThatChangedToCancelledBeforeLock() {
        ClassSession exact = session(4, "SCHEDULED", NOW);
        ClassSession changed = session(5, "CANCELLED", NOW.minusMinutes(10));
        when(sessions.findElapsedScheduledIds(NOW)).thenReturn(List.of(4, 5));
        when(sessions.findByIdForAttendanceUpdate(4)).thenReturn(Optional.of(exact));
        when(sessions.findByIdForAttendanceUpdate(5)).thenReturn(Optional.of(changed));
        when(sessions.saveAndFlush(exact)).thenReturn(exact);

        assertEquals(1, service.completeElapsedScheduledSessions());
        assertEquals("COMPLETED", exact.getStatus());
        verify(auditLogs).record(eq("STATUS_CHANGE"), eq("CLASS_SESSION"), eq(4), any());
        verify(auditLogs, never()).record(eq("STATUS_CHANGE"), eq("CLASS_SESSION"), eq(5), any());
    }

    private ClassSession session(int id, String status, LocalDateTime end) {
        ClassSession session = new ClassSession();
        session.setId(id);
        session.setStatus(status);
        session.setEndTime(end);
        return session;
    }
}
