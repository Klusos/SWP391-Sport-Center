package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.RevenueGrouping;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentStatus;
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
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import group5.example.sportCenter.payment.PaymentGateway;

@SpringBootTest(properties = "app.payment.provider=mock")
@ActiveProfiles("phase1")
@Import(PaymentIntegrationTest.TestClockConfiguration.class)
class PaymentIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@payment.example.test";

    @Autowired PaymentService service;
    @Autowired PaymentGateway paymentGateway;
    @Autowired CourseRegistrationExpirationService registrationExpiration;
    @Autowired PaymentReportService reports;
    @Autowired PaymentRepository payments;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipUpgradeRepository membershipUpgrades;
    @Autowired MembershipService membershipService;
    @Autowired PersonalTrainingBookingRepository bookings;
    @Autowired ClassSessionRepository classSessions;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired CourseClassRepository classes;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseRepository courses;
    @Autowired TestClock clock;
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
        clock.setInstant(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
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
        clock.setInstant(clock.instant().plusSeconds(120));
        LocalDateTime successfulSettlementTime = now();

        // The callback is public and authenticated only by the gateway signature.
        mvc.perform(get(callbackPath(first.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(successfulSettlementTime, payments.findById(first.getId()).orElseThrow().getPaidAt(),
                "Ordinary Membership success keeps callback processing time as paidAt");

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Membership> afterFirst = memberships.findByMemberId(member.getId());
        assertEquals(1, afterFirst.size());
        assertEquals(today, afterFirst.get(0).getStartDate());
        assertEquals(today.plusMonths(1), afterFirst.get(0).getEndDate());

        // Replaying the same signed callback must not activate a second membership.
        clock.setInstant(clock.instant().plusSeconds(120));
        mvc.perform(get(callbackPath(first.getPaymentUrl()))).andExpect(status().isOk());
        assertEquals(successfulSettlementTime, payments.findById(first.getId()).orElseThrow().getPaidAt(),
                "Duplicate success callback cannot overwrite the first paidAt");
        assertEquals(1, memberships.findByMemberId(member.getId()).size());

        PaymentResponse renewal = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("SUCCESS", service.processAtCounter(renewal.getId(), counter(true), jwt(receptionist)).getStatus());

        List<Membership> afterRenewal = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, afterRenewal.size());
        assertEquals(today.plusMonths(1), afterRenewal.get(1).getStartDate());
        assertEquals(today.plusMonths(2), afterRenewal.get(1).getEndDate());
        assertEquals(1, afterRenewal.stream().filter(row -> row.getStartDate().isAfter(today)).count());
        assertEquals(today.plusMonths(1), afterRenewal.get(0).getEndDate(),
                "The original paid period remains unchanged after queue settlement");
    }

    @Test
    void ordinaryPurchaseAllowsOneQueuedPeriodButRejectsAnotherAcrossPlans() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        MembershipPlan anotherPlan = additionalPlan("Pay alternate");
        Membership current = membership(member, plan, today.minusDays(5), today.plusDays(25));

        PaymentResponse queued = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("SUCCESS", service.processAtCounter(queued.getId(), counter(true), jwt(receptionist)).getStatus());
        Membership future = memberships.findByMemberId(member.getId()).stream()
                .filter(row -> row.getStartDate().isAfter(today)).findFirst().orElseThrow();
        assertEquals(current.getEndDate(), future.getStartDate());

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, anotherPlan.getId(),
                        PaymentMethod.COUNTER), jwt(member)));
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void pendingMembershipPaymentBlocksAnotherPlanAndConcurrentPurchaseAttempts() throws Exception {
        MembershipPlan anotherPlan = additionalPlan("Pay alternate pending");
        service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member));
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, anotherPlan.getId(),
                        PaymentMethod.ONLINE), jwt(member)));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(row -> row.getPurpose() == PaymentPurpose.MEMBERSHIP
                        && row.getStatus() == PaymentStatus.PENDING).count());

        User concurrentMember = user("MEMBER", "concurrent-membership-pay");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> buy = () -> {
                ready.countDown();
                await(start);
                try {
                    service.createPayment(request(PaymentPurpose.MEMBERSHIP, anotherPlan.getId(),
                            PaymentMethod.COUNTER), jwt(concurrentMember));
                    return true;
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 409) throw exception;
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(buy);
            Future<Boolean> second = pool.submit(buy);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(concurrentMember.getId()).stream()
                .filter(row -> row.getPurpose() == PaymentPurpose.MEMBERSHIP
                        && row.getStatus() == PaymentStatus.PENDING).count());
    }

    @Test
    void concurrentDistinctMembershipSettlementsCreateOneFuturePeriodAndReconcileTheOther() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership current = membership(member, plan, today.minusDays(5), today.plusDays(25));
        MembershipPlan anotherPlan = additionalPlan("Pay competing settlement");
        Payment first = queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING);
        Payment second = queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING);
        second.setReferenceId(anotherPlan.getId());
        second = payments.saveAndFlush(second);
        Integer firstId = first.getId();
        Integer secondId = second.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> settleFirst = () -> {
                ready.countDown(); await(start);
                return service.processAtCounter(firstId, counter(true), jwt(receptionist)).getStatus();
            };
            java.util.concurrent.Callable<String> settleSecond = () -> {
                ready.countDown(); await(start);
                return service.processAtCounter(secondId, counter(true), jwt(receptionist)).getStatus();
            };
            Future<String> firstResult = pool.submit(settleFirst);
            Future<String> secondResult = pool.submit(settleSecond);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> statuses = List.of(firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, statuses.stream().filter("REFUND_REQUIRED"::equals).count());
        } finally {
            pool.shutdownNow();
        }

        List<Membership> rows = memberships.findByMemberId(member.getId());
        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().filter(row -> row.getStartDate().isAfter(today)).count());
        Membership unchanged = memberships.findById(current.getId()).orElseThrow();
        assertEquals(today.minusDays(5), unchanged.getStartDate());
        assertEquals(today.plusDays(25), unchanged.getEndDate());
        Payment refund = payments.findById(firstId).orElseThrow().getStatus() == PaymentStatus.REFUND_REQUIRED
                ? payments.findById(firstId).orElseThrow() : payments.findById(secondId).orElseThrow();
        assertNotNull(refund.getPaidAt(), "Verified counter receipt time is retained for reconciliation");
        assertTrue(refund.getNote().contains("future membership period"));
    }

    @Test
    void verifiedOnlineCaptureWithFullQueueIsReconciledWithoutCreatingAnotherMembership() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership current = membership(member, plan, today.minusDays(5), today.plusDays(25));
        membership(member, plan, current.getEndDate(), current.getEndDate().plusMonths(1));
        Payment payment = queueFixture(member, PaymentMethod.ONLINE, PaymentPurpose.MEMBERSHIP,
                PaymentStatus.PENDING);
        String callbackUrl = paymentGateway.createPaymentUrl(payment.getTransactionCode(), payment.getAmount(),
                "127.0.0.1");
        LocalDateTime verifiedPaymentTime = now();
        clock.setInstant(clock.instant().plusSeconds(120));

        mvc.perform(get(callbackPath(callbackUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));

        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(verifiedPaymentTime, payments.findById(payment.getId()).orElseThrow().getPaidAt(),
                "REFUND_REQUIRED retains verified provider payment time as reconciliation evidence");
        assertTrue(payments.findById(payment.getId()).orElseThrow().getNote()
                .contains("future membership period"));
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void membershipEndingTodayIsExclusiveWhenASettledPurchaseStarts() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership expiredToday = membership(member, plan, today.minusMonths(1), today);
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("SUCCESS", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());

        Membership activated = memberships.findByMemberId(member.getId()).stream()
                .filter(row -> !row.getId().equals(expiredToday.getId())).findFirst().orElseThrow();
        assertEquals(today, activated.getStartDate());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void concurrentAdminAssignmentsSerializeOnMemberAndPreserveTheExistingPeriod() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        CreateMembershipRequest request = assignment(otherMember, today.plusDays(10));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> assign = () -> {
                ready.countDown(); await(start);
                try {
                    membershipService.createMembership(request, jwt(admin));
                    return true;
                } catch (IllegalArgumentException conflict) {
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(assign);
            Future<Boolean> second = pool.submit(assign);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, memberships.findByMemberId(otherMember.getId()).size());
        assertTrue(memberships.findByMemberId(otherMember.getId()).get(0).getStartDate().isAfter(today));
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
    void receptionistMembershipSaleActivatesOnlyAfterCounterPaymentConfirmation() throws Exception {
        String paymentBody = "{\"memberId\":" + member.getId()
                + ",\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":" + plan.getId()
                + ",\"autoRenew\":false}";

        var created = mvc.perform(post("/api/payments").header("Authorization", bearer(receptionist))
                        .contentType("application/json").content(paymentBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(300000.0))
                .andReturn();
        Integer paymentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());

        mvc.perform(patch("/api/payments/" + paymentId + "/counter")
                        .header("Authorization", bearer(receptionist))
                        .contentType("application/json")
                        .content("{\"success\":true,\"note\":\"Payment received at front desk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals("ACTIVE", memberships.findByMemberId(member.getId()).get(0).getStatus());
    }

    @Test
    void memberSelfServicePurchaseUsesJwtAndPreventsRepeatedPendingPlanRequests() throws Exception {
        String body = "{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":"
                + plan.getId() + ",\"autoRenew\":false}";
        var created = mvc.perform(post("/api/payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(300000.0))
                .andReturn();
        Integer paymentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());

        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(post("/api/payments").header("Authorization", bearer(member))
                        .contentType("application/json")
                        .content("{\"memberId\":" + otherMember.getId()
                                + ",\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":"
                                + plan.getId() + "}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
        assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).size());
    }

    @Test
    void membershipPurchaseRejectsInactivePlanServerSide() throws Exception {
        assertTrue(payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).isEmpty());
        plan.setStatus("INACTIVE");
        plans.saveAndFlush(plan);
        mvc.perform(post("/api/payments").header("Authorization", bearer(member))
                        .contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":"
                                + plan.getId() + "}"))
                .andExpect(status().isConflict());
        assertTrue(payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).isEmpty());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void receptionistCannotUseLegacyMembershipCreationToBypassPayment() throws Exception {
        String body = "{\"memberId\":" + member.getId() + ",\"planId\":" + plan.getId()
                + ",\"startDate\":\"" + LocalDate.now() + "\",\"autoRenew\":false}";
        mvc.perform(post("/api/memberships").header("Authorization", bearer(receptionist))
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
        LocalDateTime verifiedPaymentTime = now();
        mvc.perform(patch("/api/payments/" + payment.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        clock.setInstant(clock.instant().plusSeconds(120));

        mvc.perform(get(callbackPath(payment.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
        Payment reconciled = payments.findById(payment.getId()).orElseThrow();
        assertEquals(verifiedPaymentTime, reconciled.getPaidAt(),
                "Refund reconciliation retains verified provider payment time");
        assertTrue(reconciled.getNote().contains("Verified provider payment time="));
    }

    // ------------------------------------------------------- course registration

    @Test
    void courseRegistrationPaymentChargesSnapshottedFinalPriceOnce() {
        CourseRegistration registration = registration(member, "500000.00", "50000.00");

        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        assertEquals(new BigDecimal("450000.00"), payment.getAmount());
        assertEquals("SUCCESS", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.CONFIRMED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());

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
    void paymentJustBeforeTheRegistrationDeadlineConfirmsTheHold() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        setDeadline(registration, now().plusNanos(100_000_000));

        assertEquals("SUCCESS",
                service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.CONFIRMED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(1L, occupiedSeats(registration));
    }

    @Test
    void paymentAtTheRegistrationDeadlineCannotReclaimExpiredSeat() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        setDeadline(registration, now());

        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals("REFUND_REQUIRED",
                service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.EXPIRED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(0L, occupiedSeats(registration), "late settlement must not reclaim an expired seat");
    }

    @Test
    void expiredPendingRegistrationCannotCreateANewPayment() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        setDeadline(registration, now());

        assertThrows(IllegalArgumentException.class,
                () -> service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                        registration.getId(), PaymentMethod.COUNTER), jwt(member)));
        assertFalse(payments.existsByPurposeAndReferenceIdAndStatus(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentStatus.PENDING));
        assertEquals(CourseRegistrationStatus.PENDING_PAYMENT,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
    }

    @Test
    void paymentJustAfterTheRegistrationDeadlineCannotReviveTheHold() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        setDeadline(registration, now().minusNanos(100_000_000));

        assertEquals("REFUND_REQUIRED",
                service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.EXPIRED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(0L, occupiedSeats(registration), "late settlement must not reclaim an expired seat");
    }

    @Test
    void expirationSweepLeavesUnpaidPendingPaymentPendingUntilReceiptIsConfirmed() {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        setDeadline(registration, now().minusNanos(100_000_000));

        assertEquals(1, registrationExpiration.expireDueRegistrations());
        assertEquals(CourseRegistrationStatus.EXPIRED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus(),
                "expiration alone is not evidence that money was received");
        assertEquals(0L, occupiedSeats(registration));

        assertEquals("REFUND_REQUIRED",
                service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.EXPIRED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(0L, occupiedSeats(registration));
    }

    private void setDeadline(CourseRegistration registration, LocalDateTime deadline) {
        CourseRegistration persisted = registrations.findById(registration.getId()).orElseThrow();
        persisted.setPaymentDeadline(deadline);
        registrations.saveAndFlush(persisted);
    }

    private long occupiedSeats(CourseRegistration registration) {
        return registrations.countOccupiedSeats(registration.getCourseClass().getId(),
                CourseRegistrationStatus.CONFIRMED, CourseRegistrationStatus.PENDING_PAYMENT, now());
    }

    @Test
    void concurrentRegistrationExpirationAndPaymentSettlementLeaveExpiredHoldUnconfirmed() throws Exception {
        CourseRegistration registration = registration(member, "200000.00", "0.00");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION,
                registration.getId(), PaymentMethod.COUNTER), jwt(member));
        CourseRegistration persisted = registrations.findById(registration.getId()).orElseThrow();
        persisted.setPaymentDeadline(now());
        registrations.saveAndFlush(persisted);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> settlement = pool.submit(() -> {
                ready.countDown();
                await(start);
                service.processAtCounter(payment.getId(), counter(true), jwt(receptionist));
            });
            Future<?> expiry = pool.submit(() -> {
                ready.countDown();
                await(start);
                registrationExpiration.expireDueRegistrations();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            settlement.get(10, TimeUnit.SECONDS);
            expiry.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(CourseRegistrationStatus.EXPIRED,
                registrations.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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
        assertEquals(0, notifications.count(), "PENDING_PAYMENT booking creation must not send confirmation notices");
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals(new BigDecimal("425000.00"), payment.getAmount());

        assertEquals("SUCCESS", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(PersonalTrainingBookingStatus.CONFIRMED, bookings.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));
        assertEquals(1, notifications.countByUserIdAndReadFalse(coach.getUser().getId()),
                "Coach notification belongs to linked User.id, not Coach.id");
        assertEquals(2, notifications.count());
        assertEquals(false, coach.getId().equals(coach.getUser().getId()),
                "Fixture must use distinct Coach and User identifiers");
    }

    @Test
    void pendingPtCounterQueueIsStaffOnlyAndExcludesOnlineAndExpiredHolds() throws Exception {
        PersonalTrainingBooking counterBooking = booking(now().plusMinutes(15));
        PaymentResponse counterPayment = service.createPayment(request(PaymentPurpose.PT_BOOKING, counterBooking.getId(),
                PaymentMethod.COUNTER), jwt(member));

        PersonalTrainingBooking onlineBooking = booking(now().plusMinutes(15));
        service.createPayment(request(PaymentPurpose.PT_BOOKING, onlineBooking.getId(), PaymentMethod.ONLINE), jwt(member));

        PersonalTrainingBooking expiredBooking = booking(now().minusSeconds(1));
        // Construct a pre-existing stale counter payment to prove the queue never treats an expired hold as payable.
        queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.PT_BOOKING, PaymentStatus.PENDING)
                .setReferenceId(expiredBooking.getId());
        payments.flush();

        String path = "/api/payments/pending-pt";
        mvc.perform(get(path).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(coach.getUser())))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(counterPayment.getId()))
                .andExpect(jsonPath("$.content[0].bookingId").value(counterBooking.getId()))
                .andExpect(jsonPath("$.content[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.content[0].memberName").value(member.getName()))
                .andExpect(jsonPath("$.content[0].sportName").value(sport.getName()))
                .andExpect(jsonPath("$.content[0].coachName").value(coach.getUser().getName()))
                .andExpect(jsonPath("$.content[0].amount").value(425000))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.content[0].paymentDeadline").isString())
                .andExpect(jsonPath("$.content[0].transactionCode").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void ptBookingRejectsSecondActivePaymentAndAllowsRetryAfterCancellation() {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse first = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                        PaymentMethod.ONLINE), jwt(member)));
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(payment -> payment.getPurpose() == PaymentPurpose.PT_BOOKING
                        && payment.getReferenceId().equals(booking.getId())
                        && payment.getStatus() == PaymentStatus.PENDING).count());

        service.cancelPayment(first.getId(), jwt(member));
        PaymentResponse retry = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.ONLINE), jwt(member));
        assertEquals(PaymentStatus.PENDING.name(), retry.getStatus());
        assertTrue(retry.getPaymentUrl() != null && !retry.getPaymentUrl().isBlank());
        assertEquals(PersonalTrainingBookingStatus.PENDING_PAYMENT,
                bookings.findById(booking.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentPtPaymentRequestsCreateOnlyOnePendingAttempt() throws Exception {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> issue = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                            PaymentMethod.COUNTER), jwt(member));
                    return true;
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 409) throw exception;
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(issue);
            Future<Boolean> second = pool.submit(issue);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(payment -> payment.getPurpose() == PaymentPurpose.PT_BOOKING
                        && payment.getReferenceId().equals(booking.getId())
                        && payment.getStatus() == PaymentStatus.PENDING).count());
    }

    @Test
    void failedPtPaymentCanBeRetriedAndPendingCounterPaymentCanBeConfirmedOnce() {
        PersonalTrainingBooking retryBooking = booking(now().plusMinutes(15));
        PaymentResponse failed = service.createPayment(request(PaymentPurpose.PT_BOOKING, retryBooking.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("CANCELLED", service.processAtCounter(failed.getId(), counter(false), jwt(receptionist)).getStatus());
        PaymentResponse retried = service.createPayment(request(PaymentPurpose.PT_BOOKING, retryBooking.getId(),
                PaymentMethod.ONLINE), jwt(member));
        assertEquals("ONLINE", retried.getMethod());

        PersonalTrainingBooking counterBooking = booking(now().plusMinutes(15));
        PaymentResponse pending = service.createPayment(request(PaymentPurpose.PT_BOOKING, counterBooking.getId(),
                PaymentMethod.COUNTER), jwt(member));
        assertEquals("SUCCESS", service.processAtCounter(pending.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(PersonalTrainingBookingStatus.CONFIRMED,
                bookings.findById(counterBooking.getId()).orElseThrow().getStatus());
    }

    @Test
    void replayedSuccessfulPtPaymentCallbackDoesNotDuplicateConfirmationNotices() throws Exception {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.ONLINE), jwt(member));

        mvc.perform(get(callbackPath(payment.getPaymentUrl()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(2, notifications.count());
        mvc.perform(get(callbackPath(payment.getPaymentUrl()))).andExpect(status().isOk());
        assertEquals(2, notifications.count());
        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));
        assertEquals(1, notifications.countByUserIdAndReadFalse(coach.getUser().getId()));
    }

    @Test
    void failedPtPaymentDoesNotSendConfirmationNotices() {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));

        assertEquals("CANCELLED", service.processAtCounter(payment.getId(), counter(false), jwt(receptionist)).getStatus());
        assertEquals(PersonalTrainingBookingStatus.PENDING_PAYMENT,
                bookings.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(0, notifications.count());
    }

    @Test
    void inactiveCoachUserDoesNotReceivePtConfirmationNotification() {
        PersonalTrainingBooking booking = booking(now().plusMinutes(15));
        PaymentResponse payment = service.createPayment(request(PaymentPurpose.PT_BOOKING, booking.getId(),
                PaymentMethod.COUNTER), jwt(member));
        coach.getUser().setStatus("INACTIVE");
        users.saveAndFlush(coach.getUser());

        service.processAtCounter(payment.getId(), counter(true), jwt(receptionist));

        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));
        assertEquals(0, notifications.countByUserIdAndReadFalse(coach.getUser().getId()));
        assertEquals(1, notifications.count());
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
        assertEquals(0, notifications.count());

        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        assertEquals(0, BigDecimal.ZERO.compareTo(reports.revenueReport(today, today, RevenueGrouping.MONTH, jwt(admin)).getTotalRevenue()));
    }

    @Test
    void revenueReportGroupsSupportedPaymentPurposesBySettlementDate() {
        reportPayment(PaymentPurpose.MEMBERSHIP, PaymentStatus.SUCCESS, "100.00", "2026-10-01T09:00:00");
        reportPayment(PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.SUCCESS, "200.00", "2026-10-31T23:59:59");
        reportPayment(PaymentPurpose.PT_BOOKING, PaymentStatus.SUCCESS, "300.00", "2026-11-01T23:59:59");
        reportPayment(PaymentPurpose.MEMBERSHIP, PaymentStatus.SUCCESS, "40.00", "2027-01-01T12:00:00");
        reportPayment(PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING, "900.00", "2026-10-15T12:00:00");
        reportPayment(PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.CANCELLED, "800.00", "2026-10-20T12:00:00");
        reportPayment(PaymentPurpose.PT_BOOKING, PaymentStatus.REFUND_REQUIRED, "700.00", "2026-10-25T12:00:00");

        var daily = reports.revenueReport(LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-02"),
                RevenueGrouping.DAY, jwt(admin));
        assertEquals(new BigDecimal("500.00"), daily.getTotalRevenue());
        assertEquals(3, daily.getPeriods().size());
        assertEquals(LocalDate.parse("2026-10-31"), daily.getPeriods().get(0).periodStart());
        assertEquals(new BigDecimal("200.00"), daily.getPeriods().get(0).totalRevenue());
        assertEquals(BigDecimal.ZERO, daily.getPeriods().get(2).totalRevenue());

        var monthly = reports.revenueReport(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01"),
                RevenueGrouping.MONTH, jwt(admin));
        assertEquals(3, monthly.getPeriods().size());
        assertEquals(new BigDecimal("600.00"), monthly.getTotalRevenue());
        assertEquals(new BigDecimal("100.00"), monthly.getRevenueByPurpose().get("MEMBERSHIP"));
        assertEquals(new BigDecimal("200.00"), monthly.getRevenueByPurpose().get("COURSE_REGISTRATION"));
        assertEquals(new BigDecimal("300.00"), monthly.getRevenueByPurpose().get("PT_BOOKING"));

        var yearly = reports.revenueReport(LocalDate.parse("2026-10-01"), LocalDate.parse("2027-01-01"),
                RevenueGrouping.YEAR, jwt(admin));
        assertEquals(2, yearly.getPeriods().size());
        assertEquals(new BigDecimal("640.00"), yearly.getTotalRevenue());
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

        mvc.perform(post("/api/payments").contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":"
                                + plan.getId() + "}"))
                .andExpect(status().isUnauthorized());
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
        mvc.perform(patch("/api/payments/" + payment.getId() + "/cancel")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payments").header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":" + plan.getId()
                                + ",\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300000.0));
        mvc.perform(post("/api/payments").header("Authorization", bearer(coach.getUser()))
                        .contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"memberId\":"
                                + member.getId() + ",\"referenceId\":" + plan.getId() + "}"))
                .andExpect(status().isForbidden());
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

    @Test
    void pendingMembershipQueueIsStaffOnly() throws Exception {
        String path = "/api/payments/pending-memberships";
        for (User staff : List.of(admin, receptionist)) {
            mvc.perform(get(path).header("Authorization", bearer(staff)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        }
        for (User forbidden : List.of(member, coach.getUser())) {
            mvc.perform(get(path).header("Authorization", bearer(forbidden)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path + "?page=-1").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path + "?size=101").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendingMembershipQueueFiltersAndPaginatesNewestFirst() throws Exception {
        var newer = queueFixture(otherMember, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP,
                PaymentStatus.PENDING);
        var older = queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP,
                PaymentStatus.PENDING);
        // Explicit timestamps also prove creation order, rather than relying on generated IDs.
        jdbc.update("update Payment set created_at = ? where id = ?", LocalDateTime.of(2026, 1, 1, 0, 0), newer.getId());
        jdbc.update("update Payment set created_at = ? where id = ?", LocalDateTime.of(2025, 1, 1, 0, 0), older.getId());
        queueFixture(member, PaymentMethod.ONLINE, PaymentPurpose.MEMBERSHIP,
                PaymentStatus.PENDING);
        queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION,
                PaymentStatus.PENDING);
        queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.PT_BOOKING,
                PaymentStatus.PENDING);
        for (var state : List.of(PaymentStatus.SUCCESS,
                PaymentStatus.CANCELLED,
                PaymentStatus.REFUND_REQUIRED)) {
            queueFixture(member, PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP, state);
        }
        mvc.perform(get("/api/payments/pending-memberships?size=1").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.content[0].memberId").value(otherMember.getId()))
                .andExpect(jsonPath("$.content[0].referenceId").value(plan.getId()))
                .andExpect(jsonPath("$.content[0].amount").value(300000));
        mvc.perform(get("/api/payments/pending-memberships?size=1&page=1").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(older.getId()));
        mvc.perform(get("/api/payments/pending-memberships?page=2&size=1").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void queueConfirmationActivatesOnceAndCancellationNeverActivates() throws Exception {
        var payment = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member));
        assertEquals(0, memberships.count());
        String path = "/api/payments/" + payment.getId() + "/counter";
        mvc.perform(patch(path).header("Authorization", bearer(receptionist))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(1, memberships.count());
        Membership activated = memberships.findAll().get(0);
        assertEquals(member.getId(), activated.getMember().getId());
        assertEquals(plan.getId(), activated.getPlan().getId());
        assertEquals("ACTIVE", activated.getStatus());
        mvc.perform(get("/api/payments/pending-memberships").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(patch(path).header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isConflict());
        assertEquals(1, memberships.count());
        var cancelled = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(otherMember));
        String cancelledPath = "/api/payments/" + cancelled.getId() + "/counter";
        mvc.perform(patch(cancelledPath).header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"success\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(patch(cancelledPath).header("Authorization", bearer(receptionist))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isConflict());
        assertEquals(1, memberships.count());
        mvc.perform(get("/api/payments/pending-memberships").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pendingCourseQueueRequiresActiveStaffAndValidPagination() throws Exception {
        String path = "/api/payments/pending-courses";
        for (User staff : List.of(admin, receptionist)) {
            mvc.perform(get(path).header("Authorization", bearer(staff)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        }
        for (User forbidden : List.of(member, coach.getUser())) {
            mvc.perform(get(path).header("Authorization", bearer(forbidden))).andExpect(status().isForbidden());
            assertThrows(ResponseStatusException.class, () -> service.getPendingCourseCounterPayments(0, 20, jwt(forbidden)));
        }
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String query : List.of("page=-1", "size=0", "size=101", "page=abc")) {
            mvc.perform(get(path + "?" + query).header("Authorization", bearer(admin)))
                    .andExpect(status().isBadRequest());
        }
        receptionist.setStatus("INACTIVE");
        users.saveAndFlush(receptionist);
        mvc.perform(get(path).header("Authorization", bearer(receptionist))).andExpect(status().isForbidden());
    }

    @Test
    void pendingCourseQueueReturnsOnlyActionableCounterHoldsAndSafeDetails() throws Exception {
        CourseRegistration hold = registration(member, "500000.00", "50000.00");
        var included = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, hold.getId(), PaymentMethod.COUNTER), jwt(member));
        courseQueueFixture(hold, PaymentMethod.ONLINE, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        for (PaymentPurpose purpose : List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.PT_BOOKING)) {
            courseQueueFixture(hold, PaymentMethod.COUNTER, purpose, PaymentStatus.PENDING);
        }
        for (PaymentStatus state : List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED, PaymentStatus.REFUND_REQUIRED)) {
            courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, state);
        }
        for (CourseRegistrationStatus state : List.of(CourseRegistrationStatus.EXPIRED,
                CourseRegistrationStatus.CANCELLED, CourseRegistrationStatus.CONFIRMED)) {
            var inactive = registration(otherMember, "500000.00", "0.00");
            inactive.setStatus(state);
            registrations.saveAndFlush(inactive);
            courseQueueFixture(inactive, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        }
        var stale = registration(otherMember, "500000.00", "0.00");
        stale.setPaymentDeadline(now().minusMinutes(1));
        registrations.saveAndFlush(stale);
        var stalePayment = courseQueueFixture(stale, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        var missingDeadline = registration(otherMember, "500000.00", "0.00");
        missingDeadline.setPaymentDeadline(null);
        registrations.saveAndFlush(missingDeadline);
        courseQueueFixture(missingDeadline, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        // Corrupt/missing purpose-scoped references must not produce a misleading staff row.
        var mismatch = courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        mismatch.setMember(otherMember);
        payments.saveAndFlush(mismatch);
        var orphan = courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        orphan.setReferenceId(Integer.MAX_VALUE);
        payments.saveAndFlush(orphan);

        mvc.perform(get("/api/payments/pending-courses").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(included.getId()))
                .andExpect(jsonPath("$.content[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.content[0].memberName").value(member.getName()))
                .andExpect(jsonPath("$.content[0].registrationId").value(hold.getId()))
                .andExpect(jsonPath("$.content[0].courseId").isNumber())
                .andExpect(jsonPath("$.content[0].courseName").isString())
                .andExpect(jsonPath("$.content[0].classId").isNumber())
                .andExpect(jsonPath("$.content[0].className").value("Pay class"))
                .andExpect(jsonPath("$.content[0].amount").value(450000))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].registrationStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.content[0].paymentDeadline").isString())
                .andExpect(jsonPath("$.content[0].paymentCreatedAt").isString())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].transactionCode").doesNotExist());
        Payment untouched = payments.findById(stalePayment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, untouched.getStatus());
        assertNull(untouched.getPaidAt());
    }

    @Test
    void pendingCourseQueueUsesStrictDeadlineComparisonIncludingEquality() {
        var hold = registration(member, "200000.00", "0.00");
        LocalDateTime boundary = now().withNano(0);
        hold.setPaymentDeadline(boundary);
        registrations.saveAndFlush(hold);
        courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        assertEquals(1, payments.findPendingCoursePayments(boundary.minusSeconds(1), page).getTotalElements());
        assertEquals(0, payments.findPendingCoursePayments(boundary, page).getTotalElements());
        assertEquals(0, payments.findPendingCoursePayments(boundary.plusSeconds(1), page).getTotalElements());
        assertEquals(0, service.getPendingCourseCounterPayments(0, 20, jwt(admin)).getTotalElements());
    }

    @Test
    void pendingCourseQueuePaginatesOldestFirstWithStableIdTies() throws Exception {
        var hold = registration(member, "200000.00", "0.00");
        var newer = courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        var first = courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        var second = courseQueueFixture(hold, PaymentMethod.COUNTER, PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        jdbc.update("update Payment set created_at = ? where id = ?", now().minusHours(1), newer.getId());
        LocalDateTime sameTime = now().minusHours(2).withNano(0);
        jdbc.update("update Payment set created_at = ? where id = ?", sameTime, first.getId());
        jdbc.update("update Payment set created_at = ? where id = ?", sameTime, second.getId());
        mvc.perform(get("/api/payments/pending-courses?size=2").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].paymentId").value(first.getId()))
                .andExpect(jsonPath("$.content[1].paymentId").value(second.getId()));
        mvc.perform(get("/api/payments/pending-courses?size=2&page=1").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(newer.getId()));
        mvc.perform(get("/api/payments/pending-courses?size=2&page=2").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void courseQueueDoesNotBroadenMemberPaymentHistoryAccess() throws Exception {
        var own = registration(member, "200000.00", "0.00");
        var others = registration(otherMember, "300000.00", "0.00");
        var ownPayment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, own.getId(), PaymentMethod.COUNTER), jwt(member));
        service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, others.getId(), PaymentMethod.COUNTER), jwt(otherMember));
        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownPayment.getId()))
                .andExpect(jsonPath("$[0].memberId").value(member.getId()));
        mvc.perform(get("/api/payments/member/" + otherMember.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void courseQueueSettlementConfirmsOnlyOnceAndRemovesAllAttemptsForThatHold() throws Exception {
        var hold = registration(member, "200000.00", "0.00");
        var payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, hold.getId(), PaymentMethod.COUNTER), jwt(member));
        var second = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, hold.getId(), PaymentMethod.COUNTER), jwt(member));
        mvc.perform(patch("/api/payments/" + payment.getId() + "/counter").header("Authorization", bearer(receptionist))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(CourseRegistrationStatus.CONFIRMED, registrations.findById(hold.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/payments/pending-courses").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(patch("/api/payments/" + payment.getId() + "/counter").header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isConflict());
        assertEquals("REFUND_REQUIRED", service.processAtCounter(second.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(1, registrations.count());
        assertEquals(CourseRegistrationStatus.CONFIRMED, registrations.findById(hold.getId()).orElseThrow().getStatus());
    }

    @Test
    void selectedCoursePaymentRechecksDeadlineAndLateReceiptNeverReclaimsSeat() throws Exception {
        var hold = registration(member, "200000.00", "0.00");
        var payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, hold.getId(), PaymentMethod.COUNTER), jwt(member));
        assertEquals(1, service.getPendingCourseCounterPayments(0, 20, jwt(receptionist)).getTotalElements());
        hold.setPaymentDeadline(now().minusSeconds(1));
        registrations.saveAndFlush(hold);
        assertEquals(0, service.getPendingCourseCounterPayments(0, 20, jwt(receptionist)).getTotalElements());
        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        mvc.perform(patch("/api/payments/" + payment.getId() + "/counter").header("Authorization", bearer(receptionist))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(CourseRegistrationStatus.EXPIRED, registrations.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void actionablePendingSummaryExcludesExpiredAndOverdueCourseHoldsWithoutChangingHistory() throws Exception {
        var valid = registration(member, "500000.00", "50000.00");
        var validPayment = service.createPayment(
                request(PaymentPurpose.COURSE_REGISTRATION, valid.getId(), PaymentMethod.COUNTER), jwt(member));

        var expired = registration(member, "250000.00", "0.00");
        expired.setStatus(CourseRegistrationStatus.EXPIRED);
        registrations.saveAndFlush(expired);
        var expiredPayment = courseQueueFixture(expired, PaymentMethod.ONLINE,
                PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);

        var overdue = registration(member, "250000.00", "0.00");
        overdue.setPaymentDeadline(now().minusSeconds(1));
        registrations.saveAndFlush(overdue); // Status intentionally remains PENDING_PAYMENT.
        var overduePayment = courseQueueFixture(overdue, PaymentMethod.COUNTER,
                PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);

        var anotherMembersRegistration = registration(otherMember, "250000.00", "0.00");
        var mismatchedOwnerPayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.COURSE_REGISTRATION, PaymentStatus.PENDING);
        mismatchedOwnerPayment.setReferenceId(anotherMembersRegistration.getId());
        payments.saveAndFlush(mismatchedOwnerPayment);

        var otherMembersPayment = queueFixture(otherMember, PaymentMethod.COUNTER,
                PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING);
        otherMembersPayment.setReferenceId(plan.getId());
        payments.saveAndFlush(otherMembersPayment);

        mvc.perform(get("/api/payments/me/actionable-summary").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalAmount").value(450000));

        // The summary is read-only. Reconciliation history still reports the original pending attempts.
        assertEquals(CourseRegistrationStatus.EXPIRED, registrations.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals(CourseRegistrationStatus.PENDING_PAYMENT, registrations.findById(overdue.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PENDING, payments.findById(expiredPayment.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PENDING, payments.findById(overduePayment.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        mvc.perform(get("/api/payments/me/actionable-summary").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalAmount").value(300000));
        mvc.perform(get("/api/payments/me/actionable-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actionablePendingSummaryChecksMembershipPtUpgradeAndOnlineLinkExpiry() throws Exception {
        var membershipPayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING);
        membershipPayment.setReferenceId(plan.getId());
        payments.saveAndFlush(membershipPayment);

        var validBooking = booking(now().plusMinutes(10));
        var validPtPayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.PT_BOOKING, PaymentStatus.PENDING);
        validPtPayment.setReferenceId(validBooking.getId());
        validPtPayment.setAmount(validBooking.getFinalPrice());
        payments.saveAndFlush(validPtPayment);

        var expiredBooking = booking(now().minusSeconds(1));
        var expiredPtPayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.PT_BOOKING, PaymentStatus.PENDING);
        expiredPtPayment.setReferenceId(expiredBooking.getId());
        payments.saveAndFlush(expiredPtPayment);

        Membership source = new Membership();
        source.setMember(member);
        source.setPlan(plan);
        source.setAutoRenew(false);
        source.setStartDate(LocalDate.now().minusDays(10));
        source.setEndDate(LocalDate.now().plusDays(20));
        source.setStatus("ACTIVE");
        source = memberships.saveAndFlush(source);

        var validUpgradePayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.UPGRADE, PaymentStatus.PENDING);
        validUpgradePayment.setReferenceId(plan.getId());
        payments.saveAndFlush(validUpgradePayment);
        MembershipUpgrade validUpgrade = new MembershipUpgrade();
        validUpgrade.setPayment(validUpgradePayment);
        validUpgrade.setSourceMembership(source);
        validUpgrade.setTargetPlan(plan);
        validUpgrade.setRemainingDays(20);
        validUpgrade.setExpiresAt(now().plusMinutes(10));
        validUpgrade.setCreatedAt(now());
        membershipUpgrades.saveAndFlush(validUpgrade);

        var expiredUpgradePayment = queueFixture(member, PaymentMethod.COUNTER,
                PaymentPurpose.UPGRADE, PaymentStatus.PENDING);
        expiredUpgradePayment.setReferenceId(plan.getId());
        payments.saveAndFlush(expiredUpgradePayment);
        MembershipUpgrade expiredUpgrade = new MembershipUpgrade();
        expiredUpgrade.setPayment(expiredUpgradePayment);
        expiredUpgrade.setSourceMembership(source);
        expiredUpgrade.setTargetPlan(plan);
        expiredUpgrade.setRemainingDays(20);
        expiredUpgrade.setExpiresAt(now().minusSeconds(1));
        expiredUpgrade.setCreatedAt(now().minusMinutes(1));
        membershipUpgrades.saveAndFlush(expiredUpgrade);

        mvc.perform(get("/api/payments/me/actionable-summary").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.totalAmount").value(1025000));
        assertEquals(PaymentStatus.PENDING, payments.findById(expiredPtPayment.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PENDING, payments.findById(expiredUpgradePayment.getId()).orElseThrow().getStatus());

        var onlinePayment = queueFixture(member, PaymentMethod.ONLINE,
                PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING);
        onlinePayment.setReferenceId(plan.getId());
        onlinePayment = payments.saveAndFlush(onlinePayment);
        mvc.perform(get("/api/payments/me/actionable-summary").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(4));
        clock.setInstant(clock.instant().plusSeconds(16 * 60));
        mvc.perform(get("/api/payments/me/actionable-summary").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        assertEquals(PaymentStatus.PENDING, payments.findById(onlinePayment.getId()).orElseThrow().getStatus());
    }

    @Test
    void canceledCourseHoldIsNeverRevivedByExplicitReceipt() {
        var hold = registration(member, "200000.00", "0.00");
        var payment = service.createPayment(request(PaymentPurpose.COURSE_REGISTRATION, hold.getId(), PaymentMethod.COUNTER), jwt(member));
        hold.setStatus(CourseRegistrationStatus.CANCELLED);
        registrations.saveAndFlush(hold);
        assertEquals(0, service.getPendingCourseCounterPayments(0, 20, jwt(admin)).getTotalElements());
        assertEquals("REFUND_REQUIRED", service.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(CourseRegistrationStatus.CANCELLED, registrations.findById(hold.getId()).orElseThrow().getStatus());
    }

    private Payment courseQueueFixture(CourseRegistration registration, PaymentMethod method, PaymentPurpose purpose, PaymentStatus state) {
        var payment = queueFixture(registration.getMember(), method, purpose, state);
        payment.setReferenceId(registration.getId());
        return payments.saveAndFlush(payment);
    }

    private Payment queueFixture(User owner, PaymentMethod method,
            PaymentPurpose purpose, PaymentStatus state) {
        var payment = new Payment();
        payment.setMember(owner);
        payment.setAmount(plan.getPrice());
        payment.setReferenceId(plan.getId());
        payment.setMethod(method);
        payment.setPurpose(purpose);
        payment.setStatus(state);
        payment.setTransactionCode("QUEUE-" + java.util.UUID.randomUUID().toString().substring(0, 20));
        return payments.save(payment);
    }

    private MembershipPlan additionalPlan(String name) {
        MembershipPlan result = new MembershipPlan();
        result.setName(name + " " + System.nanoTime());
        result.setPrice(new BigDecimal("450000.00"));
        result.setDurationMonths(1);
        result.setCourseDiscountPercent(BigDecimal.ZERO);
        result.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        result.setAllowPersonalTrainingPlan(false);
        result.setAllowOneOnOneCoaching(false);
        result.setStatus("ACTIVE");
        return plans.save(result);
    }

    private Membership membership(User owner, MembershipPlan membershipPlan, LocalDate start, LocalDate end) {
        Membership row = new Membership();
        row.setMember(owner);
        row.setPlan(membershipPlan);
        row.setAutoRenew(false);
        row.setStartDate(start);
        row.setEndDate(end);
        row.setStatus("ACTIVE");
        return memberships.saveAndFlush(row);
    }

    private CreateMembershipRequest assignment(User owner, LocalDate start) {
        CreateMembershipRequest result = new CreateMembershipRequest();
        result.setMemberId(owner.getId());
        result.setPlanId(plan.getId());
        result.setStartDate(start);
        result.setAutoRenew(false);
        return result;
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Clears the payment-related tables, but removes users, coaches and sports only when this class
     * created them: other test classes may leave rows (e.g. member sport levels) that reference theirs.
     */
    private void cleanDatabase() {
        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        membershipUpgrades.deleteAllInBatch();
        // Memberships reference the payment that bought them.
        memberships.deleteAll();
        payments.deleteAll();
        bookings.deleteAll();
        classSessions.deleteAll();
        registrations.deleteAll();
        classes.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        plans.deleteAll();
        List<User> ownUsers = users.findAll().stream().filter(u -> u.getEmail().endsWith(EMAIL_DOMAIN)).toList();
        var ownUserIds = ownUsers.stream().map(User::getId).toList();
        coaches.deleteAll(coaches.findAll().stream()
                .filter(c -> ownUserIds.contains(c.getUser().getId())).toList());
        sports.deleteAll(sports.findAll().stream().filter(s -> s.getName().startsWith("Pay Sport")).toList());
        users.deleteAll(ownUsers);
    }

    private Payment reportPayment(PaymentPurpose purpose, PaymentStatus status, String amount, String paidAt) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(purpose);
        payment.setReferenceId(999);
        payment.setStatus(status);
        payment.setTransactionCode("REPORT-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        payment.setPaidAt(LocalDateTime.parse(paidAt));
        return payments.saveAndFlush(payment);
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
        registration.setStatus(CourseRegistrationStatus.PENDING_PAYMENT);
        registration.setRegisteredAt(now());
        registration.setPaymentDeadline(now().plusHours(24));
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

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        TestClock paymentIntegrationTestClock() {
            return new TestClock();
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        TestClock() {
            this(new AtomicReference<>(Instant.EPOCH), ZoneId.systemDefault());
        }

        private TestClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new TestClock(current, zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
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
