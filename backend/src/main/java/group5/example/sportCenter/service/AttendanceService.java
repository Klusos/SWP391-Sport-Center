package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.AttendanceHistoryResponse;
import group5.example.sportCenter.dto.AttendanceResponse;
import group5.example.sportCenter.dto.AttendanceRosterMemberResponse;
import group5.example.sportCenter.dto.AttendanceRosterResponse;
import group5.example.sportCenter.dto.AttendanceSessionResponse;
import group5.example.sportCenter.dto.AttendanceSubmissionRequest;
import group5.example.sportCenter.dto.MarkAttendanceRequest;
import group5.example.sportCenter.dto.MemberAttendanceResponse;
import group5.example.sportCenter.dto.MemberAttendanceOverviewResponse;
import group5.example.sportCenter.dto.MemberAttendanceOverviewSessionResponse;
import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceHistory;
import group5.example.sportCenter.entity.AttendanceStatus;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.AttendanceHistoryRepository;
import group5.example.sportCenter.repository.AttendanceRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.TrainingResultRepository;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceHistoryRepository historyRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final CourseClassRepository courseClasses;
    private final TrainingResultRepository trainingResults;
    private final AssessmentAccess access;
    private final Clock clock;
    private final AuditLogService auditLogs;
    private final ApplicationEventPublisher events;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            AttendanceHistoryRepository historyRepository,
            ClassSessionRepository classSessionRepository,
            CourseRegistrationRepository registrationRepository,
            CourseClassRepository courseClasses,
            TrainingResultRepository trainingResults,
            AssessmentAccess access,
            Clock clock,
            AuditLogService auditLogs,
            ApplicationEventPublisher events) {
        this.attendanceRepository = attendanceRepository;
        this.historyRepository = historyRepository;
        this.classSessionRepository = classSessionRepository;
        this.registrationRepository = registrationRepository;
        this.courseClasses = courseClasses;
        this.trainingResults = trainingResults;
        this.access = access;
        this.clock = clock;
        this.auditLogs = auditLogs;
        this.events = events;
    }

    public List<AttendanceSessionResponse> coachSessions(Jwt jwt) {
        User actor = access.actor(jwt);
        var coach = access.coach(actor);
        return sessionResponses(classSessionRepository.findByCourseClass_Coach_IdOrderByStartTimeAscIdAsc(coach.getId()));
    }

    public List<AttendanceSessionResponse> staffSessions(Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        return sessionResponses(classSessionRepository.findAllByOrderByStartTimeAscIdAsc());
    }

    public AttendanceRosterResponse roster(Integer sessionId, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = findSession(sessionId);
        authorizeSessionAccess(actor, session);

        Map<Integer, Attendance> markedByMember = new HashMap<>();
        attendanceRepository.findByClassSessionId(sessionId)
                .forEach(record -> markedByMember.put(record.getMember().getId(), record));

        List<CourseRegistration> eligible = eligibleRegistrations(session);
        List<AttendanceRosterMemberResponse> members = eligible.stream()
                .map(registration -> {
                    User member = registration.getMember();
                    Attendance record = markedByMember.get(member.getId());
                    return new AttendanceRosterMemberResponse(
                            member.getId(), record == null ? null : record.getId(), member.getName(),
                            record == null ? "NOT_MARKED" : record.getStatus().name(),
                            record == null || record.getMarkedBy() == null ? null : record.getMarkedBy().getId(),
                            record == null ? null : record.getMarkedAt(), record == null ? null : record.getNote());
                }).toList();

        long recorded = eligible.stream().filter(registration -> {
            Attendance mark = markedByMember.get(registration.getMember().getId());
            return mark != null && mark.getStatus() != null;
        }).count();
        return new AttendanceRosterResponse(session.getId(), session.getCourseClass().getId(),
                session.getCourseClass().getName(), session.getStartTime(), session.getEndTime(), session.getStatus(),
                !isCancelled(session) && !LocalDateTime.now(clock).isBefore(session.getStartTime()),
                completeness(session, eligible.size(), (int) recorded), eligible.size(), (int) recorded, members);
    }

    @Transactional
    public AttendanceRosterResponse submitInitial(Integer sessionId, AttendanceSubmissionRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = classSessionRepository.findByIdForAttendanceUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + sessionId));
        authorizeMarkAccess(actor, session);
        validateSessionCanBeMarked(session);
        CourseClass lockedClass = courseClasses.findByIdForRegistrationUpdate(session.getCourseClass().getId())
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found"));

        List<CourseRegistration> eligible = eligibleRegistrations(session);
        if (eligible.isEmpty()) throw error(HttpStatus.CONFLICT, "Không có học viên đủ điều kiện điểm danh cho buổi học này.");
        Map<Integer, Attendance> existingByMember = new HashMap<>();
        attendanceRepository.findByClassSessionId(sessionId)
                .forEach(record -> existingByMember.put(record.getMember().getId(), record));
        Map<Integer, group5.example.sportCenter.dto.AttendanceSubmissionMemberRequest> submitted = new HashMap<>();
        for (var row : request.members()) {
            if (row.memberId() == null || submitted.putIfAbsent(row.memberId(), row) != null) {
                throw error(HttpStatus.BAD_REQUEST, "Danh sách điểm danh có mã học viên bị thiếu hoặc trùng lặp.");
            }
        }
        Set<Integer> eligibleIds = eligible.stream().map(row -> row.getMember().getId()).collect(java.util.stream.Collectors.toSet());
        if (!submitted.keySet().equals(eligibleIds)) {
            throw error(HttpStatus.CONFLICT, "Danh sách điểm danh đã thay đổi hoặc chưa bao gồm đầy đủ học viên đủ điều kiện. Hãy tải lại danh sách.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (CourseRegistration registration : eligible) {
            Integer memberId = registration.getMember().getId();
            var row = submitted.get(memberId);
            Attendance existing = existingByMember.get(memberId);
            if (existing == null) {
                if (row.status() != AttendanceStatus.PRESENT && row.status() != AttendanceStatus.ABSENT) {
                    throw error(HttpStatus.BAD_REQUEST, "Điểm danh mới chỉ được chọn Có mặt hoặc Vắng mặt.");
                }
                if (trainingResults.existsByClassSessionIdAndMemberId(sessionId, memberId)) {
                    throw error(HttpStatus.CONFLICT, "Không thể hoàn tất điểm danh do kết quả tập luyện hiện có không khớp với trạng thái điểm danh.");
                }
            } else if (existing.getStatus() != row.status()) {
                throw error(HttpStatus.CONFLICT, "Không thể thay đổi trạng thái đã ghi nhận trong lần gửi điểm danh đầu tiên. Hãy dùng chức năng điều chỉnh có lý do.");
            }
        }

        // Session + class locks serialize result writes and registration changes. Only new rows are
        // inserted here; historical status, note, marker, and history rows remain untouched.
        for (CourseRegistration registration : eligible) {
            Integer memberId = registration.getMember().getId();
            if (existingByMember.containsKey(memberId)) continue;
            var selection = submitted.get(memberId);
            Attendance created = new Attendance();
            created.setClassSession(session);
            created.setMember(registration.getMember());
            created.setStatus(selection.status());
            created.setMarkedBy(actor);
            created.setMarkedAt(now);
            created.setNote(trimToNull(selection.note()));
            created = attendanceRepository.saveAndFlush(created);
            appendHistory(created, null, selection.status(), actor, now, null);
            auditLogs.record("MARK", "ATTENDANCE", created.getId(), "Attendance marked as " + created.getStatus().name() + ".");
            if (created.getStatus() == AttendanceStatus.ABSENT) {
                events.publishEvent(new NotificationBusinessEvents.AttendanceChanged(created.getId(), memberId,
                        lockedClass.getName(), session.getStartTime(), null, created.getStatus().name()));
            }
        }
        return roster(sessionId, jwt);
    }

    @Transactional
    public AttendanceResponse mark(Integer sessionId, Integer memberId, MarkAttendanceRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = classSessionRepository.findByIdForAttendanceUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + sessionId));
        authorizeMarkAccess(actor, session);
        validateSessionCanBeMarked(session);

        CourseRegistration eligibleRegistration = eligibleRegistrations(session).stream()
                .filter(registration -> registration.getMember().getId().equals(memberId)).findFirst().orElse(null);
        if (eligibleRegistration == null) throw notRegistered();

        Attendance existing = attendanceRepository.findByClassSessionIdAndMemberId(sessionId, memberId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (existing == null) {
            throw error(HttpStatus.CONFLICT,
                    "Điểm danh ban đầu phải được gửi đầy đủ cho toàn bộ học viên trong một lần.");
        }

        // Repeating the same status is idempotent: return the stored row unchanged and
        // do not alter its latest marker, note, or history.
        if (existing.getStatus() == request.status()) {
            return attendanceResponse(existing);
        }

        if (request.status() != AttendanceStatus.PRESENT
                && trainingResults.existsByClassSessionIdAndMemberId(sessionId, memberId)) {
            throw error(HttpStatus.CONFLICT,
                    "Học viên đã có kết quả tập luyện cho buổi học này. Không thể chuyển trạng thái điểm danh sang vắng mặt hoặc vắng có phép.");
        }

        if (request.status() == AttendanceStatus.EXCUSED) {
            throw error(HttpStatus.BAD_REQUEST, "Không thể chọn Vắng có phép cho một lần điểm danh hoặc điều chỉnh mới.");
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
        AttendanceHistory changed = appendHistory(saved, previous, request.status(), actor, now, reason);
        auditLogs.record("CORRECT", "ATTENDANCE", saved.getId(), "Attendance corrected from "
                + previous.name() + " to " + saved.getStatus().name() + ".");
        events.publishEvent(new NotificationBusinessEvents.AttendanceChanged(changed.getId(), memberId,
                session.getCourseClass().getName(), session.getStartTime(), previous.name(), saved.getStatus().name()));
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

    public List<MemberAttendanceOverviewResponse> memberOverview(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        LocalDateTime now = LocalDateTime.now(clock);
        List<CourseRegistration> memberRegistrations = registrationRepository
                .findByMemberIdOrderByRegisteredAtDescIdDesc(actor.getId()).stream()
                .filter(registration -> registration.getStatus() == CourseRegistrationStatus.CONFIRMED
                        || registration.getStatus() == CourseRegistrationStatus.CANCELLED)
                .toList();
        List<Integer> classIds = memberRegistrations.stream().map(row -> row.getCourseClass().getId()).distinct().toList();
        if (classIds.isEmpty()) return List.of();
        List<ClassSession> sessions = classSessionRepository.findByClassIds(classIds);
        List<Integer> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        Map<Integer, Attendance> marks = sessionIds.isEmpty() ? Map.of() : attendanceRepository
                .findByClassSessionIdIn(sessionIds).stream().filter(row -> row.getMember().getId().equals(actor.getId()))
                .collect(java.util.stream.Collectors.toMap(row -> row.getClassSession().getId(), row -> row, (a, b) -> a));

        Map<Integer, List<ClassSession>> eligibleByClass = new java.util.LinkedHashMap<>();
        for (ClassSession session : sessions) {
            if (eligibleForMember(session, memberRegistrations)) {
                eligibleByClass.computeIfAbsent(session.getCourseClass().getId(), ignored -> new java.util.ArrayList<>()).add(session);
            }
        }
        return eligibleByClass.entrySet().stream().map(entry -> {
            List<ClassSession> classSessions = entry.getValue();
            ClassSession sample = classSessions.get(0);
            var courseClass = sample.getCourseClass();
            var offering = courseClass.getCourseOffering();
            var course = offering.getCourse();
            var sport = course.getSport();
            int present = 0, absent = 0, excused = 0, recordedElapsed = 0, elapsed = 0, future = 0;
            List<MemberAttendanceOverviewSessionResponse> responses = new java.util.ArrayList<>();
            for (ClassSession session : classSessions) {
                Attendance mark = marks.get(session.getId());
                boolean cancelled = isCancelled(session);
                boolean valid = !cancelled;
                boolean elapsedSession = !session.getEndTime().isAfter(now) && valid;
                if (session.getStartTime().isAfter(now) && valid) future++;
                if (elapsedSession) {
                    elapsed++;
                    if (mark != null && mark.getStatus() != null) {
                        recordedElapsed++;
                        switch (mark.getStatus()) {
                            case PRESENT -> present++;
                            case ABSENT -> absent++;
                            case EXCUSED -> excused++;
                        }
                    }
                }
                responses.add(new MemberAttendanceOverviewSessionResponse(session.getId(), session.getStartTime(),
                        session.getEndTime(), session.getStatus(), mark == null ? null : mark.getId(),
                        mark == null ? null : mark.getStatus().name(), valid));
            }
            return new MemberAttendanceOverviewResponse(courseClass.getId(), courseClass.getName(), course.getId(),
                    course.getName(), offering.getId(), offering.getName(), offering.getStartDate(), offering.getEndDate(),
                    sport.getId(), sport.getName(), courseClass.getCoach() == null ? null : courseClass.getCoach().getUser().getName(),
                    present, absent, excused, recordedElapsed, elapsed, Math.max(0, elapsed - recordedElapsed), future,
                    responses.stream().sorted(java.util.Comparator.comparing(MemberAttendanceOverviewSessionResponse::startTime))
                            .toList());
        }).sorted(java.util.Comparator.comparing(MemberAttendanceOverviewResponse::offeringStartDate,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(MemberAttendanceOverviewResponse::courseClassId)).toList();
    }

    private boolean eligibleForMember(ClassSession session, List<CourseRegistration> registrations) {
        return registrations.stream().filter(row -> row.getCourseClass().getId().equals(session.getCourseClass().getId()))
                .anyMatch(row -> row.getRegisteredAt() != null && !row.getRegisteredAt().isAfter(session.getStartTime())
                        && (row.getStatus() == CourseRegistrationStatus.CONFIRMED
                                || (row.getCancelledAt() != null && !row.getCancelledAt().isBefore(session.getStartTime()))));
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

    private AttendanceHistory appendHistory(Attendance attendance, AttendanceStatus oldStatus, AttendanceStatus newStatus,
            User actor, LocalDateTime changedAt, String reason) {
        AttendanceHistory history = new AttendanceHistory();
        history.setAttendance(attendance);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(actor);
        history.setChangedAt(changedAt);
        history.setReason(reason);
        return historyRepository.saveAndFlush(history);
    }

    private AttendanceSessionResponse sessionResponse(ClassSession session) {
        var courseClass = session.getCourseClass();
        var offering = courseClass.getCourseOffering();
        var course = offering.getCourse();
        var sport = course.getSport();
        int eligible = eligibleRegistrations(session).size();
        Set<Integer> eligibleMemberIds = eligibleRegistrations(session).stream()
                .map(registration -> registration.getMember().getId()).collect(java.util.stream.Collectors.toSet());
        int recorded = (int) attendanceRepository.findByClassSessionId(session.getId()).stream()
                .filter(mark -> eligibleMemberIds.contains(mark.getMember().getId()) && mark.getStatus() != null).count();
        return new AttendanceSessionResponse(session.getId(), courseClass.getId(), courseClass.getName(),
                offering.getId(), offering.getName(), course.getId(), course.getName(), sport.getId(), sport.getName(),
                courseClass.getCoach().getId(), courseClass.getCoach().getUser().getName(), offering.getStartDate(), offering.getEndDate(),
                session.getStartTime(), session.getEndTime(), session.getStatus(),
                completeness(session, eligible, recorded), eligible, recorded, attendanceTimeEligible(session));
    }

    private List<AttendanceSessionResponse> sessionResponses(List<ClassSession> sessions) {
        // The existing session result set is bounded by the current role's visible classes. Batch
        // registration/attendance reads to keep completeness derived from committed data without
        // issuing per-session queries.
        if (sessions.isEmpty()) return List.of();
        List<Integer> classIds = sessions.stream().map(row -> row.getCourseClass().getId()).distinct().toList();
        List<Integer> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        List<CourseRegistration> registrations = registrationRepository.findByCourseClassIdInAndStatusIn(classIds,
                List.of(CourseRegistrationStatus.CONFIRMED, CourseRegistrationStatus.CANCELLED));
        Map<Integer, List<CourseRegistration>> registrationsByClass = registrations.stream()
                .collect(java.util.stream.Collectors.groupingBy(row -> row.getCourseClass().getId()));
        Map<Integer, Set<Integer>> eligibleBySession = new HashMap<>();
        for (ClassSession session : sessions) eligibleBySession.put(session.getId(),
                eligibleRegistrations(session, registrationsByClass.getOrDefault(session.getCourseClass().getId(), List.of()))
                        .stream().map(row -> row.getMember().getId()).collect(java.util.stream.Collectors.toSet()));
        Map<Integer, Integer> recordedBySession = new HashMap<>();
        attendanceRepository.findByClassSessionIdIn(sessionIds).forEach(row -> {
            if (eligibleBySession.getOrDefault(row.getClassSession().getId(), Set.of()).contains(row.getMember().getId())
                    && row.getStatus() != null) recordedBySession.merge(row.getClassSession().getId(), 1, Integer::sum);
        });
        return sessions.stream().map(session -> {
            int eligible = eligibleBySession.get(session.getId()).size();
            int recorded = recordedBySession.getOrDefault(session.getId(), 0);
            var courseClass = session.getCourseClass();
            var offering = courseClass.getCourseOffering();
            var course = offering.getCourse();
            var sport = course.getSport();
            return new AttendanceSessionResponse(session.getId(), courseClass.getId(), courseClass.getName(),
                    offering.getId(), offering.getName(), course.getId(), course.getName(), sport.getId(), sport.getName(),
                    courseClass.getCoach().getId(), courseClass.getCoach().getUser().getName(), offering.getStartDate(), offering.getEndDate(),
                    session.getStartTime(), session.getEndTime(), session.getStatus(),
                    completeness(session, eligible, recorded), eligible, recorded, attendanceTimeEligible(session));
        }).toList();
    }

    private boolean attendanceTimeEligible(ClassSession session) {
        return !isCancelled(session) && !LocalDateTime.now(clock).isBefore(session.getStartTime());
    }

    private List<CourseRegistration> eligibleRegistrations(ClassSession session) {
        return eligibleRegistrations(session, registrationRepository.findByCourseClassIdAndStatusIn(
                session.getCourseClass().getId(), List.of(CourseRegistrationStatus.CONFIRMED, CourseRegistrationStatus.CANCELLED)));
    }

    private List<CourseRegistration> eligibleRegistrations(ClassSession session, List<CourseRegistration> candidates) {
        return candidates.stream().filter(registration -> registration.getRegisteredAt() != null
                && !registration.getRegisteredAt().isAfter(session.getStartTime()))
                .filter(registration -> registration.getStatus() == CourseRegistrationStatus.CONFIRMED
                        || (registration.getStatus() == CourseRegistrationStatus.CANCELLED
                                && registration.getCancelledAt() != null
                                && !registration.getCancelledAt().isBefore(session.getStartTime())))
                .toList();
    }

    private String completeness(ClassSession session, int eligible, int recorded) {
        if (isCancelled(session) || eligible == 0) return "NOT_APPLICABLE";
        if (LocalDateTime.now(clock).isBefore(session.getStartTime())) return "NOT_YET_ELIGIBLE";
        if (recorded == 0) return "NOT_RECORDED";
        return recorded == eligible ? "COMPLETE" : "PARTIAL";
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
