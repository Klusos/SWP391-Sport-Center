package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.ClassProgressMemberResponse;
import group5.example.sportCenter.dto.MemberTrainingClassResponse;
import group5.example.sportCenter.dto.ProgressEvaluationRequest;
import group5.example.sportCenter.dto.ProgressEvaluationResponse;
import group5.example.sportCenter.dto.RecordTrainingResultRequest;
import group5.example.sportCenter.dto.TrainingClassResponse;
import group5.example.sportCenter.dto.TrainingExerciseRequest;
import group5.example.sportCenter.dto.TrainingExerciseResponse;
import group5.example.sportCenter.dto.TrainingPlanResponse;
import group5.example.sportCenter.dto.TrainingResultResponse;
import group5.example.sportCenter.dto.TrainingResultRowResponse;
import group5.example.sportCenter.dto.TrainingResultSheetResponse;
import group5.example.sportCenter.dto.TrainingSessionResponse;
import group5.example.sportCenter.dto.UpsertTrainingPlanRequest;
import group5.example.sportCenter.entity.Attendance;
import group5.example.sportCenter.entity.AttendanceStatus;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.ProgressEvaluation;
import group5.example.sportCenter.entity.TrainingExercise;
import group5.example.sportCenter.entity.TrainingPlan;
import group5.example.sportCenter.entity.TrainingResult;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.AttendanceRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.ProgressEvaluationRepository;
import group5.example.sportCenter.repository.TrainingExerciseRepository;
import group5.example.sportCenter.repository.TrainingPlanRepository;
import group5.example.sportCenter.repository.TrainingResultRepository;

/**
 * Flow 4 (training and attendance): training plans, goals and exercises per CourseClass,
 * session completion, per-session training results and progress evaluations.
 * Writes are limited to the class's assigned Coach or ADMIN; reads also allow staff and
 * members with a CONFIRMED registration in the class (members read through {@link #mine}).
 */
@Service
@Transactional(readOnly = true)
public class TrainingService {

    private static final String SCHEDULED = "SCHEDULED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";

    private final TrainingPlanRepository plans;
    private final TrainingExerciseRepository exercises;
    private final TrainingResultRepository results;
    private final ProgressEvaluationRepository evaluations;
    private final CourseClassRepository classes;
    private final ClassSessionRepository sessions;
    private final CourseRegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final AssessmentAccess access;
    private final Clock clock;
    private final AuditLogService auditLogs;

    public TrainingService(
            TrainingPlanRepository plans,
            TrainingExerciseRepository exercises,
            TrainingResultRepository results,
            ProgressEvaluationRepository evaluations,
            CourseClassRepository classes,
            ClassSessionRepository sessions,
            CourseRegistrationRepository registrations,
            AttendanceRepository attendance,
            AssessmentAccess access,
            Clock clock,
            AuditLogService auditLogs) {
        this.plans = plans;
        this.exercises = exercises;
        this.results = results;
        this.evaluations = evaluations;
        this.classes = classes;
        this.sessions = sessions;
        this.registrations = registrations;
        this.attendance = attendance;
        this.access = access;
        this.clock = clock;
        this.auditLogs = auditLogs;
    }

    // ---- Classes and plans ------------------------------------------------------------------

    public List<TrainingClassResponse> classes(Jwt jwt) {
        User actor = access.actor(jwt);
        List<CourseClass> visible = access.staff(actor)
                ? classes.findAll()
                : classes.findByCoachId(access.coach(actor).getId());
        return visible.stream()
                .sorted(Comparator.comparing(CourseClass::getId))
                .map(this::classResponse)
                .toList();
    }

    public TrainingPlanResponse plan(Integer classId, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeClassRead(actor, courseClass);
        return plans.findByCourseClassId(classId)
                .map(this::planResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Training plan has not been created for class " + classId));
    }

    @Transactional
    public TrainingPlanResponse upsertPlan(Integer classId, UpsertTrainingPlanRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeClassWrite(actor, courseClass);
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingPlan plan = plans.findByCourseClassId(classId).orElse(null);
        boolean created = plan == null;
        if (created) {
            plan = new TrainingPlan();
            plan.setCourseClass(courseClass);
            plan.setCreatedAt(now);
        }
        plan.setGoal(request.goal().trim());
        plan.setDescription(trimToNull(request.description()));
        plan.setUpdatedBy(actor);
        plan.setUpdatedAt(now);
        TrainingPlan saved = plans.saveAndFlush(plan);
        auditLogs.record(created ? "CREATE" : "UPDATE", "TRAINING_PLAN", saved.getId(),
                "Training plan " + (created ? "created" : "updated") + " for class " + classId + ".");
        return planResponse(saved);
    }

    @Transactional
    public TrainingExerciseResponse addExercise(Integer classId, TrainingExerciseRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeClassWrite(actor, courseClass);
        TrainingPlan plan = plans.findByCourseClassId(classId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Create the training plan and goal before adding exercises"));
        TrainingExercise exercise = new TrainingExercise();
        exercise.setPlan(plan);
        applyExercise(exercise, request);
        if (request.sortOrder() == null) {
            int next = exercises.findByPlanIdOrderBySortOrderAscIdAsc(plan.getId()).stream()
                    .mapToInt(TrainingExercise::getSortOrder).max().orElse(-1) + 1;
            exercise.setSortOrder(next);
        }
        TrainingExercise saved = exercises.saveAndFlush(exercise);
        touchPlan(plan, actor);
        auditLogs.record("CREATE", "TRAINING_EXERCISE", saved.getId(), "Exercise added to class " + classId + ".");
        return exerciseResponse(saved);
    }

    @Transactional
    public TrainingExerciseResponse updateExercise(Integer exerciseId, TrainingExerciseRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        TrainingExercise exercise = findExercise(exerciseId);
        authorizeClassWrite(actor, exercise.getPlan().getCourseClass());
        applyExercise(exercise, request);
        TrainingExercise saved = exercises.saveAndFlush(exercise);
        touchPlan(exercise.getPlan(), actor);
        auditLogs.record("UPDATE", "TRAINING_EXERCISE", saved.getId(), "Exercise updated.");
        return exerciseResponse(saved);
    }

    @Transactional
    public void deleteExercise(Integer exerciseId, Jwt jwt) {
        User actor = access.actor(jwt);
        TrainingExercise exercise = findExercise(exerciseId);
        authorizeClassWrite(actor, exercise.getPlan().getCourseClass());
        TrainingPlan plan = exercise.getPlan();
        exercises.delete(exercise);
        exercises.flush();
        touchPlan(plan, actor);
        auditLogs.record("DELETE", "TRAINING_EXERCISE", exerciseId, "Exercise removed.");
    }

    // ---- Sessions and results ---------------------------------------------------------------

    public List<TrainingSessionResponse> classSessions(Integer classId, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeStaffOrCoachRead(actor, courseClass);
        return sessions.findByCourseClassId(classId).stream()
                .sorted(Comparator.comparing(ClassSession::getStartTime).thenComparing(ClassSession::getId))
                .map(this::sessionResponse)
                .toList();
    }

    @Transactional
    public TrainingSessionResponse completeSession(Integer sessionId, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = sessions.findByIdForAttendanceUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + sessionId));
        authorizeClassWrite(actor, session.getCourseClass());
        if (isStatus(session, COMPLETED)) {
            return sessionResponse(session);
        }
        if (isStatus(session, CANCELLED)) {
            throw error(HttpStatus.CONFLICT, "A CANCELLED ClassSession cannot be completed");
        }
        if (LocalDateTime.now(clock).isBefore(session.getEndTime())) {
            throw error(HttpStatus.CONFLICT, "A ClassSession can be completed only at or after its end time");
        }
        session.setStatus(COMPLETED);
        ClassSession saved = sessions.saveAndFlush(session);
        auditLogs.record("COMPLETE", "CLASS_SESSION", saved.getId(), "Class session conducted and marked COMPLETED.");
        return sessionResponse(saved);
    }

    public TrainingResultSheetResponse resultSheet(Integer sessionId, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = findSession(sessionId);
        CourseClass courseClass = session.getCourseClass();
        authorizeStaffOrCoachRead(actor, courseClass);

        Map<Integer, Attendance> attendanceByMember = attendance.findByClassSessionId(sessionId).stream()
                .collect(Collectors.toMap(record -> record.getMember().getId(), record -> record, (a, b) -> a));
        Map<Integer, TrainingResult> resultByMember = results.findByClassSessionId(sessionId).stream()
                .collect(Collectors.toMap(result -> result.getMember().getId(), result -> result, (a, b) -> a));

        List<TrainingResultRowResponse> rows = confirmedRegistrations(courseClass.getId()).stream()
                .map(CourseRegistration::getMember)
                .map(member -> {
                    Attendance record = attendanceByMember.get(member.getId());
                    TrainingResult result = resultByMember.get(member.getId());
                    return new TrainingResultRowResponse(member.getId(), member.getName(),
                            record == null ? "NOT_MARKED" : record.getStatus().name(),
                            result == null ? null : result.getId(), result == null ? null : result.getScore(),
                            result == null ? null : result.getNotes(),
                            result == null ? null : result.getRecordedBy().getId(),
                            result == null ? null : result.getRecordedAt());
                }).toList();

        return new TrainingResultSheetResponse(session.getId(), courseClass.getId(), courseClass.getName(),
                session.getStartTime(), session.getEndTime(), session.getStatus(), canRecordResults(session), rows);
    }

    @Transactional
    public TrainingResultResponse recordResult(Integer sessionId, Integer memberId,
            RecordTrainingResultRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        ClassSession session = sessions.findByIdForAttendanceUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + sessionId));
        authorizeClassWrite(actor, session.getCourseClass());
        if (isStatus(session, CANCELLED)) {
            throw error(HttpStatus.CONFLICT, "Training results cannot be recorded for a CANCELLED ClassSession");
        }
        if (LocalDateTime.now(clock).isBefore(session.getStartTime())) {
            throw error(HttpStatus.CONFLICT, "Training results can be recorded only at or after the ClassSession start time");
        }
        CourseRegistration registration = registrations.findByMemberIdAndCourseClassIdAndStatus(
                memberId, session.getCourseClass().getId(), CourseRegistrationStatus.CONFIRMED)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,
                        "Member must have a CONFIRMED CourseRegistration for this CourseClass"));
        Attendance record = attendance.findByClassSessionIdAndMemberId(sessionId, memberId).orElse(null);
        if (record == null || record.getStatus() != AttendanceStatus.PRESENT) {
            throw error(HttpStatus.CONFLICT, "Take attendance first: results can be recorded only for PRESENT members");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TrainingResult result = results.findByClassSessionIdAndMemberId(sessionId, memberId).orElse(null);
        boolean created = result == null;
        if (created) {
            result = new TrainingResult();
            result.setClassSession(session);
            result.setMember(registration.getMember());
        }
        result.setScore(request.score());
        result.setNotes(trimToNull(request.notes()));
        result.setRecordedBy(actor);
        result.setRecordedAt(now);
        TrainingResult saved = results.saveAndFlush(result);
        auditLogs.record(created ? "CREATE" : "UPDATE", "TRAINING_RESULT", saved.getId(),
                "Training result " + (created ? "recorded" : "updated") + " with score " + saved.getScore() + ".");
        return resultResponse(saved);
    }

    // ---- Progress evaluation ----------------------------------------------------------------

    public List<ClassProgressMemberResponse> classProgress(Integer classId, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeStaffOrCoachRead(actor, courseClass);

        List<ClassSession> classSessions = sessions.findByCourseClassId(classId);
        Map<Integer, List<Attendance>> attendanceByMember = new HashMap<>();
        Map<Integer, List<TrainingResult>> resultsByMember = new HashMap<>();
        for (ClassSession session : classSessions) {
            attendance.findByClassSessionId(session.getId()).forEach(record -> attendanceByMember
                    .computeIfAbsent(record.getMember().getId(), key -> new ArrayList<>()).add(record));
            results.findByClassSessionId(session.getId()).forEach(result -> resultsByMember
                    .computeIfAbsent(result.getMember().getId(), key -> new ArrayList<>()).add(result));
        }
        Map<Integer, List<ProgressEvaluationResponse>> evaluationsByMember = evaluations
                .findByCourseClassIdOrderByEvaluatedAtDescIdDesc(classId).stream()
                .map(this::evaluationResponse)
                .collect(Collectors.groupingBy(ProgressEvaluationResponse::memberId));

        return confirmedRegistrations(classId).stream()
                .map(CourseRegistration::getMember)
                .map(member -> {
                    List<Attendance> marked = attendanceByMember.getOrDefault(member.getId(), List.of());
                    List<TrainingResult> memberResults = resultsByMember.getOrDefault(member.getId(), List.of());
                    List<ProgressEvaluationResponse> history = evaluationsByMember.getOrDefault(member.getId(), List.of());
                    return new ClassProgressMemberResponse(member.getId(), member.getName(), marked.size(),
                            marked.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count(),
                            memberResults.size(), average(memberResults), history.isEmpty() ? null : history.get(0), history);
                }).toList();
    }

    @Transactional
    public ProgressEvaluationResponse evaluate(Integer classId, Integer memberId,
            ProgressEvaluationRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeClassWrite(actor, courseClass);
        CourseRegistration registration = registrations.findByMemberIdAndCourseClassIdAndStatus(
                memberId, classId, CourseRegistrationStatus.CONFIRMED)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,
                        "Member must have a CONFIRMED CourseRegistration for this CourseClass"));
        ProgressEvaluation evaluation = new ProgressEvaluation();
        evaluation.setCourseClass(courseClass);
        evaluation.setMember(registration.getMember());
        evaluation.setRating(request.rating());
        evaluation.setComment(request.comment().trim());
        evaluation.setEvaluatedBy(actor);
        evaluation.setEvaluatedAt(LocalDateTime.now(clock));
        ProgressEvaluation saved = evaluations.saveAndFlush(evaluation);
        auditLogs.record("CREATE", "PROGRESS_EVALUATION", saved.getId(),
                "Progress evaluated with rating " + saved.getRating() + " for class " + classId + ".");
        return evaluationResponse(saved);
    }

    // ---- Member view ------------------------------------------------------------------------

    public List<MemberTrainingClassResponse> mine(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        List<Attendance> memberAttendance = attendance.findByMemberIdOrderByMarkedAtDescIdDesc(actor.getId());
        List<TrainingResult> memberResults = results.findByMemberIdOrderByClassSession_StartTimeDescIdDesc(actor.getId());
        List<ProgressEvaluation> memberEvaluations = evaluations.findByMemberIdOrderByEvaluatedAtDescIdDesc(actor.getId());

        return registrations.findByMemberIdOrderByRegisteredAtDescIdDesc(actor.getId()).stream()
                .filter(registration -> registration.getStatus() == CourseRegistrationStatus.CONFIRMED)
                .map(CourseRegistration::getCourseClass)
                .map(courseClass -> {
                    Integer classId = courseClass.getId();
                    List<Attendance> classAttendance = memberAttendance.stream()
                            .filter(record -> classId.equals(record.getClassSession().getCourseClass().getId())).toList();
                    List<TrainingResult> classResults = memberResults.stream()
                            .filter(result -> classId.equals(result.getClassSession().getCourseClass().getId())).toList();
                    return new MemberTrainingClassResponse(classResponse(courseClass),
                            plans.findByCourseClassId(classId).map(this::planResponse).orElse(null),
                            classAttendance.size(),
                            classAttendance.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count(),
                            average(classResults),
                            classResults.stream().map(this::resultResponse).toList(),
                            memberEvaluations.stream()
                                    .filter(evaluation -> classId.equals(evaluation.getCourseClass().getId()))
                                    .map(this::evaluationResponse).toList());
                }).toList();
    }

    // ---- Authorization ----------------------------------------------------------------------

    private void authorizeClassWrite(User actor, CourseClass courseClass) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return;
        var coach = access.coach(actor);
        if (!coach.getId().equals(courseClass.getCoach().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned Coach or ADMIN can manage training for this class");
        }
    }

    private void authorizeStaffOrCoachRead(User actor, CourseClass courseClass) {
        if (access.staff(actor)) return;
        var coach = access.coach(actor);
        if (!coach.getId().equals(courseClass.getCoach().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned Coach or staff can view training for this class");
        }
    }

    private void authorizeClassRead(User actor, CourseClass courseClass) {
        if ("MEMBER".equalsIgnoreCase(actor.getRole())) {
            if (registrations.findByMemberIdAndCourseClassIdAndStatus(
                    actor.getId(), courseClass.getId(), CourseRegistrationStatus.CONFIRMED).isEmpty()) {
                throw error(HttpStatus.FORBIDDEN, "Only members with a CONFIRMED registration can view this training plan");
            }
            return;
        }
        authorizeStaffOrCoachRead(actor, courseClass);
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private void applyExercise(TrainingExercise exercise, TrainingExerciseRequest request) {
        exercise.setName(request.name().trim());
        exercise.setSets(request.sets());
        exercise.setReps(request.reps());
        exercise.setDurationMinutes(request.durationMinutes());
        exercise.setNotes(trimToNull(request.notes()));
        if (request.sortOrder() != null) exercise.setSortOrder(request.sortOrder());
    }

    private void touchPlan(TrainingPlan plan, User actor) {
        plan.setUpdatedBy(actor);
        plan.setUpdatedAt(LocalDateTime.now(clock));
        plans.saveAndFlush(plan);
    }

    private List<CourseRegistration> confirmedRegistrations(Integer classId) {
        return registrations.findByCourseClassIdAndStatus(classId, CourseRegistrationStatus.CONFIRMED).stream()
                .sorted(Comparator.comparing(registration -> registration.getMember().getName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private boolean canRecordResults(ClassSession session) {
        return !isStatus(session, CANCELLED) && !LocalDateTime.now(clock).isBefore(session.getStartTime());
    }

    private static boolean isStatus(ClassSession session, String status) {
        return status.equalsIgnoreCase(session.getStatus());
    }

    private static Double average(List<TrainingResult> values) {
        OptionalDouble avg = values.stream().mapToInt(TrainingResult::getScore).average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null;
    }

    private CourseClass findClass(Integer id) {
        return classes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + id));
    }

    private ClassSession findSession(Integer id) {
        return sessions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession not found with id: " + id));
    }

    private TrainingExercise findExercise(Integer id) {
        return exercises.findWithPlanById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Training exercise not found with id: " + id));
    }

    private TrainingClassResponse classResponse(CourseClass courseClass) {
        var offering = courseClass.getCourseOffering();
        var course = offering.getCourse();
        var sport = course.getSport();
        var coach = courseClass.getCoach();
        List<ClassSession> classSessions = sessions.findByCourseClassId(courseClass.getId());
        TrainingPlan plan = plans.findByCourseClassId(courseClass.getId()).orElse(null);
        return new TrainingClassResponse(courseClass.getId(), courseClass.getName(), courseClass.getStatus(),
                offering.getId(), offering.getName(), offering.getStartDate(), offering.getEndDate(),
                course.getId(), course.getName(), sport.getId(), sport.getName(), coach.getId(),
                coach.getUser() == null ? null : coach.getUser().getName(),
                registrations.findByCourseClassIdAndStatus(courseClass.getId(), CourseRegistrationStatus.CONFIRMED).size(),
                classSessions.stream().filter(session -> !isStatus(session, CANCELLED)).count(),
                classSessions.stream().filter(session -> isStatus(session, COMPLETED)).count(),
                plan != null, plan == null ? null : plan.getGoal());
    }

    private TrainingPlanResponse planResponse(TrainingPlan plan) {
        return new TrainingPlanResponse(plan.getId(), plan.getCourseClass().getId(), plan.getGoal(),
                plan.getDescription(), plan.getUpdatedBy().getId(), plan.getUpdatedBy().getName(),
                plan.getCreatedAt(), plan.getUpdatedAt(),
                exercises.findByPlanIdOrderBySortOrderAscIdAsc(plan.getId()).stream().map(this::exerciseResponse).toList());
    }

    private TrainingExerciseResponse exerciseResponse(TrainingExercise exercise) {
        return new TrainingExerciseResponse(exercise.getId(), exercise.getName(), exercise.getSets(),
                exercise.getReps(), exercise.getDurationMinutes(), exercise.getNotes(), exercise.getSortOrder());
    }

    private TrainingSessionResponse sessionResponse(ClassSession session) {
        Set<Integer> present = attendance.findByClassSessionId(session.getId()).stream()
                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT)
                .map(record -> record.getMember().getId())
                .collect(Collectors.toSet());
        long recorded = results.findByClassSessionId(session.getId()).size();
        boolean canComplete = isStatus(session, SCHEDULED) && !LocalDateTime.now(clock).isBefore(session.getEndTime());
        return new TrainingSessionResponse(session.getId(), session.getCourseClass().getId(), session.getStartTime(),
                session.getEndTime(), session.getStatus(), present.size(), recorded, canComplete, canRecordResults(session));
    }

    private TrainingResultResponse resultResponse(TrainingResult result) {
        ClassSession session = result.getClassSession();
        return new TrainingResultResponse(result.getId(), session.getId(), session.getCourseClass().getId(),
                session.getCourseClass().getName(), session.getStartTime(), session.getEndTime(),
                result.getMember().getId(), result.getMember().getName(), result.getScore(), result.getNotes(),
                result.getRecordedBy().getId(), result.getRecordedBy().getName(), result.getRecordedAt());
    }

    private ProgressEvaluationResponse evaluationResponse(ProgressEvaluation evaluation) {
        return new ProgressEvaluationResponse(evaluation.getId(), evaluation.getCourseClass().getId(),
                evaluation.getCourseClass().getName(), evaluation.getMember().getId(), evaluation.getMember().getName(),
                evaluation.getRating(), evaluation.getComment(), evaluation.getEvaluatedBy().getId(),
                evaluation.getEvaluatedBy().getName(), evaluation.getEvaluatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
