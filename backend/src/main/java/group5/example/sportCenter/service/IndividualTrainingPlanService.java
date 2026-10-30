package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.IndividualTrainingPlanRequest;
import group5.example.sportCenter.dto.IndividualTrainingPlanResponse;
import group5.example.sportCenter.dto.IndividualTrainingPlanRevisionResponse;
import group5.example.sportCenter.dto.IndividualTrainingWeekResponse;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.IndividualTrainingPlan;
import group5.example.sportCenter.entity.IndividualTrainingPlanExercise;
import group5.example.sportCenter.entity.IndividualTrainingPlanRevision;
import group5.example.sportCenter.entity.IndividualTrainingPlanWeek;
import group5.example.sportCenter.entity.TrainingExercise;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanExerciseRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanRevisionRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanWeekRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Individualized course-long training plans, separate from the shared class plan. */
@Service
@Transactional(readOnly = true)
public class IndividualTrainingPlanService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final IndividualTrainingPlanRepository plans;
    private final IndividualTrainingPlanRevisionRepository revisions;
    private final IndividualTrainingPlanWeekRepository weeks;
    private final IndividualTrainingPlanExerciseRepository exercises;
    private final CourseClassRepository classes;
    private final CourseRegistrationRepository registrations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AssessmentAccess access;
    private final AuditLogService auditLogs;
    private final Clock clock;

    public IndividualTrainingPlanService(IndividualTrainingPlanRepository plans,
            IndividualTrainingPlanRevisionRepository revisions, IndividualTrainingPlanWeekRepository weeks,
            IndividualTrainingPlanExerciseRepository exercises, CourseClassRepository classes,
            CourseRegistrationRepository registrations, MembershipRepository memberships,
            UserRepository users, AssessmentAccess access, AuditLogService auditLogs, Clock clock) {
        this.plans = plans;
        this.revisions = revisions;
        this.weeks = weeks;
        this.exercises = exercises;
        this.classes = classes;
        this.registrations = registrations;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public IndividualTrainingPlanResponse create(Integer classId, Integer memberId,
            IndividualTrainingPlanRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = classes.findByIdForScheduleUpdate(classId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + classId));
        authorizeCoachOrAdmin(actor, courseClass);

        registrations.findByMemberIdAndCourseClassIdAndStatusForUpdate(
                        memberId, classId, CourseRegistrationStatus.CONFIRMED)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,
                        "Member must have a CONFIRMED CourseRegistration for this CourseClass"));
        User member = users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw error(HttpStatus.CONFLICT, "An active Member account is required for an individual training plan");
        }
        requireCurrentPlanEntitlement(memberId);
        validateRequest(request);
        if (plans.existsByCourseClassIdAndMemberId(classId, memberId)) {
            throw error(HttpStatus.CONFLICT, "An individual training plan already exists for this Member and CourseClass");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        IndividualTrainingPlan plan = new IndividualTrainingPlan();
        plan.setCourseClass(courseClass);
        plan.setMember(member);
        plan.setCreatedBy(actor);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        plan = plans.saveAndFlush(plan);
        appendRevision(plan, request, actor, 1, now);
        auditLogs.record("CREATE", "INDIVIDUAL_TRAINING_PLAN", plan.getId(),
                "Individual training plan created for class " + classId + ".");
        return response(plan);
    }

    public List<IndividualTrainingPlanResponse> forClass(Integer classId, Jwt jwt) {
        User actor = access.actor(jwt);
        CourseClass courseClass = findClass(classId);
        authorizeCoachOrAdmin(actor, courseClass);
        return plans.findByCourseClassIdOrderByIdAsc(classId).stream().map(this::response).toList();
    }

    public List<IndividualTrainingPlanResponse> mine(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        return plans.findByMemberIdOrderByUpdatedAtDescIdDesc(actor.getId()).stream()
                .map(this::response).toList();
    }

    public IndividualTrainingPlanResponse detail(Integer planId, Jwt jwt) {
        User actor = access.actor(jwt);
        IndividualTrainingPlan plan = findPlan(planId);
        authorizePlanRead(actor, plan);
        return response(plan);
    }

    public List<IndividualTrainingPlanRevisionResponse> history(Integer planId, Jwt jwt) {
        User actor = access.actor(jwt);
        IndividualTrainingPlan plan = findPlan(planId);
        authorizePlanRead(actor, plan);
        return revisions.findByPlanIdOrderByRevisionNumberDesc(planId).stream()
                .map(this::revisionResponse).toList();
    }

    @Transactional
    public IndividualTrainingPlanResponse revise(Integer planId, IndividualTrainingPlanRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        IndividualTrainingPlan plan = plans.findByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Individual training plan not found with id: " + planId));
        CourseClass courseClass = classes.findByIdForScheduleUpdate(plan.getCourseClass().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CourseClass not found with id: " + plan.getCourseClass().getId()));
        authorizeCoachOrAdmin(actor, courseClass);
        User member = users.findByIdForBookingUpdate(plan.getMember().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + plan.getMember().getId()));
        requireCurrentPlanEntitlement(member.getId());
        validateRequest(request);

        int nextRevision = revisions.findFirstByPlanIdOrderByRevisionNumberDesc(planId)
                .map(revision -> revision.getRevisionNumber() + 1).orElse(1);
        LocalDateTime now = LocalDateTime.now(clock);
        appendRevision(plan, request, actor, nextRevision, now);
        plan.setUpdatedAt(now);
        plans.saveAndFlush(plan);
        auditLogs.record("UPDATE", "INDIVIDUAL_TRAINING_PLAN", plan.getId(),
                "Individual training plan revised to version " + nextRevision + ".");
        return response(plan);
    }

    private void appendRevision(IndividualTrainingPlan plan, IndividualTrainingPlanRequest request,
            User actor, int number, LocalDateTime now) {
        IndividualTrainingPlanRevision revision = new IndividualTrainingPlanRevision();
        revision.setPlan(plan);
        revision.setRevisionNumber(number);
        revision.setGoal(request.goal().trim());
        revision.setDescription(trimToNull(request.description()));
        revision.setRevisedBy(actor);
        revision.setCreatedAt(now);
        revision = revisions.saveAndFlush(revision);

        for (var weekRequest : request.weeks().stream()
                .sorted(java.util.Comparator.comparing(week -> week.weekNumber())).toList()) {
            IndividualTrainingPlanWeek week = new IndividualTrainingPlanWeek();
            week.setRevision(revision);
            week.setWeekNumber(weekRequest.weekNumber());
            week.setTitle(trimToNull(weekRequest.title()));
            week.setDescription(trimToNull(weekRequest.description()));
            week = weeks.saveAndFlush(week);
            for (int i = 0; i < weekRequest.exercises().size(); i++) {
                var exerciseRequest = weekRequest.exercises().get(i);
                IndividualTrainingPlanExercise exercise = new IndividualTrainingPlanExercise();
                exercise.setWeek(week);
                exercise.setName(exerciseRequest.name().trim());
                exercise.setSets(exerciseRequest.sets());
                exercise.setReps(exerciseRequest.reps());
                exercise.setDurationMinutes(exerciseRequest.durationMinutes());
                exercise.setNotes(trimToNull(exerciseRequest.notes()));
                exercise.setSortOrder(exerciseRequest.sortOrder() == null ? i : exerciseRequest.sortOrder());
                exercises.save(exercise);
            }
            exercises.flush();
        }
    }

    private void requireCurrentPlanEntitlement(Integer memberId) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (memberships.findEffectivePersonalTrainingPlanMemberships(memberId, today).isEmpty()) {
            throw error(HttpStatus.FORBIDDEN,
                    "A currently effective Membership with personal training plan eligibility is required");
        }
    }

    private void validateRequest(IndividualTrainingPlanRequest request) {
        if (request == null || request.weeks() == null || request.weeks().isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "At least one training week is required");
        }
        Set<Integer> weekNumbers = new HashSet<>();
        for (var week : request.weeks()) {
            if (week.weekNumber() == null || !weekNumbers.add(week.weekNumber())) {
                throw error(HttpStatus.BAD_REQUEST, "Week numbers must be unique within a revision");
            }
            if (week.exercises() == null) {
                throw error(HttpStatus.BAD_REQUEST, "Every training week must include an exercises list");
            }
            Set<Integer> sortOrders = new HashSet<>();
            for (int index = 0; index < week.exercises().size(); index++) {
                Integer sortOrder = week.exercises().get(index).sortOrder();
                int effectiveOrder = sortOrder == null ? index : sortOrder;
                if (!sortOrders.add(effectiveOrder)) {
                    throw error(HttpStatus.BAD_REQUEST, "Exercise sortOrder values must be unique within a week");
                }
            }
        }
    }

    private void authorizePlanRead(User actor, IndividualTrainingPlan plan) {
        if ("MEMBER".equalsIgnoreCase(actor.getRole())) {
            if (!plan.getMember().getId().equals(actor.getId())) {
                throw error(HttpStatus.FORBIDDEN, "Members may read only their own individual training plans");
            }
            return;
        }
        authorizeCoachOrAdmin(actor, plan.getCourseClass());
    }

    private void authorizeCoachOrAdmin(User actor, CourseClass courseClass) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return;
        var coach = access.coach(actor);
        if (!coach.getId().equals(courseClass.getCoach().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned Coach or ADMIN can manage this individual training plan");
        }
    }

    private IndividualTrainingPlanResponse response(IndividualTrainingPlan plan) {
        IndividualTrainingPlanRevision current = revisions.findFirstByPlanIdOrderByRevisionNumberDesc(plan.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Individual training plan has no revision"));
        var courseClass = plan.getCourseClass();
        return new IndividualTrainingPlanResponse(plan.getId(), courseClass.getId(), courseClass.getName(),
                plan.getMember().getId(), plan.getMember().getName(), plan.getCreatedAt(), plan.getUpdatedAt(),
                revisionResponse(current));
    }

    private IndividualTrainingPlanRevisionResponse revisionResponse(IndividualTrainingPlanRevision revision) {
        List<IndividualTrainingWeekResponse> weekResponses = weeks
                .findByRevisionIdOrderByWeekNumberAscIdAsc(revision.getId()).stream()
                .map(week -> new IndividualTrainingWeekResponse(week.getWeekNumber(), week.getTitle(),
                        week.getDescription(), exercises.findByWeekIdOrderBySortOrderAscIdAsc(week.getId()).stream()
                                .map(exercise -> new group5.example.sportCenter.dto.TrainingExerciseResponse(
                                        exercise.getId(), exercise.getName(), exercise.getSets(), exercise.getReps(),
                                        exercise.getDurationMinutes(), exercise.getNotes(), exercise.getSortOrder()))
                                .toList()))
                .toList();
        return new IndividualTrainingPlanRevisionResponse(revision.getId(), revision.getRevisionNumber(),
                revision.getGoal(), revision.getDescription(), revision.getRevisedBy().getId(),
                revision.getRevisedBy().getName(), revision.getCreatedAt(), weekResponses);
    }

    private CourseClass findClass(Integer id) {
        return classes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + id));
    }

    private IndividualTrainingPlan findPlan(Integer id) {
        return plans.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Individual training plan not found with id: " + id));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
