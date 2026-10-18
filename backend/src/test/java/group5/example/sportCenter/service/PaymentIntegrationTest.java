package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class PaymentIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@payment.example.test";

    @Autowired PaymentService service;
    @Autowired PaymentReportService reports;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired PersonalTrainingBookingRepository bookings;
    @Autowired ClassSessionRepository classSessions;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired CourseClassRepository classes;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseRepository courses;
    @Autowired Clock clock;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private MockMvc mvc;
    private User member;
    private User otherMember;
    private User receptionist;
    private User admin;
    private MembershipPlan plan;
    private Coach coach;
    private Sport sport;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        cleanDatabase();

        member = user("MEMBER", "member-pay");
        otherMember = user("MEMBER", "other-pay");
        receptionist = user("RECEPTIONIST", "reception-pay");
        admin = user("ADMIN", "admin-pay");

        plan = new MembershipPlan();
        plan.setName("Pay plan " + System.nanoTime());
        plan.setPrice(new BigDecimal("300000.00"));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("ACTIVE");
        plan = plans.save(plan);

        sport = new Sport();
        sport.setName("Pay Sport");
        sport.setStatus("ACTIVE");
        sport = sports.save(sport);

        coach = new Coach();
        coach.setUser(user("COACH", "coach-pay"));
        coach.setStatus("ACTIVE");
        coach = coaches.save(coach);
    }

    @AfterEach
    void tearDown() {
        // Payment rows reference users; other test classes delete users in their own setUp.
        cleanDatabase();
    }

    // ------------------------------------------------------------ membership

    @Test
    void onlineMembershipPaymentActivatesThenRenewsFromCurrentEndDate() throws Exception {
        PaymentResponse first = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.ONLINE), jwt(member));
        assertEquals("PENDING", first.getStatus());
        assertEquals(new BigDecimal("300000.00"), first.getAmount());

        // The callback is public and authenticated only by the gateway signature.
        mvc.perform(get(callbackPath(first.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Membership> afterFirst = memberships.findByMemberId(member.getId());
        assertEquals(1, afterFirst.size());
        assertEquals(today, afterFirst.get(0).getStartDate());
        assertEquals(today.plusMonths(1), afterFirst.get(0).getEndDate());

        // Replaying the same signed callback must not activate a second membership.
        mvc.perform(get(callbackPath(first.getPaymentUrl()))).andExpect(status().isOk());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());

        PaymentResponse renewal = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("SUCCESS", service.processAtCounter(renewal.getId(), counter(true), jwt(receptionist)).getStatus());

        List<Membership> afterRenewal = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, afterRenewal.size());
        assertEquals(today.plusMonths(1), afterRenewal.get(1).getStartDate());
        assertEquals(today.plusMonths(2), afterRenewal.get(1).getEndDate());
    }

    @Test
    void forgedCallbackSignatureIsRejected() throws Exception {
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.ONLINE), jwt(member));
        mvc.perform(get("/api/payments/online/callback")
                        .param("transactionCode", payment.getTransactionCode())
                        .param("success", "true")
                        .param("signature", "forged"))
                .andExpect(status().isForbidden());
        assertEquals("PENDING", payments.findById(payment.getId()).orElseThrow().getStatus().name());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void memberCannotCreateMembershipWithoutPayment() throws Exception {
        String body = "{\"memberId\":" + member.getId() + ",\"planId\":" + plan.getId()
                + ",\"startDate\":\"" + LocalDate.now() + "\",\"autoRenew\":false}";
        mvc.perform(post("/api/memberships").header("Authorization", bearer(member))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void failedCounterPaymentIsCancelledAndDoesNotActivate() {
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        PaymentResponse result = service.processAtCounter(payment.getId(), counter(false), jwt(receptionist));
        assertEquals("CANCELLED", result.getStatus());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)));
    }

    @Test
    void successAfterMemberCancelledIsRoutedToRefund() throws Exception {
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.ONLINE), jwt(member));
        mvc.perform(patch("/api/payments/" + payment.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get(callbackPath(payment.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    // ------------------------------------------------------- course registration

    @Test
    void courseRegistrationPaymentChargesSnapshottedFinalPriceOnce() {
        CourseRegistration registration = registration(member, "500000.00", "50000.00");

        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        assertEquals(new BigDecimal("450000.00"), payment.getAmount());
        assertEquals("SUCCESS", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());

        assertThrows(IllegalArgumentException.class, () -> service.createPayment(
                request(PaymentPurpose.COURSE_REGISTRATION, registration.getId(), PaymentMethod.COUNTER), jwt(member)));

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var rows = reports.classRegistrationReport(today, today, jwt(admin));
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("450000.00"), rows.get(0).getTotalAmount());
    }

    @Test
    void secondPendingPaymentForAlreadyPaidRegistrationNeedsRefund() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse first = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        PaymentResponse second = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));

        assertEquals("SUCCESS", service.processAtCounter(first.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals("REFUND_REQUIRED",
                service.processAtCounter(second.getId(), counter(true), jwt(receptionist)).getStatus());
    }

    @Test
    void memberCannotPayForAnotherMembersRegistration() {
        CourseRegistration registration = registration(otherMember, "200000.00", "0.00");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.createPayment(
                request(PaymentPurpose.COURSE_REGISTRATION, registration.getId(), PaymentMethod.COUNTER), jwt(member)));
        assertEquals(403, error.getStatusCode().value());
    }

    // ------------------------------------------------------------- PT booking

    @Test
    void ptBookingPaymentWithinHoldConfirmsBooking() {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals(new BigDecimal("425000.00"), payment.getAmount());

        assertEquals("SUCCESS", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(PersonalTrainingBookingStatus.CONFIRMED, bookings.findById(booking.getId()).orElseThrow().getStatus());
    }

    @Test
    void latePtPaymentLeavesBookingExpiredAndIsExcludedFromRevenue() {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));

        PersonalTrainingBooking expired = bookings.findById(booking.getId()).orElseThrow();
        expired.setPaymentExpiresAt(now().minusSeconds(1));
        bookings.saveAndFlush(expired);

        PaymentResponse result = service.processAtCounter(payment.getId(), counter(true), jwt(receptionist));
        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals(PersonalTrainingBookingStatus.EXPIRED, bookings.findById(booking.getId()).orElseThrow().getStatus());

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        assertEquals(0, BigDecimal.ZERO.compareTo(reports.revenueReport(today, today, jwt(admin)).getTotalRevenue()));
    }

    @Test
    void expiredPtHoldCannotStartAPayment() {
        PersonalTrainingBooking booking = booking(now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(
                request(PaymentPurpose.PT_BOOKING, booking.getId(), PaymentMethod.COUNTER), jwt(member)));
    }

    // ------------------------------------------------------------ authorization

    @Test
    void httpRolesAreEnforcedOnPaymentAndReportEndpoints() throws Exception {
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));

        mvc.perform(patch("/api/payments/" + payment.getId() + "/counter").header("Authorization", bearer(member))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/payments/" + payment.getId() + "/cancel").header("Authorization", bearer(otherMember)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(payment.getId()));
        mvc.perform(get("/api/payments/member/" + member.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/revenue").param("from", "2026-01-01").param("to", "2026-12-31")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/revenue").param("from", "2026-01-01").param("to", "2026-12-31")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payments").header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":" + plan.getId()
                                + ",\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300000.0));
    }

    @Test
    void counterPaymentRecordsProcessingStaff() {
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertNull(payment.getPaymentUrl());
        service.processAtCounter(payment.getId(), counter(true), jwt(receptionist));
        assertEquals(receptionist.getId(),
                payments.findById(payment.getId()).orElseThrow().getProcessedBy().getId());
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Clears the payment-related tables, but removes users, coaches and sports only when this class
     * created them: other test classes may leave rows (e.g. member sport levels) that reference theirs.
     */
    private void cleanDatabase() {
        payments.deleteAll();
        bookings.deleteAll();
        classSessions.deleteAll();
        registrations.deleteAll();
        classes.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        memberships.deleteAll();
        plans.deleteAll();
        List<User> ownUsers = users.findAll().stream().filter(u -> u.getEmail().endsWith(EMAIL_DOMAIN)).toList();
        var ownUserIds = ownUsers.stream().map(User::getId).toList();
        coaches.deleteAll(coaches.findAll().stream()
                .filter(c -> ownUserIds.contains(c.getUser().getId())).toList());
        sports.deleteAll(sports.findAll().stream().filter(s -> s.getName().startsWith("Pay Sport")).toList());
        users.deleteAll(ownUsers);
    }

    private CourseRegistration registration(User owner, String originalPrice, String discount) {
        Course course = new Course();
        course.setSport(sport);
        course.setName("Pay course " + System.nanoTime());
        course.setStatus("ACTIVE");
        course = courses.save(course);

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Pay offering");
        offering.setPrice(new BigDecimal(originalPrice));
        offering.setSessionDuration(60);
        offering.setStartDate(LocalDate.now().plusDays(10));
        offering.setEndDate(LocalDate.now().plusDays(40));
        offering.setRegistrationDeadline(LocalDateTime.now().plusDays(5));
        offering.setCancellationDeadline(LocalDateTime.now().plusDays(5));
        offering.setStatus("OPEN");
        offering = offerings.save(offering);

        CourseClass courseClass = new CourseClass();
        courseClass.setCourseOffering(offering);
        courseClass.setCoach(coach);
        courseClass.setName("Pay class");
        courseClass.setCapacity(10);
        courseClass.setStatus("ACTIVE");
        courseClass = classes.save(courseClass);

        CourseRegistration registration = new CourseRegistration();
        registration.setMember(owner);
        registration.setCourseClass(courseClass);
        registration.setStatus(CourseRegistrationStatus.CONFIRMED);
        registration.setOriginalPrice(new BigDecimal(originalPrice));
        registration.setMembershipDiscount(new BigDecimal(discount));
        registration.setFinalPrice(new BigDecimal(originalPrice).subtract(new BigDecimal(discount)));
        return registrations.save(registration);
    }

    private PersonalTrainingBooking booking(LocalDateTime paymentExpiresAt) {
        LocalDateTime start = now().plusDays(2).withSecond(0).withNano(0);
        PersonalTrainingBooking booking = new PersonalTrainingBooking();
        booking.setMember(member);
        booking.setCoach(coach);
        booking.setSport(sport);
        booking.setStartTime(start);
        booking.setEndTime(start.plusMinutes(90));
        booking.setOriginalPrice(new BigDecimal("500000.00"));
        booking.setDiscount(new BigDecimal("75000.00"));
        booking.setFinalPrice(new BigDecimal("425000.00"));
        booking.setStatus(PersonalTrainingBookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(paymentExpiresAt);
        return bookings.save(booking);
    }

    private CreatePaymentRequest request(PaymentPurpose purpose, Integer referenceId, PaymentMethod method) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(purpose);
        request.setReferenceId(referenceId);
        request.setMethod(method);
        return request;
    }

    private CounterPaymentRequest counter(boolean success) {
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(success);
        return request;
    }

    private String callbackPath(String paymentUrl) {
        return paymentUrl.substring(paymentUrl.indexOf("/api/payments/online/callback"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }

    private User user(String role, String suffix) {
        User user = new User();
        user.setName("Test " + suffix);
        user.setEmail(suffix + EMAIL_DOMAIN);
        user.setPasswordHash("unused");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
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
}
