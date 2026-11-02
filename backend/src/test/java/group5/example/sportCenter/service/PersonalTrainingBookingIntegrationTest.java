package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.CreatePersonalTrainingBookingRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.GlobalExceptionHandler;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("phase1")
@TestPropertySource(properties = "app.pt.session-base-price=500000")
class PersonalTrainingBookingIntegrationTest {
    @Autowired PersonalTrainingBookingService service;
    @Autowired ClassSessionService classSessionService;
    @Autowired PtBookingExpirationService expirationService;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository notifications;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired PersonalTrainingBookingRepository bookings;
    @Autowired ClassSessionRepository classSessions;
    @Autowired CoachScheduleConflictService coachScheduleConflicts;
    @Autowired CourseClassRepository classes;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseRepository courses;
    @Autowired CourseRegistrationRepository courseRegistrations;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService paymentService;
    @Autowired NotificationService notificationService;
    @Autowired Clock clock;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private User member;
    private Coach coach;
    private Sport sport;
    private Jwt jwt;
    private LocalDateTime slot;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        bookings.deleteAll();
        classSessions.deleteAll();
        courseRegistrations.deleteAll();
        classes.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        memberships.deleteAll();
        payments.deleteAllInBatch();
        coachSports.deleteAll();
        coaches.deleteAll();
        sports.deleteAll();
        plans.deleteAll();
        users.deleteAll();

        member = user("MEMBER", "ACTIVE", "member-pt");
        sport = new Sport();
        sport.setName("PT Sport");
        sport.setStatus("ACTIVE");
        sport = sports.save(sport);

        User coachUser = user("COACH", "ACTIVE", "coach-pt");
        coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coaches.save(coach);
        CoachSport assignment = new CoachSport();
        assignment.setCoach(coach);
        assignment.setSport(sport);
        assignment.setExperienceYears(3);
        coachSports.save(assignment);

        MembershipPlan plan = plan(true, "15.00");
        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setStartDate(LocalDateTime.now().toLocalDate().minusDays(1));
        membership.setEndDate(LocalDateTime.now().toLocalDate().plusMonths(1));
        membership.setStatus("ACTIVE");
        membership.setAutoRenew(false);
        memberships.save(membership);

        jwt = jwt(member);
        slot = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2).withSecond(0).withNano(0);
    }

    @Test
    void booksNinetyMinuteSessionAndCalculatesMembershipDiscountServerSide() {
        var result = service.create(jwt, request(coach, sport, slot));
        assertEquals("PENDING_PAYMENT", result.status());
        assertEquals(slot.plusMinutes(90), result.endTime());
        assertEquals(new BigDecimal("500000.00"), result.originalPrice());
        assertEquals(new BigDecimal("75000.00"), result.discount());
        assertEquals(new BigDecimal("425000.00"), result.finalPrice());
        assertEquals(result.createdAt().plusMinutes(15), result.paymentExpiresAt());
        assertEquals(1, service.mine(jwt).size());
        assertEquals(0, notifications.count(), "A pending payment hold is not a confirmed booking event");
    }

    @Test
    void expiredPendingBookingReleasesItsSlotAndExpirationIsIdempotent() {
        var created = service.create(jwt, request(coach, sport, slot));
        PersonalTrainingBooking booking = bookings.findById(created.id()).orElseThrow();
        booking.setPaymentExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        bookings.saveAndFlush(booking);

        // The slot becomes reusable at its deadline even before the scheduled sweeper runs.
        var replacement = service.create(jwt, request(coach, sport, slot));
        assertEquals("PENDING_PAYMENT", replacement.status());
        assertEquals(1, expirationService.expireDueBookings());
        assertEquals("EXPIRED", bookings.findById(created.id()).orElseThrow().getStatus().name());
        assertEquals(0, expirationService.expireDueBookings());
        assertEquals(0, notifications.count(), "Expiration must not emit confirmation notifications");
    }

    @Test
    void lateVerifiedPaymentCannotConfirmExpiredBooking() {
        var created = service.create(jwt, request(coach, sport, slot));
        PersonalTrainingBooking booking = bookings.findById(created.id()).orElseThrow();
        booking.setPaymentExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        bookings.saveAndFlush(booking);

        assertEquals(false, expirationService.confirmAfterVerifiedPayment(created.id()));
        assertEquals("EXPIRED", bookings.findById(created.id()).orElseThrow().getStatus().name());
    }

    @Test
    void paymentConfirmationAndExpirationRaceCannotConfirmAnExpiredBooking() throws Exception {
        var created = service.create(jwt, request(coach, sport, slot));
        PersonalTrainingBooking booking = bookings.findById(created.id()).orElseThrow();
        booking.setPaymentExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        bookings.saveAndFlush(booking);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> confirm = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return expirationService.confirmAfterVerifiedPayment(created.id());
            });
            Future<Integer> expire = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return expirationService.expireDueBookings();
            });
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertEquals(false, confirm.get(15, TimeUnit.SECONDS));
            expire.get(15, TimeUnit.SECONDS);
        }
        assertEquals("EXPIRED", bookings.findById(created.id()).orElseThrow().getStatus().name());
        assertEquals(0, expirationService.expireDueBookings());
    }

    @Test
    void expirationDoesNotOverwriteAlreadyConfirmedBooking() {
        var created = service.create(jwt, request(coach, sport, slot));
        assertEquals(true, expirationService.confirmAfterVerifiedPayment(created.id()));
        assertEquals(0, expirationService.expireDueBookings());
        assertEquals("CONFIRMED", bookings.findById(created.id()).orElseThrow().getStatus().name());
    }

    @Test
    void rejectsPlanWithoutOneOnOneEligibilityAndKeepsAllowPersonalTrainingPlanSeparate() {
        memberships.deleteAll();
        MembershipPlan plan = plan(false, "50.00");
        Membership membership = membership(plan, member);
        memberships.save(membership);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, sport, slot))).getStatusCode().value());
    }

    @Test
    void rejectsMembershipOutsideItsSessionDateRange() {
        memberships.deleteAll();
        MembershipPlan plan = plan(true, "5.00");
        Membership membership = membership(plan, member);
        membership.setEndDate(slot.toLocalDate());
        memberships.save(membership);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, sport, slot))).getStatusCode().value());
    }

    @Test
    void permitsUnlimitedNonOverlappingBookingsButRejectsOverlapsForCoachOrMember() {
        service.create(jwt, request(coach, sport, slot));
        service.create(jwt, request(coach, sport, slot.plusMinutes(90)));
        service.create(jwt, request(coach, sport, slot.plusHours(3)));
        assertEquals(3, bookings.count());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, sport, slot.plusMinutes(30)))).getStatusCode().value());

        User otherCoachUser = user("COACH", "ACTIVE", "coach-pt-member-overlap");
        Coach otherCoach = new Coach();
        otherCoach.setUser(otherCoachUser);
        otherCoach.setStatus("ACTIVE");
        otherCoach = coaches.save(otherCoach);
        CoachSport qualification = new CoachSport();
        qualification.setCoach(otherCoach);
        qualification.setSport(sport);
        qualification.setExperienceYears(2);
        coachSports.save(qualification);
        Coach differentCoach = otherCoach;
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(differentCoach, sport, slot.plusMinutes(30))))
                .getStatusCode().value());
    }

    @Test
    void concurrentDifferentMembersCannotDoubleBookOneCoach() throws Exception {
        User second = user("MEMBER", "ACTIVE", "member-pt-concurrent");
        memberships.save(membership(plan(true, "10.00"), second));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> attemptAfter(start, ready, jwt, coach.getId(), sport.getId()));
            Future<Boolean> secondAttempt = pool.submit(() -> attemptAfter(start, ready, jwt(second), coach.getId(), sport.getId()));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (secondAttempt.get(15, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, bookings.count());
        }
    }

    @Test
    void concurrentPtAndClassSessionCreationSerializeOnTheCoachLock() throws Exception {
        Course course = new Course();
        course.setSport(sport);
        course.setName("Cross-flow schedule course");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);
        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Cross-flow schedule offering");
        offering.setPrice(new BigDecimal("100.00"));
        offering.setSessionDuration(60);
        offering.setPlannedSessions(1);
        offering.setStartDate(slot.toLocalDate());
        offering.setEndDate(slot.toLocalDate().plusDays(1));
        offering.setRegistrationDeadline(slot.minusDays(1));
        offering.setCancellationDeadline(slot.minusDays(1));
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);
        CourseClass group = new CourseClass();
        group.setCourseOffering(offering);
        group.setCoach(coach);
        group.setName("Cross-flow schedule class");
        group.setCapacity(10);
        group.setStatus("ACTIVE");
        group = classes.saveAndFlush(group);

        Integer classId = group.getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> pt = pool.submit(() -> crossFlowAttempt(start, ready, () ->
                    service.create(jwt, request(coach, sport, slot))));
            Future<String> classSession = pool.submit(() -> crossFlowAttempt(start, ready, () -> {
                var create = new group5.example.sportCenter.dto.CreateClassSessionRequest();
                create.setClassId(classId);
                create.setStartTime(slot);
                create.setEndTime(slot.plusHours(1));
                create.setStatus("SCHEDULED");
                return classSessionService.create(create);
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both scheduling transactions must reach the start gate");
            start.countDown();
            List<String> outcomes = List.of(pt.get(15, TimeUnit.SECONDS), classSession.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count(), outcomes.toString());
        }

        LocalDateTime end = slot.plusHours(2);
        int activePt = bookings.existsCoachOverlap(coach.getId(), slot, end, LocalDateTime.now(clock)) ? 1 : 0;
        int activeClass = classSessions.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                coach.getId(), "CANCELLED", end, slot) ? 1 : 0;
        assertEquals(1, activePt + activeClass, "exactly one committed reservation may occupy the slot");
    }

    private String crossFlowAttempt(CountDownLatch start, CountDownLatch ready, java.util.concurrent.Callable<?> action)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "cross-flow start gate was not released");
        try {
            action.call();
            return "CREATED";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) return "CONFLICT";
            throw exception;
        } catch (IllegalArgumentException exception) {
            return "CONFLICT";
        }
    }

    @Test
    void rejectsUnqualifiedCoachAndPastBooking() {
        Sport other = new Sport();
        other.setName("Other PT Sport");
        other.setStatus("ACTIVE");
        other = sports.save(other);
        Sport unassigned = other;
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, unassigned, slot))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, sport, LocalDateTime.now().minusMinutes(1))))
                .getStatusCode().value());
    }

    @Test
    void existingGroupClassSessionBlocksCoachAtOverlappingTime() {
        Course course = new Course();
        course.setSport(sport);
        course.setName("PT conflict fixture");
        course.setStatus("ACTIVE");
        course = courses.save(course);
        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("PT conflict fixture");
        offering.setPrice(new BigDecimal("1.00"));
        offering.setSessionDuration(90);
        offering.setStartDate(slot.toLocalDate());
        offering.setEndDate(slot.toLocalDate().plusDays(2));
        offering.setRegistrationDeadline(slot.minusDays(1));
        offering.setCancellationDeadline(slot.minusDays(1));
        offering.setStatus("OPEN");
        offering = offerings.save(offering);
        CourseClass group = new CourseClass();
        group.setCourseOffering(offering);
        group.setCoach(coach);
        group.setName("PT conflict fixture");
        group.setCapacity(10);
        group.setStatus("ACTIVE");
        group = classes.save(group);
        ClassSession session = new ClassSession();
        session.setCourseClass(group);
        session.setStartTime(slot.minusMinutes(30));
        session.setEndTime(slot.plusMinutes(30));
        session.setStatus("SCHEDULED");
        classSessions.save(session);

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.create(jwt, request(coach, sport, slot))).getStatusCode().value());
    }

    @Test
    void coachHistoryIsScopedToAuthenticatedCoach() {
        service.create(jwt, request(coach, sport, slot));
        Jwt coachJwt = jwt(coach.getUser());
        List<?> assigned = service.assignedToMe(coachJwt);
        assertEquals(1, assigned.size());
    }

    @Test
    void httpEndpointsEnforceMemberAndCoachRolesAndReturnPendingPayment() throws Exception {
        String memberToken = tokens.generateToken(member);
        String coachToken = tokens.generateToken(coach.getUser());
        mvc.perform(get("/api/pt-bookings/assigned").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pt-bookings/me").header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isForbidden());
        String body = "{\"coachId\":" + coach.getId() + ",\"sportId\":" + sport.getId()
                + ",\"startTime\":\"" + slot + "\"}";
        mvc.perform(post("/api/pt-bookings").header("Authorization", "Bearer " + memberToken)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.durationMinutes").value(90));
    }

    @Test
    void memberCancelsAPendingBookingAndItsPendingPaymentAndTheSlotIsFreed() throws Exception {
        var booking = service.create(jwt, request(coach, sport, slot));
        PaymentResponse pending = paymentService.createPayment(ptPayment(booking.id()), jwt);

        mvc.perform(patch("/api/pt-bookings/" + booking.id() + "/cancel")
                        .header("Authorization", "Bearer " + tokens.generateToken(coach.getUser())))
                .andExpect(status().isForbidden());
        User other = user("MEMBER", "ACTIVE", "other-pt");
        mvc.perform(patch("/api/pt-bookings/" + booking.id() + "/cancel")
                        .header("Authorization", "Bearer " + tokens.generateToken(other)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/pt-bookings/" + booking.id() + "/cancel")
                        .header("Authorization", "Bearer " + tokens.generateToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(patch("/api/pt-bookings/" + booking.id() + "/cancel")
                        .header("Authorization", "Bearer " + tokens.generateToken(member)))
                .andExpect(status().isConflict());

        assertEquals(PaymentStatus.CANCELLED, payments.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(1, service.eligibleCoaches(jwt, sport.getId(), slot).size());
    }

    @Test
    void cancellingAConfirmedBookingMarksThePaymentForRefundAndNotifiesTheCoach() {
        var booking = service.create(jwt, request(coach, sport, slot));
        PaymentResponse paid = payAtCounter(booking.id());
        assertEquals("CONFIRMED", bookings.findById(booking.id()).orElseThrow().getStatus().name());

        var cancelled = service.cancel(jwt, booking.id());
        assertEquals("CANCELLED", cancelled.status());
        Payment refund = payments.findById(paid.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, refund.getStatus());
        assertTrue(refund.getNote().contains("PT booking #" + booking.id()));
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ? and title = ?",
                Long.class, coach.getUser().getId(), "Buổi PT bị huỷ"));
    }

    @Test
    void membersCannotCancelAConfirmedSessionWithin24HoursOnlyAdminCanAndReceptionistsNever() throws Exception {
        LocalDateTime soon = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(10).withSecond(0).withNano(0);
        var booking = service.create(jwt, request(coach, sport, soon));
        payAtCounter(booking.id());

        IllegalArgumentException tooLate = assertThrows(IllegalArgumentException.class, () -> service.cancel(jwt, booking.id()));
        assertTrue(tooLate.getMessage().contains("24 hours"));
        ResponseStatusException receptionistRefused = assertThrows(ResponseStatusException.class,
                () -> service.cancel(jwt(receptionist()), booking.id()));
        assertEquals(403, receptionistRefused.getStatusCode().value());
        mvc.perform(patch("/api/pt-bookings/" + booking.id() + "/cancel")
                        .header("Authorization", "Bearer " + tokens.generateToken(receptionist())))
                .andExpect(status().isForbidden());
        assertEquals("CANCELLED", service.cancel(jwt(admin()), booking.id()).status());
    }

    @Test
    void missingBasePriceFailsClearlyInsteadOfSubstitutingAValue() {
        PersonalTrainingBookingService unconfigured = new PersonalTrainingBookingService(bookings, users, coaches,
                coachSports, sports, memberships, coachScheduleConflicts, payments, notificationService, clock, "");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> unconfigured.eligibleCoaches(jwt, sport.getId(), slot));
        assertEquals(503, exception.getStatusCode().value());
        var error = new GlobalExceptionHandler().handlePtPricingConfiguration(
                (group5.example.sportCenter.exception.PtPricingConfigurationException) exception);
        assertEquals(503, error.getBody().status());
        assertTrue(error.getBody().message().contains("pricing is not configured"));
    }

    private CreatePaymentRequest ptPayment(Integer bookingId) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.PT_BOOKING);
        request.setReferenceId(bookingId);
        request.setMethod(PaymentMethod.COUNTER);
        return request;
    }

    private PaymentResponse payAtCounter(Integer bookingId) {
        PaymentResponse created = paymentService.createPayment(ptPayment(bookingId), jwt);
        CounterPaymentRequest counter = new CounterPaymentRequest();
        counter.setSuccess(true);
        return paymentService.processAtCounter(created.getId(), counter, jwt(receptionist()));
    }

    private User admin() {
        return users.findAll().stream().filter(u -> "ADMIN".equals(u.getRole())).findFirst()
                .orElseGet(() -> user("ADMIN", "ACTIVE", "admin-pt"));
    }

    private User receptionist() {
        return users.findAll().stream().filter(u -> "RECEPTIONIST".equals(u.getRole())).findFirst()
                .orElseGet(() -> user("RECEPTIONIST", "ACTIVE", "reception-pt"));
    }

    private User user(String role, String status, String suffix) {
        User user = new User();
        user.setName("Test " + suffix);
        user.setEmail(suffix + "@example.test");
        user.setPasswordHash("unused");
        user.setRole(role);
        user.setStatus(status);
        return users.save(user);
    }

    private MembershipPlan plan(boolean oneOnOne, String percent) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName("PT plan " + System.nanoTime());
        plan.setPrice(new BigDecimal("500.00"));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(new BigDecimal(percent));
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(oneOnOne);
        plan.setStatus("ACTIVE");
        return plans.save(plan);
    }

    private Membership membership(MembershipPlan plan, User owner) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(plan);
        membership.setStartDate(slot.toLocalDate().minusDays(1));
        membership.setEndDate(slot.toLocalDate().plusDays(1));
        membership.setStatus("ACTIVE");
        membership.setAutoRenew(false);
        return membership;
    }

    private CreatePersonalTrainingBookingRequest request(Coach assignedCoach, Sport selectedSport, LocalDateTime start) {
        CreatePersonalTrainingBookingRequest request = new CreatePersonalTrainingBookingRequest();
        request.setCoachId(assignedCoach.getId());
        request.setSportId(selectedSport.getId());
        request.setStartTime(start);
        return request;
    }

    private Jwt jwt(User user) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(String.valueOf(user.getId()))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("role", user.getRole())
                .build();
    }

    private boolean attemptAfter(CountDownLatch start, CountDownLatch ready, Jwt actor, Integer coachId, Integer sportId)
            throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            service.create(actor, request(coaches.findById(coachId).orElseThrow(), sports.findById(sportId).orElseThrow(), slot));
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 409) return false;
            throw ex;
        }
    }
}
