package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.repository.ClassSessionRepository;

/** Completes elapsed scheduled class sessions without creating attendance or training results. */
@Service
public class ClassSessionCompletionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ClassSessionRepository sessions;
    private final AuditLogService auditLogs;
    private final Clock clock;

    public ClassSessionCompletionService(ClassSessionRepository sessions, AuditLogService auditLogs, Clock clock) {
        this.sessions = sessions;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public int completeElapsedScheduledSessions() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        List<Integer> candidates = sessions.findElapsedScheduledIds(now);
        int completed = 0;
        for (Integer id : candidates) {
            ClassSession session = sessions.findByIdForAttendanceUpdate(id).orElse(null);
            if (session == null || !"SCHEDULED".equalsIgnoreCase(session.getStatus())
                    || session.getEndTime() == null || session.getEndTime().isAfter(now)) continue;
            session.setStatus("COMPLETED");
            ClassSession saved = sessions.saveAndFlush(session);
            auditLogs.record("STATUS_CHANGE", "CLASS_SESSION", saved.getId(),
                    "status=SCHEDULED->COMPLETED;source=scheduled-end-time;completedAt=" + now + ".");
            completed++;
        }
        return completed;
    }
}
