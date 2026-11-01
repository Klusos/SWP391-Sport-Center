package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.AttendanceHistoryResponse;
import group5.example.sportCenter.dto.AttendanceResponse;
import group5.example.sportCenter.dto.AttendanceRosterMemberResponse;
import group5.example.sportCenter.dto.AttendanceRosterResponse;
import group5.example.sportCenter.dto.AttendanceSessionResponse;
import group5.example.sportCenter.dto.MarkAttendanceRequest;
import group5.example.sportCenter.dto.MemberAttendanceResponse;
import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceHistory;
import group5.example.sportCenter.entity.AttendanceStatus;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.AttendanceHistoryRepository;
import group5.example.sportCenter.repository.AttendanceRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceHistoryRepository historyRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final AssessmentAccess access;
    private final Clock clock;
    private final AuditLogService auditLogs;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            AttendanceHistoryRepository historyRepository,
            ClassSessionRepository classSessionRepository,
            CourseRegistrationRepository registrationRepository,
            AssessmentAccess access,
            Clock clock,
            AuditLogService auditLogs) {
        this.attendanceRepository = attendanceRepository;
        this.historyRepository = historyRepository;
        this.classSessionRepository = classSessionRepository;
        this.registrationRepository = registrationRepository;
        this.access = access;
        this.clock = clock;
        this.auditLogs = auditLogs;
    }

    public List<AttendanceSessionResponse> coachSessions(Jwt jwt) {
        User actor = access.actor(jwt);
        var coach = access.coach(actor);
        return classSessionRepository.findByCourseClass_Coach_IdOrderByStartTimeAscIdAsc(coach.getId())
                .stream().map(this::sessionResponse).toList();
    }

    public List<AttendanceSessionResponse> staffSessions(Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        return classSessionRepository.findAllByOrderByStartTimeAscIdAsc()
                .stream().map(this::sessionResponse).toList();
    }

    public AttendanceRosterResponse roster(Integer sessionId, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = findSession(sessionId);
        authorizeSessionAccess(actor, session);

        Map<Integer, Attendance> markedByMember = new HashMap<>();
        attendanceRepository.findByClassSessionId(sessionId)
                .forEach(record -> markedByMember.put(record.getMember().getId(), record));

        List<AttendanceRosterMemberResponse> members = registrationRepository
                .findByCourseClassIdAndStatus(session.getCourseClass().getId(), CourseRegistrationStatus.CONFIRMED)
                .stream()
                .map(registration -> {
                    User member = registration.getMember();
                    Attendance record = markedByMember.get(member.getId());
                    return new AttendanceRosterMemberResponse(
                            member.getId(), record == null ? null : record.getId(), member.getName(),
                            record == null ? "NOT_MARKED" : record.getStatus().name(),
                            record == null || record.getMarkedBy() == null ? null : record.getMarkedBy().getId(),
                            record == null ? null : record.getMarkedAt(), record == null ? null : record.getNote());
                }).toList();

        return new AttendanceRosterResponse(session.getId(), session.getCourseClass().getId(),
                session.getCourseClass().getName(), session.getStartTime(), session.getEndTime(), session.getStatus(),
                !isCancelled(session) && !LocalDateTime.now(clock).isBefore(session.getStartTime()), members);
    }

    @Transactional
    public AttendanceResponse mark(Integer sessionId, Integer memberId, MarkAttendanceRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = classSessionRepository.findByIdForAttendanceUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + sessionId));
        authorizeMarkAccess(actor, session);
        validateSessionCanBeMarked(session);

        var confirmedRegistration = registrationRepository.findByMemberIdAndCourseClassIdAndStatus(
                memberId, session.getCourseClass().getId(), CourseRegistrationStatus.CONFIRMED);
        if (confirmedRegistration.isEmpty()) throw notRegistered();

        Attendance existing = attendanceRepository.findByClassSessionIdAndMemberId(sessionId, memberId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (existing == null) {
            User member = confirmedRegistration.orElseThrow(this::notRegistered).getMember();

            Attendance created = new Attendance();
            created.setClassSession(session);
            created.setMember(member);
            created.setStatus(request.status());
            created.setMarkedBy(actor);
            created.setMarkedAt(now);
            created.setNote(trimToNull(request.note()));
            created = attendanceRepository.saveAndFlush(created);
            appendHistory(created, null, request.status(), actor, now, null);
            auditLogs.record("MARK", "ATTENDANCE", created.getId(), "Attendance marked as " + created.getStatus().name() + ".");
            return attendanceResponse(created);
        }

        // Repeating the same status is idempotent: return the stored row unchanged and
        // do not alter its latest marker, note, or history.
        if (existing.getStatus() == request.status()) {
            return attendanceResponse(existing);
        }

        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw error(HttpStatus.BAD_REQUEST, "A nonblank reason is required when correcting attendance status");
        }

        AttendanceStatus previous = existing.getStatus();
        existing.setStatus(request.status());
        existing.setMarkedBy(actor);
        existing.setMarkedAt(now);
        if (request.note() != null) {
            existing.setNote(trimToNull(request.note()));
        }
        Attendance saved = attendanceRepository.saveAndFlush(existing);
        appendHistory(saved, previous, request.status(), actor, now, reason);
        auditLogs.record("CORRECT", "ATTENDANCE", saved.getId(), "Attendance corrected from "
                + previous.name() + " to " + saved.getStatus().name() + ".");
        return attendanceResponse(saved);
    }

    public List<AttendanceHistoryResponse> history(Integer attendanceId, Jwt jwt) {
        User actor = access.actor(jwt);
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found with id: " + attendanceId));
        authorizeAttendanceHistoryAccess(actor, attendance);
        return historyRepository.findByAttendanceIdOrderByChangedAtAscIdAsc(attendanceId)
                .stream().map(this::historyResponse).toList();
    }

    public List<MemberAttendanceResponse> mine(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        return attendanceRepository.findByMemberIdOrderByMarkedAtDescIdDesc(actor.getId())
                .stream().map(this::memberAttendanceResponse).toList();
    }

    private void validateSessionCanBeMarked(ClassSession session) {
        if (isCancelled(session)) {
            throw error(HttpStatus.CONFLICT, "Attendance cannot be marked for a CANCELLED ClassSession");
        }
        if (LocalDateTime.now(clock).isBefore(session.getStartTime())) {
            throw error(HttpStatus.CONFLICT, "Attendance can be marked only at or after the ClassSession start time");
        }
    }

    private boolean isCancelled(ClassSession session) {
        return "CANCELLED".equalsIgnoreCase(session.getStatus());
    }

    private void authorizeSessionAccess(User actor, ClassSession session) {
        if (access.staff(actor)) return;
        var coach = access.coach(actor);
        if (!coach.getId().equals(session.getCourseClass().getCoach().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned Coach can access attendance for this ClassSession");
        }
    }

    private void authorizeMarkAccess(User actor, ClassSession session) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return;
        var coach = access.coach(actor);
        if (!coach.getId().equals(session.getCourseClass().getCoach().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned Coach or ADMIN can mark attendance");
        }
    }

    private void authorizeAttendanceHistoryAccess(User actor, Attendance attendance) {
        if (access.staff(actor)) return;
        if ("MEMBER".equalsIgnoreCase(actor.getRole())) {
            if (!actor.getId().equals(attendance.getMember().getId())) {
                throw error(HttpStatus.FORBIDDEN, "Attendance belongs to another Member");
            }
            return;
        }
        authorizeSessionAccess(actor, attendance.getClassSession());
    }

    private ClassSession findSession(Integer id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + id));
    }

    private void appendHistory(Attendance attendance, AttendanceStatus oldStatus, AttendanceStatus newStatus,
            User actor, LocalDateTime changedAt, String reason) {
        AttendanceHistory history = new AttendanceHistory();
        history.setAttendance(attendance);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(actor);
        history.setChangedAt(changedAt);
        history.setReason(reason);
        historyRepository.saveAndFlush(history);
    }

    private AttendanceSessionResponse sessionResponse(ClassSession session) {
        var courseClass = session.getCourseClass();
        var offering = courseClass.getCourseOffering();
        var course = offering.getCourse();
        var sport = course.getSport();
        return new AttendanceSessionResponse(session.getId(), courseClass.getId(), courseClass.getName(),
                offering.getId(), offering.getName(), course.getId(), course.getName(), sport.getId(), sport.getName(),
                courseClass.getCoach().getId(), session.getStartTime(), session.getEndTime(), session.getStatus());
    }

    private AttendanceResponse attendanceResponse(Attendance attendance) {
        ClassSession session = attendance.getClassSession();
        return new AttendanceResponse(attendance.getId(), session.getId(), session.getCourseClass().getId(),
                session.getCourseClass().getName(), session.getStartTime(), session.getEndTime(), session.getStatus(),
                attendance.getMember().getId(), attendance.getMember().getName(), attendance.getStatus().name(),
                attendance.getMarkedBy().getId(), attendance.getMarkedAt(), attendance.getNote(), !isCancelled(session));
    }

    private MemberAttendanceResponse memberAttendanceResponse(Attendance attendance) {
        ClassSession session = attendance.getClassSession();
        var courseClass = session.getCourseClass();
        var offering = courseClass.getCourseOffering();
        var course = offering.getCourse();
        var sport = course.getSport();
        return new MemberAttendanceResponse(attendance.getId(), session.getId(), courseClass.getId(),
                courseClass.getName(), session.getStartTime(), session.getEndTime(), session.getStatus(), offering.getId(),
                offering.getName(), course.getId(), course.getName(), attendance.getStatus().name(),
                attendance.getMarkedBy() == null ? null : attendance.getMarkedBy().getId(), attendance.getMarkedAt(),
                attendance.getNote(), !isCancelled(session), sport.getId(), sport.getName());
    }

    private AttendanceHistoryResponse historyResponse(AttendanceHistory history) {
        return new AttendanceHistoryResponse(history.getId(), history.getAttendance().getId(),
                history.getOldStatus() == null ? null : history.getOldStatus().name(), history.getNewStatus().name(),
                history.getChangedBy().getId(), history.getChangedAt(), history.getReason());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private ResponseStatusException notRegistered() {
        return error(HttpStatus.CONFLICT,
                "Member must have a CONFIRMED CourseRegistration for this CourseClass");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
