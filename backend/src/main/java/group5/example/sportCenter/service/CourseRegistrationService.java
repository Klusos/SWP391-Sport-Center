package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.ClassSessionResponse;
import group5.example.sportCenter.dto.CourseRegistrationResponse;
import group5.example.sportCenter.dto.CreateCourseRegistrationRequest;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.MemberSportLevel;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.SportLevel;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.MemberSportLevelRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;
import static group5.example.sportCenter.service.AssessmentAccess.error;

@Service
@Transactional(readOnly = true)
public class CourseRegistrationService {

    private static final CourseRegistrationStatus PENDING = CourseRegistrationStatus.PENDING_PAYMENT;
    private static final CourseRegistrationStatus CONFIRMED = CourseRegistrationStatus.CONFIRMED;
    private static final String SCHEDULED = "SCHEDULED";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final CourseRegistrationRepository registrations;
    private final CourseClassRepository courseClasses;
    private final UserRepository users;
    private final MemberSportLevelRepository levels;
    private final MembershipRepository memberships;
    private final ClassSessionRepository classSessions;
    private final AssessmentAccess access;
    private final Clock clock;
    private final CourseRegistrationExpirationService expiration;

    public CourseRegistrationService(CourseRegistrationRepository registrations, CourseClassRepository courseClasses,
            UserRepository users, MemberSportLevelRepository levels, ClassSessionRepository classSessions,
            MembershipRepository memberships, AssessmentAccess access, Clock clock,
            CourseRegistrationExpirationService expiration) {
        this.registrations = registrations;
        this.courseClasses = courseClasses;
        this.users = users;
        this.levels = levels;
        this.memberships = memberships;
        this.classSessions = classSessions;
        this.access = access;
        this.clock = clock;
        this.expiration = expiration;
    }

    @Transactional
    public CourseRegistrationResponse register(CreateCourseRegistrationRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        return registerForMember(actor.getId(), request);
    }

    @Transactional
    public CourseRegistrationResponse registerForMember(Integer memberId, CreateCourseRegistrationRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireStaff(actor);
        if (memberId == null || memberId <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "A valid target member ID is required");
        }
        return registerForMember(memberId, request);
    }

    private CourseRegistrationResponse registerForMember(Integer memberId, CreateCourseRegistrationRequest request) {

        // Lock order is always MEMBER then CourseClass. The member lock serializes same-member
        // offering/schedule decisions; the class lock serializes seat counts for this class.
        User member = users.findByIdForAssessmentUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw error(HttpStatus.FORBIDDEN, "Only an ACTIVE MEMBER can register for a CourseClass");
        }

        CourseClass courseClass = courseClasses.findByIdForRegistrationUpdate(request.classId())
                .orElseThrow(() -> new ResourceNotFoundException("CourseClass not found with id: " + request.classId()));
        if (!"ACTIVE".equalsIgnoreCase(courseClass.getStatus())) {
            throw error(HttpStatus.CONFLICT, "CourseClass is not active for registration");
        }

        CourseOffering offering = courseClass.getCourseOffering();
        // Existing CourseOfferingService creates OPEN offerings; that is the project's open/active state.
        if (!"OPEN".equalsIgnoreCase(offering.getStatus())) {
            throw error(HttpStatus.CONFLICT, "CourseOffering is not open for registration");
        }
        Course course = offering.getCourse();
        if (!"ACTIVE".equalsIgnoreCase(course.getStatus())) {
            throw error(HttpStatus.CONFLICT, "Course is not active for registration");
        }
        if (!"ACTIVE".equalsIgnoreCase(course.getSport().getStatus())) {
            throw error(HttpStatus.CONFLICT, "Course sport is not active");
        }

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        LocalDate registrationDate = now.toLocalDate();
        validateRegistrationWindow(offering, registrationDate);
        validateRegistrationDeadline(offering, now);
        LocalDateTime paymentDeadline = now.plusHours(24);
        if (offering.getRegistrationDeadline().isBefore(paymentDeadline)) {
            paymentDeadline = offering.getRegistrationDeadline();
        }
        if (!paymentDeadline.isAfter(now)) {
            throw error(HttpStatus.CONFLICT, "CourseOffering registration deadline has passed");
        }
        validateAgeEligibility(member, course);
        validateSportLevel(member, course);

        expiration.expireForMember(member.getId(), now);
        expiration.expireForClass(courseClass.getId(), now);
        if (hasActiveRegistration(member.getId(), courseClass.getId())) {
            throw error(HttpStatus.CONFLICT, "Member is already registered for this CourseClass");
        }
        if (registrations.existsByMemberIdAndCourseClass_CourseOffering_IdAndStatus(
                    member.getId(), offering.getId(), CONFIRMED)
                || registrations.existsByMemberIdAndCourseClass_CourseOffering_IdAndStatus(
                    member.getId(), offering.getId(), PENDING)) {
            throw error(HttpStatus.CONFLICT, "Member may register for only one CourseClass in this CourseOffering");
        }

        long occupiedCount = registrations.countOccupiedSeats(courseClass.getId(), CONFIRMED, PENDING, now);
        if (occupiedCount >= courseClass.getCapacity()) {
            throw error(HttpStatus.CONFLICT, "CourseClass has reached its registration capacity");
        }

        List<CourseRegistration> existingRegistrations = registrations
                .findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(member.getId(), CONFIRMED);
        existingRegistrations = new ArrayList<>(existingRegistrations);
        existingRegistrations.addAll(registrations.findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(member.getId(), PENDING)
                .stream().filter(existing -> existing.getPaymentDeadline() != null
                        && existing.getPaymentDeadline().isAfter(now)).toList());
        List<Integer> existingClassIds = existingRegistrations.stream()
                .map(registration -> registration.getCourseClass().getId()).distinct().toList();
        validateSchedule(existingClassIds, courseClass.getId(), now);

        CourseRegistration registration = new CourseRegistration();
        registration.setMember(member);
        registration.setCourseClass(courseClass);
        registration.setStatus(PENDING);
        registration.setRegisteredAt(now);
        registration.setPaymentDeadline(paymentDeadline);
        BigDecimal originalPrice = offering.getPrice().setScale(2, RoundingMode.HALF_UP);
        if (originalPrice.signum() < 0) {
            throw error(HttpStatus.CONFLICT, "CourseOffering price is invalid for registration");
        }
        BigDecimal discountPercent = memberships
                .findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                        member.getId(), "ACTIVE", registrationDate, registrationDate)
                .stream()
                .map(Membership::getPlan)
                .map(MembershipPlan::getCourseDiscountPercent)
                .filter(percent -> percent != null && percent.signum() >= 0 && percent.compareTo(ONE_HUNDRED) <= 0)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal membershipDiscount = originalPrice.multiply(discountPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                .min(originalPrice)
                .max(ZERO_MONEY);
        registration.setOriginalPrice(originalPrice);
        registration.setMembershipDiscount(membershipDiscount);
        registration.setFinalPrice(originalPrice.subtract(membershipDiscount).setScale(2, RoundingMode.HALF_UP));
        return response(registrations.saveAndFlush(registration));
    }

    @Transactional
    public List<CourseRegistrationResponse> mine(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        expiration.expireForMember(actor.getId(), LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        return registrations.findByMemberIdOrderByRegisteredAtDescIdDesc(actor.getId()).stream()
                .map(this::response).toList();
    }

    public List<CourseRegistrationResponse> all(Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        return registrations.findAllByOrderByRegisteredAtDescIdDesc().stream().map(this::response).toList();
    }

    public List<ClassSessionResponse> myCourseSessions(Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        List<Integer> classIds = registrations
                .findByMemberIdAndStatusOrderByRegisteredAtDescIdDesc(actor.getId(), CONFIRMED)
                .stream().map(registration -> registration.getCourseClass().getId()).distinct().toList();
        if (classIds.isEmpty()) return List.of();
        return classSessions.findByClassIds(classIds).stream()
                .filter(session -> !"CANCELLED".equalsIgnoreCase(session.getStatus()))
                .map(this::sessionResponse).toList();
    }

    @Transactional
    public CourseRegistrationResponse cancel(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireMember(actor);
        CourseRegistration registration = registrations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course registration not found with id: " + id));
        if (!Objects.equals(registration.getMember().getId(), actor.getId())) {
            throw error(HttpStatus.FORBIDDEN, "You can only cancel your own CourseRegistration");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (expireIfDue(registration, now)) return response(registration);
        if (registration.getStatus() == PENDING) {
            registration.setStatus(CourseRegistrationStatus.CANCELLED);
            registration.setCancelledAt(now);
        } else {
            throw error(HttpStatus.CONFLICT, "Only unpaid pending course registrations can be cancelled");
        }
        return response(registrations.save(registration));
    }

    private boolean hasActiveRegistration(Integer memberId, Integer classId) {
        return registrations.existsByMemberIdAndCourseClassIdAndStatus(memberId, classId, CONFIRMED)
                || registrations.existsByMemberIdAndCourseClassIdAndStatus(memberId, classId, PENDING);
    }

    private boolean expireIfDue(CourseRegistration registration, LocalDateTime now) {
        if (registration.getStatus() == PENDING && registration.getPaymentDeadline() != null
                && !registration.getPaymentDeadline().isAfter(now)) {
            registration.setStatus(CourseRegistrationStatus.EXPIRED);
            return true;
        }
        return false;
    }

    private void validateRegistrationDeadline(CourseOffering offering, LocalDateTime now) {
        if (!now.isBefore(offering.getRegistrationDeadline())) {
            throw error(HttpStatus.CONFLICT, "CourseOffering registration deadline has passed");
        }
    }

    private void validateRegistrationWindow(CourseOffering offering, LocalDate registrationDate) {
        LocalDate opensOn = offering.getStartDate().minusDays(30);
        if (registrationDate.isBefore(opensOn)) {
            throw error(HttpStatus.CONFLICT, "CourseOffering registration opens on " + opensOn
                    + "; registration date is " + registrationDate);
        }
    }

    private void validateAgeEligibility(User member, Course course) {
        Integer minimum = course.getTargetAgeMin();
        Integer maximum = course.getTargetAgeMax();
        if (minimum == null && maximum == null) return;

        Integer age = member.getAge();
        if (age == null) {
            throw error(HttpStatus.CONFLICT, "Member date of birth is required to verify this Course age range");
        }
        if ((minimum != null && age < minimum) || (maximum != null && age > maximum)) {
            throw error(HttpStatus.CONFLICT, "Member does not meet this Course's target age range");
        }
    }

    private void validateSportLevel(User member, Course course) {
        SportLevel requiredLevel = parseRequiredLevel(course.getLevel());
        MemberSportLevel memberLevel = levels.findByMemberIdAndSportId(member.getId(), course.getSport().getId())
                .orElse(null);
        boolean hasVerifiedLevel = memberLevel != null
                && memberLevel.getVerifiedByCoach() != null
                && memberLevel.getVerifiedAt() != null;
        if (!hasVerifiedLevel) {
            if (requiredLevel != SportLevel.BEGINNER) throw insufficientLevel();
        } else if (memberLevel.getLevel().ordinal() < requiredLevel.ordinal()) {
            throw insufficientLevel();
        }
    }

    private SportLevel parseRequiredLevel(String value) {
        try {
            return SportLevel.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw error(HttpStatus.CONFLICT,
                    "Course has an unsupported required level; registration is unavailable");
        }
    }

    private void validateSchedule(List<Integer> existingClassIds, Integer requestedClassId, LocalDateTime now) {
        if (existingClassIds.isEmpty()) return;
        List<Integer> classIds = new ArrayList<>(existingClassIds);
        classIds.add(requestedClassId);
        List<ClassSession> futureScheduled = classSessions.findUpcomingByClassIdsAndStatus(classIds, SCHEDULED, now);
        List<ClassSession> requestedSessions = futureScheduled.stream()
                .filter(session -> requestedClassId.equals(session.getCourseClass().getId())).toList();
        List<ClassSession> existingSessions = futureScheduled.stream()
                .filter(session -> existingClassIds.contains(session.getCourseClass().getId())).toList();

        boolean conflict = requestedSessions.stream().anyMatch(requested -> existingSessions.stream().anyMatch(existing ->
                requested.getStartTime().isBefore(existing.getEndTime())
                        && requested.getEndTime().isAfter(existing.getStartTime())));
        if (conflict) {
            throw error(HttpStatus.CONFLICT, "CourseClass schedule conflicts with another registered CourseClass");
        }
    }

    private org.springframework.web.server.ResponseStatusException insufficientLevel() {
        return error(HttpStatus.CONFLICT,
                "Verified sport level is insufficient. Request a consultation and COACH assessment before registering.");
    }

    private CourseRegistrationResponse response(CourseRegistration registration) {
        CourseClass courseClass = registration.getCourseClass();
        CourseOffering offering = courseClass.getCourseOffering();
        Course course = offering.getCourse();
        return new CourseRegistrationResponse(registration.getId(), registration.getMember().getId(), course.getId(),
                course.getName(), offering.getId(), offering.getName(), courseClass.getId(), courseClass.getName(),
                course.getSport().getId(), course.getLevel(), registration.getStatus().name(),
                registration.getRegisteredAt(), registration.getPaymentDeadline(), registration.getCancelledAt(), registration.getOriginalPrice(),
                registration.getMembershipDiscount(), registration.getFinalPrice());
    }

    private ClassSessionResponse sessionResponse(ClassSession session) {
        CourseClass courseClass = session.getCourseClass();
        var facility = session.getFacility();
        return new ClassSessionResponse(session.getId(), courseClass.getId(), courseClass.getName(),
                facility == null ? null : facility.getId(), facility == null ? null : facility.getName(),
                session.getStartTime(), session.getEndTime(), session.getStatus(), List.of());
    }
}
