package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
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
import java.util.Map;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.RevenueGrouping;
import group5.example.sportCenter.dto.SelectRenewalPaymentMethodRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.CheckoutProvider;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentCheckoutAttempt;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import group5.example.sportCenter.service.RenewalSettlementResult;
import group5.example.sportCenter.service.RenewalSettlementService;
import group5.example.sportCenter.service.VerifiedRenewalCapture;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(PaymentIntegrationTest.TestClockConfiguration.class)
class PaymentIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@payment.example.test";

    @Autowired PaymentService service;
    @MockitoSpyBean PaymentGateway paymentGateway;
    @Autowired MembershipRenewalService renewalService;
    @Autowired RenewalSettlementService renewalSettlementService;
    @Autowired MembershipService membershipService;
    @Autowired CourseRegistrationExpirationService registrationExpiration;
    @Autowired PaymentReportService reports;
    @Autowired PaymentRepository payments;
    @Autowired PaymentCheckoutAttemptRepository checkoutAttempts;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
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
        plan.setMonthlyPrice(new BigDecimal("300000.00"));
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
    void onlineMembershipPurchaseActivatesOnceAndCannotBeUsedForRenewal() throws Exception {
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

        ResponseStatusException samePlan = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member)));
        assertEquals(HttpStatus.CONFLICT, samePlan.getStatusCode());
        MembershipPlan otherPlan = new MembershipPlan();
        otherPlan.setName("Other pay plan " + System.nanoTime());
        otherPlan.setPrice(new BigDecimal("450000.00"));
        otherPlan.setMonthlyPrice(new BigDecimal("450000.00"));
        otherPlan.setDurationMonths(1);
        otherPlan.setCourseDiscountPercent(BigDecimal.ZERO);
        otherPlan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        otherPlan.setAllowPersonalTrainingPlan(false);
        otherPlan.setAllowOneOnOneCoaching(true);
        otherPlan.setStatus("ACTIVE");
        otherPlan = plans.saveAndFlush(otherPlan);
        Integer otherPlanId = otherPlan.getId();
        ResponseStatusException differentPlan = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, otherPlanId, PaymentMethod.COUNTER), jwt(member)));
        assertEquals(HttpStatus.CONFLICT, differentPlan.getStatusCode());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void ordinaryPurchaseRejectsFutureMembershipForMemberAndReceptionist() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership future = new Membership();
        future.setMember(member);
        future.setPlan(plan);
        future.setStartDate(today.plusDays(1));
        future.setEndDate(today.plusMonths(1).plusDays(1));
        future.setAutoRenew(false);
        future.setStatus("ACTIVE");
        memberships.saveAndFlush(future);

        ResponseStatusException memberAttempt = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member)));
        assertEquals(HttpStatus.CONFLICT, memberAttempt.getStatusCode());

        CreatePaymentRequest assisted = request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER);
        assisted.setMemberId(member.getId());
        ResponseStatusException staffAttempt = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(assisted, jwt(receptionist)));
        assertEquals(HttpStatus.CONFLICT, staffAttempt.getStatusCode());
        assertEquals(0, payments.count());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void concurrentOrdinaryPurchaseRequestsCreateOnlyOnePendingPayment() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> tryCreateOrdinaryPurchase(start));
            var second = pool.submit(() -> tryCreateOrdinaryPurchase(start));
            start.countDown();

            List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count(), outcomes.toString());
            assertEquals(1, payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                    .filter(payment -> payment.getPurpose() == PaymentPurpose.MEMBERSHIP
                            && payment.getStatus() == PaymentStatus.PENDING).count());
        } finally {
            pool.shutdownNow();
        }
    }

    private String tryCreateOrdinaryPurchase(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member));
            return "CREATED";
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode() == HttpStatus.CONFLICT ? "CONFLICT" : "OTHER";
        }
    }

    @Test
    void verifiedOnlineCaptureWithChangedEligibilityIsMarkedForReconciliation() throws Exception {
        PaymentResponse pending = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.ONLINE), jwt(member));
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership acquiredAfterCheckout = new Membership();
        acquiredAfterCheckout.setMember(member);
        acquiredAfterCheckout.setPlan(plan);
        acquiredAfterCheckout.setStartDate(today);
        acquiredAfterCheckout.setEndDate(today.plusMonths(1));
        acquiredAfterCheckout.setAutoRenew(false);
        acquiredAfterCheckout.setStatus("ACTIVE");
        memberships.saveAndFlush(acquiredAfterCheckout);

        mvc.perform(get(callbackPath(pending.getPaymentUrl())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void verifiedOnlineMembershipCaptureWithAmountMismatchIsReconciled() {
        PaymentResponse pending = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.ONLINE), jwt(member));
        Payment payment = payments.findById(pending.getId()).orElseThrow();
        Map<String, String> params = Map.of("vnp_TxnRef", payment.getTransactionCode());
        GatewayCallback capture = new GatewayCallback(payment.getTransactionCode(), true,
                payment.getAmount().add(new BigDecimal("1.00")), null,
                "provider-mismatch", "00", "00", payment.getTransactionCode());
        doReturn(Optional.of(capture)).when(paymentGateway).verifyCallback(params);

        OnlineCallbackResult result = service.processOnlineCallback(params);

        assertEquals(OnlineCallbackResult.Status.APPLIED, result.status());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertNotNull(payments.findById(payment.getId()).orElseThrow().getPaidAt());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void oldPendingCounterPurchaseCannotCreateASequentialMembership() {
        PaymentResponse oldPending = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership existing = new Membership();
        existing.setMember(member);
        existing.setPlan(plan);
        existing.setStartDate(today.minusDays(2));
        existing.setEndDate(today.plusDays(28));
        existing.setAutoRenew(false);
        existing.setStatus("ACTIVE");
        memberships.saveAndFlush(existing);

        PaymentResponse result = service.processAtCounter(oldPending.getId(), counter(true), jwt(receptionist));

        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        Payment persisted = payments.findById(oldPending.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, persisted.getStatus());
        assertNotNull(persisted.getPaidAt());
    }

    @Test
    void renewalSchedulerCreatesOneMethodNeutralPaymentInsideThreeDayWindow() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(3));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);

        assertEquals(1, renewalService.createDueRenewalRequests());
        assertEquals(0, renewalService.createDueRenewalRequests());
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        assertEquals(PaymentPurpose.MEMBERSHIP, renewal.getPurpose());
        assertEquals(PaymentStatus.PENDING, renewal.getStatus());
        assertNull(renewal.getMethod());
        assertEquals(source.getMember().getId(), renewal.getMember().getId());
        assertEquals(source.getPlan().getId(), renewal.getReferenceId());
        assertEquals(1, renewal.getPurchasedMonths());
        assertEquals(plan.getMonthlyPrice(), renewal.getMonthlyPriceSnapshot());
        assertEquals(plan.getMonthlyPrice(), renewal.getAmount());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void renewalCheckoutIssuesPersistedVnpayAttemptAndRejectsLiveDuplicate() throws Exception {
        enableRenewalCheckoutForTest();
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, false);
        LocalDateTime issuedAt = now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        mvc.perform(post("/api/payments/{id}/renewal-checkout", fixture.payment().getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(fixture.payment().getId()))
                .andExpect(jsonPath("$.checkoutAttemptId").isNumber())
                .andExpect(jsonPath("$.attemptReference").value(org.hamcrest.Matchers.startsWith("RNL")))
                .andExpect(jsonPath("$.amount").value(300000.0))
                .andExpect(jsonPath("$.checkoutUrl").isNotEmpty());

        List<PaymentCheckoutAttempt> attempts = checkoutAttempts
                .findByPayment_IdOrderByCreatedAtAscIdAsc(fixture.payment().getId());
        assertEquals(1, attempts.size());
        PaymentCheckoutAttempt attempt = attempts.get(0);
        assertEquals(CheckoutProvider.VNPAY, attempt.getProvider());
        assertEquals(CheckoutAttemptStatus.URL_ISSUED, attempt.getStatus());
        assertEquals(fixture.payment().getAmount(), attempt.getAmount());
        assertEquals(issuedAt, attempt.getCreatedAt());
        assertEquals(issuedAt.plusMinutes(15), attempt.getExpiresAt());

        assertThrows(ResponseStatusException.class,
                () -> service.createRenewalCheckout(fixture.payment().getId(), jwt(member), "127.0.0.1"));
        assertEquals(1, checkoutAttempts.findByPayment_IdOrderByCreatedAtAscIdAsc(fixture.payment().getId()).size());
    }

    @Test
    void renewalCheckoutRequiresOwnerAndEligibleOnlinePendingPayment() {
        enableRenewalCheckoutForTest();
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, false);
        assertThrows(ResponseStatusException.class,
                () -> service.createRenewalCheckout(fixture.payment().getId(), jwt(otherMember), "127.0.0.1"));

        fixture.payment().setStatus(PaymentStatus.CANCELLED);
        payments.saveAndFlush(fixture.payment());
        assertThrows(ResponseStatusException.class,
                () -> service.createRenewalCheckout(fixture.payment().getId(), jwt(member), "127.0.0.1"));
        assertTrue(checkoutAttempts.findByPayment_IdOrderByCreatedAtAscIdAsc(fixture.payment().getId()).isEmpty());
    }

    @Test
    void expiredRenewalCheckoutCanBeReplacedWithoutDeletingPriorAttempt() {
        enableRenewalCheckoutForTest();
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, false);
        PaymentCheckoutAttempt oldAttempt = checkoutAttempts.saveAndFlush(
                checkoutAttempt(fixture.payment(), now().minusMinutes(16), "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        oldAttempt.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        checkoutAttempts.saveAndFlush(oldAttempt);

        service.createRenewalCheckout(fixture.payment().getId(), jwt(member), "127.0.0.1");
        List<PaymentCheckoutAttempt> attempts = checkoutAttempts
                .findByPayment_IdOrderByCreatedAtAscIdAsc(fixture.payment().getId());
        assertEquals(2, attempts.size());
        assertEquals(CheckoutAttemptStatus.URL_ISSUED, attempts.get(0).getStatus());
        assertEquals(CheckoutAttemptStatus.URL_ISSUED, attempts.get(1).getStatus());
        assertTrue(attempts.get(1).getCreatedAt().isAfter(attempts.get(0).getCreatedAt()));
    }

    @Test
    void concurrentRenewalCheckoutRequestsCreateAtMostOneLiveAttempt() throws Exception {
        enableRenewalCheckoutForTest();
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> issueCheckoutAfter(start, fixture.payment().getId()));
            Future<Boolean> second = pool.submit(() -> issueCheckoutAfter(start, fixture.payment().getId()));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        List<PaymentCheckoutAttempt> attempts = checkoutAttempts
                .findByPayment_IdOrderByCreatedAtAscIdAsc(fixture.payment().getId());
        assertEquals(1, attempts.size());
    }

    private void enableRenewalCheckoutForTest() {
        doReturn(true).when(paymentGateway).supportsRenewalCheckout();
        doReturn(15).when(paymentGateway).renewalCheckoutExpiryMinutes();
    }

    private boolean issueCheckoutAfter(CountDownLatch start, Integer paymentId) throws Exception {
        start.await();
        try {
            service.createRenewalCheckout(paymentId, jwt(member), "127.0.0.1");
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        }
    }

    @Test
    void oneRenewalPaymentCanRetainMultipleDistinctCheckoutAttempts() {
        // H2 has no SQL Server-style filtered indexes. This test-only computed key
        // models their exact uniqueness rule: NULL links get distinct attempt keys,
        // while a non-NULL Membership can be linked only once.
        jdbc.execute("alter table payment_checkout_attempt add applied_membership_unique_key int "
                + "generated always as (case when applied_membership_id is null then -id "
                + "else applied_membership_id end)");
        jdbc.execute("alter table payment_checkout_attempt add winning_payment_unique_key int "
                + "generated always as (case when applied_membership_id is null then -id "
                + "else payment_id end)");
        jdbc.execute("create unique index ux_test_attempt_applied_membership "
                + "on payment_checkout_attempt(applied_membership_unique_key)");
        jdbc.execute("create unique index ux_test_attempt_winning_payment "
                + "on payment_checkout_attempt(winning_payment_unique_key)");

        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();

        LocalDateTime createdAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        PaymentCheckoutAttempt first = checkoutAttempt(renewal, createdAt, "RNL" + java.util.UUID.randomUUID().toString().replace("-", ""));
        PaymentCheckoutAttempt second = checkoutAttempt(renewal, createdAt.plusSeconds(1), "RNL" + java.util.UUID.randomUUID().toString().replace("-", ""));
        checkoutAttempts.saveAndFlush(first);
        checkoutAttempts.saveAndFlush(second);

        // Multiple non-winning attempts may leave the applied Membership link NULL.
        first.setAppliedMembership(source);
        checkoutAttempts.saveAndFlush(first);

        List<PaymentCheckoutAttempt> stored = checkoutAttempts.findByPayment_IdOrderByCreatedAtAscIdAsc(renewal.getId());
        assertEquals(2, stored.size());
        assertTrue(!stored.get(0).getTransactionReference().equals(stored.get(1).getTransactionReference()));
        assertEquals(renewal.getId(), stored.get(0).getPayment().getId());
        assertEquals(source.getId(), stored.get(0).getAppliedMembership().getId());
        assertNull(stored.get(1).getAppliedMembership());
        assertEquals(renewal.getAmount(), stored.get(0).getAmount());
        assertEquals(CheckoutProvider.VNPAY, stored.get(0).getProvider());
        assertEquals(CheckoutAttemptStatus.URL_ISSUED, stored.get(0).getStatus());
        PaymentCheckoutAttempt duplicateReference = checkoutAttempt(renewal, createdAt.plusSeconds(2),
                stored.get(0).getTransactionReference());
        assertThrows(DataIntegrityViolationException.class, () -> checkoutAttempts.saveAndFlush(duplicateReference));
        PaymentCheckoutAttempt duplicateAppliedMembership = checkoutAttempt(renewal, createdAt.plusSeconds(3),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", ""));
        duplicateAppliedMembership.setAppliedMembership(source);
        assertThrows(DataIntegrityViolationException.class,
                () -> checkoutAttempts.saveAndFlush(duplicateAppliedMembership));
        Membership unrelatedMembership = new Membership();
        unrelatedMembership.setMember(member);
        unrelatedMembership.setPlan(plan);
        unrelatedMembership.setAutoRenew(false);
        unrelatedMembership.setStartDate(today.plusMonths(3));
        unrelatedMembership.setEndDate(today.plusMonths(4));
        unrelatedMembership.setStatus("ACTIVE");
        unrelatedMembership = memberships.saveAndFlush(unrelatedMembership);
        PaymentCheckoutAttempt duplicateWinningAttempt = checkoutAttempt(renewal, createdAt.plusSeconds(4),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", ""));
        duplicateWinningAttempt.setAppliedMembership(unrelatedMembership);
        Membership persistedUnrelatedMembership = unrelatedMembership;
        assertThrows(DataIntegrityViolationException.class,
                () -> checkoutAttempts.saveAndFlush(duplicateWinningAttempt));
        assertEquals(2, checkoutAttempts.findByPayment_IdOrderByCreatedAtAscIdAsc(renewal.getId()).size());
    }

    @Test
    void overlappingRenewalSchedulerRunsCreateOnlyOneAttempt() throws Exception {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(3));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return renewalService.createDueRenewalRequests();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                return renewalService.createDueRenewalRequests();
            });
            start.countDown();
            int created = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, created);
            assertEquals(1, payments.findAll().stream()
                    .filter(p -> p.getRenewalMembership() != null
                            && p.getRenewalMembership().getId().equals(source.getId())).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void renewalSchedulerDoesNotCreateBeforeWindowOrAfterExpiry() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(4));
        source.setAutoRenew(false);
        memberships.saveAndFlush(source);
        assertEquals(0, renewalService.createDueRenewalRequests());
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        assertEquals(0, renewalService.createDueRenewalRequests());

        source.setEndDate(today);
        memberships.saveAndFlush(source);
        assertEquals(0, renewalService.createDueRenewalRequests());
    }

    @Test
    void enablingAutoRenewInsideWindowCreatesRequestButDoesNotGrantTime() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(false);
        memberships.saveAndFlush(source);

        membershipService.enableAutoRenew(source.getId(), jwt(member));
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, renewal.getStatus());
        assertNull(renewal.getMethod());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(today.plusDays(2), memberships.findById(source.getId()).orElseThrow().getEndDate());
    }

    @Test
    void nullMethodIsAllowedOnlyForPendingOrCancelledRenewalPayments() {
        Payment ordinary = methodNeutralPayment(null, PaymentStatus.PENDING, null);
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(ordinary));

        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);

        Payment success = methodNeutralPayment(source, PaymentStatus.SUCCESS, null);
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(success));
        Payment refundRequired = methodNeutralPayment(source, PaymentStatus.REFUND_REQUIRED, null);
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(refundRequired));

        Payment cancelled = methodNeutralPayment(source, PaymentStatus.CANCELLED, null);
        assertEquals(PaymentStatus.CANCELLED, payments.saveAndFlush(cancelled).getStatus());
    }

    @Test
    void futurePaidMembershipPeriodPreventsEarlierPeriodRenewal() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership current = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        current.setStartDate(today.minusMonths(1));
        current.setEndDate(today.plusDays(2));
        current.setAutoRenew(true);
        memberships.saveAndFlush(current);

        Payment queuedPayment = new Payment();
        queuedPayment.setMember(member);
        queuedPayment.setAmount(plan.getMonthlyPrice());
        queuedPayment.setMethod(PaymentMethod.COUNTER);
        queuedPayment.setPurpose(PaymentPurpose.MEMBERSHIP);
        queuedPayment.setReferenceId(plan.getId());
        queuedPayment.setPurchasedMonths(1);
        queuedPayment.setMonthlyPriceSnapshot(plan.getMonthlyPrice());
        queuedPayment.setStatus(PaymentStatus.SUCCESS);
        queuedPayment.setTransactionCode("TXN-FUTURE-" + System.nanoTime());
        queuedPayment.setPaidAt(now());
        queuedPayment = payments.saveAndFlush(queuedPayment);
        Membership future = new Membership();
        future.setMember(member);
        future.setPlan(plan);
        future.setAutoRenew(false);
        future.setStartDate(current.getEndDate());
        future.setEndDate(current.getEndDate().plusMonths(1));
        future.setStatus("ACTIVE");
        future.setPurchasedMonths(1);
        future.setPaidAmount(plan.getMonthlyPrice());
        future.setSourcePayment(queuedPayment);
        memberships.saveAndFlush(future);

        assertEquals(0, renewalService.createDueRenewalRequests());
        assertTrue(payments.findByRenewalMembership_Id(current.getId()).isEmpty());
        assertEquals(current.getEndDate(), memberships.findById(current.getId()).orElseThrow().getEndDate());
        assertEquals(future.getStartDate(), memberships.findById(future.getId()).orElseThrow().getStartDate());
    }

    @Test
    void renewalMethodSelectionIsOwnerOnlyAndCounterSettlementFailsClosed() throws Exception {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();

        mvc.perform(patch("/api/payments/{id}/renewal-method", renewal.getId())
                        .header("Authorization", bearer(otherMember))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"COUNTER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/payments/{id}/renewal-method", renewal.getId())
                        .header("Authorization", bearer(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/payments/{id}/renewal-method", renewal.getId())
                        .header("Authorization", bearer(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"OFFLINE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/payments/{id}/renewal-method", renewal.getId())
                        .header("Authorization", bearer(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"COUNTER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("COUNTER"));
        assertThrows(ResponseStatusException.class,
                () -> service.processAtCounter(renewal.getId(), counter(true), jwt(receptionist)));
        assertEquals(PaymentStatus.PENDING, payments.findById(renewal.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void disablingRenewalCancelsPendingAttemptAndExpiryIsIdempotent() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        Integer sourceId = source.getId();
        assertEquals(PaymentStatus.PENDING, renewal.getStatus());
        assertNull(renewal.getMethod());

        membershipService.cancelAutoRenew(source.getId(), jwt(member));
        assertEquals("CANCELLED", payments.findById(renewal.getId()).orElseThrow().getStatus().name());
        assertNull(payments.findById(renewal.getId()).orElseThrow().getMethod());
        membershipService.cancelAutoRenew(source.getId(), jwt(member));
        assertEquals(PaymentStatus.CANCELLED, payments.findById(renewal.getId()).orElseThrow().getStatus());
        assertNull(payments.findById(renewal.getId()).orElseThrow().getMethod());
        assertEquals(0, renewalService.createDueRenewalRequests());
        assertThrows(ResponseStatusException.class, () -> membershipService.enableAutoRenew(sourceId, jwt(member)));

        source = memberships.findById(source.getId()).orElseThrow();
        source.setAutoRenew(false);
        source.setEndDate(today);
        memberships.saveAndFlush(source);
        assertEquals(1, membershipService.expireMemberships());
        assertEquals(0, membershipService.expireMemberships());
        assertEquals("EXPIRED", memberships.findById(source.getId()).orElseThrow().getStatus());
    }

    @Test
    void expirationCancelsPendingRenewalWithoutChangingPaidFuturePeriods() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(1));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();

        clock.setInstant(today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        assertEquals(1, membershipService.expireMemberships());
        assertEquals(PaymentStatus.CANCELLED, payments.findById(renewal.getId()).orElseThrow().getStatus());
        assertNull(payments.findById(renewal.getId()).orElseThrow().getMethod());
        assertEquals("EXPIRED", memberships.findById(source.getId()).orElseThrow().getStatus());
        assertEquals(0, membershipService.expireMemberships());
    }

    @Test
    void expiredRenewalRequestRejectsMethodSelection() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(1));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        SelectRenewalPaymentMethodRequest method = new SelectRenewalPaymentMethodRequest();
        method.setMethod(PaymentMethod.ONLINE);

        clock.setInstant(today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        assertThrows(ResponseStatusException.class,
                () -> service.selectRenewalPaymentMethod(renewal.getId(), method, jwt(member)));
        assertEquals(PaymentStatus.PENDING, payments.findById(renewal.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void memberCanExplicitlyChooseRenewedMembershipAutoRenewTrueThroughApi() throws Exception {
        RenewalFixture fixture = renewalFixture(null, null, false);
        mvc.perform(patch("/api/payments/{id}/renewal-method", fixture.payment().getId())
                        .header("Authorization", bearer(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"ONLINE\",\"renewalAutoRenewChoice\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("ONLINE"));

        Payment saved = payments.findById(fixture.payment().getId()).orElseThrow();
        assertEquals(Boolean.TRUE, saved.getRenewalAutoRenewChoice());
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
    }

    @Test
    void explicitFalseAndOmittedChoiceRemainDistinct() {
        enableRenewalCheckoutForTest();
        RenewalFixture falseFixture = renewalFixture(null, null, false);
        SelectRenewalPaymentMethodRequest falseChoice = renewalMethod(PaymentMethod.COUNTER, false);
        service.selectRenewalPaymentMethod(falseFixture.payment().getId(), falseChoice, jwt(member));
        assertEquals(Boolean.FALSE, payments.findById(falseFixture.payment().getId()).orElseThrow()
                .getRenewalAutoRenewChoice());

        User missingOwner = user("MEMBER", "renew-missing-choice");
        RenewalFixture missingFixture = renewalFixture(missingOwner, null, null, false);
        SelectRenewalPaymentMethodRequest missingChoice = renewalMethod(PaymentMethod.ONLINE, null);
        service.selectRenewalPaymentMethod(missingFixture.payment().getId(), missingChoice, jwt(missingOwner));
        assertNull(payments.findById(missingFixture.payment().getId()).orElseThrow().getRenewalAutoRenewChoice());
        assertThrows(ResponseStatusException.class, () -> service.createRenewalCheckout(
                missingFixture.payment().getId(), jwt(missingOwner), "127.0.0.1"));
    }

    @Test
    void onlyOwningMemberCanSetRenewalAutoRenewChoice() {
        RenewalFixture fixture = renewalFixture(null, null, false);
        assertThrows(ResponseStatusException.class, () -> service.selectRenewalPaymentMethod(
                fixture.payment().getId(), renewalMethod(PaymentMethod.ONLINE, true), jwt(otherMember)));
        assertNull(payments.findById(fixture.payment().getId()).orElseThrow().getRenewalAutoRenewChoice());
    }

    @Test
    void invalidRenewalStateAndExistingAttemptPreventChoiceChanges() {
        RenewalFixture invalidFixture = renewalFixture(null, null, false);
        Payment cancelled = payments.findById(invalidFixture.payment().getId()).orElseThrow();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        payments.saveAndFlush(cancelled);
        assertThrows(ResponseStatusException.class, () -> service.selectRenewalPaymentMethod(
                cancelled.getId(), renewalMethod(PaymentMethod.COUNTER, false), jwt(member)));
        assertNull(payments.findById(cancelled.getId()).orElseThrow().getRenewalAutoRenewChoice());

        User attemptedOwner = user("MEMBER", "renew-attempted-choice");
        RenewalFixture attemptedFixture = renewalFixture(attemptedOwner, null, null, false);
        checkoutAttempts.saveAndFlush(checkoutAttempt(attemptedFixture.payment(), now(),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        assertThrows(ResponseStatusException.class, () -> service.selectRenewalPaymentMethod(
                attemptedFixture.payment().getId(), renewalMethod(PaymentMethod.ONLINE, true), jwt(attemptedOwner)));
        assertNull(payments.findById(attemptedFixture.payment().getId()).orElseThrow().getRenewalAutoRenewChoice());
    }

    @Test
    void counterRenewalCanCarryExplicitFalseChoiceIntoSettlement() {
        RenewalFixture fixture = renewalFixture(null, null, false);
        service.selectRenewalPaymentMethod(fixture.payment().getId(),
                renewalMethod(PaymentMethod.COUNTER, false), jwt(member));

        RenewalSettlementResult settled = renewalSettlementService.settleConfirmedCounterReceipt(
                fixture.payment().getId(), receptionist.getId());

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, settled.outcome());
        Membership next = memberships.findById(settled.membershipId()).orElseThrow();
        assertEquals(Boolean.FALSE, next.getAutoRenew());
    }

    private SelectRenewalPaymentMethodRequest renewalMethod(PaymentMethod method, Boolean autoRenewChoice) {
        SelectRenewalPaymentMethodRequest request = new SelectRenewalPaymentMethodRequest();
        request.setMethod(method);
        request.setRenewalAutoRenewChoice(autoRenewChoice);
        return request;
    }

    @Test
    void successfulRenewalIpnSettlesOnceAndDuplicateIsIdempotent() throws Exception {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        GatewayCallback callback = renewalGatewayCallback(fixture, true, fixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(callback);

        mvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", callback.rawTransactionReference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", callback.rawTransactionReference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        PaymentCheckoutAttempt attempt = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals("VNPAY-TXN-1", attempt.getProviderTransactionId());
        assertEquals("00/00", attempt.getProviderResponseCode());
        assertEquals(1, memberships.findByMemberId(member.getId()).size() - 1);
    }

    @Test
    void failedRenewalIpnRecordsAttemptEvidenceWithoutSettlingParentPayment() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        GatewayCallback callback = renewalGatewayCallback(fixture, false, fixture.payment().getAmount(), "24", "02");
        stubRenewalCallback(callback);

        OnlineCallbackResult result = service.processVnpayIpn(Map.of("vnp_TxnRef", callback.rawTransactionReference()));

        assertEquals(OnlineCallbackResult.Status.APPLIED, result.status());
        PaymentCheckoutAttempt attempt = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(CheckoutAttemptStatus.FAILED, attempt.getStatus());
        assertEquals("VNPAY-TXN-1", attempt.getProviderTransactionId());
        assertEquals("24/02", attempt.getProviderResponseCode());
        assertNull(attempt.getVerifiedAt());
        assertEquals(PaymentStatus.PENDING, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());

        GatewayCallback contradictoryCapture = renewalGatewayCallback(fixture, true,
                fixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(contradictoryCapture);
        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,
                service.processVnpayIpn(Map.of("vnp_TxnRef", contradictoryCapture.rawTransactionReference())).status());
        PaymentCheckoutAttempt reconciled = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED, reconciled.getStatus());
        assertEquals("VNPAY-TXN-1", reconciled.getProviderTransactionId());
        assertNotNull(reconciled.getVerifiedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED,
                payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
    }

    @Test
    void renewalIpnRejectsInvalidSignatureUnknownAttemptAndAmountMismatch() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        String reference = fixture.attempt().getTransactionReference();
        doReturn(Optional.empty()).when(paymentGateway).verifyCallback(Map.of("vnp_TxnRef", reference));
        assertEquals(OnlineCallbackResult.Status.INVALID_SIGNATURE,
                service.processVnpayIpn(Map.of("vnp_TxnRef", reference)).status());

        GatewayCallback unknown = new GatewayCallback("RNL00000000000000000000000000000000", true,
                fixture.payment().getAmount(), null, "VNPAY-TXN-2", "00", "00",
                "RNL00000000000000000000000000000000");
        stubRenewalCallback(unknown);
        assertEquals(OnlineCallbackResult.Status.NOT_FOUND,
                service.processVnpayIpn(Map.of("vnp_TxnRef", unknown.rawTransactionReference())).status());

        GatewayCallback mismatch = renewalGatewayCallback(fixture, true,
                fixture.payment().getAmount().subtract(new BigDecimal("1.00")), "00", "00");
        stubRenewalCallback(mismatch);
        assertEquals(OnlineCallbackResult.Status.AMOUNT_MISMATCH,
                service.processVnpayIpn(Map.of("vnp_TxnRef", mismatch.rawTransactionReference())).status());
        PaymentCheckoutAttempt persisted = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(new BigDecimal("299999.00"), persisted.getProviderAmount());
        assertEquals("VNPAY-TXN-1", persisted.getProviderTransactionId());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void concurrentSuccessfulRenewalIpnsCreateOnlyOneMembership() throws Exception {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, false, true);
        GatewayCallback callback = renewalGatewayCallback(fixture, true, fixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(callback);
        Map<String, String> params = Map.of("vnp_TxnRef", callback.rawTransactionReference());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<OnlineCallbackResult> first = pool.submit(() -> { start.await(); return service.processVnpayIpn(params); });
            Future<OnlineCallbackResult> second = pool.submit(() -> { start.await(); return service.processVnpayIpn(params); });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void lateRenewalCaptureRequiresReconciliation() {
        RenewalFixture lateFixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        GatewayCallback lateCallback = renewalGatewayCallback(lateFixture, true,
                lateFixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(lateCallback);
        clock.setInstant(lateFixture.source().getEndDate().atStartOfDay(BUSINESS_ZONE).toInstant());
        OnlineCallbackResult late = service.processVnpayIpn(Map.of("vnp_TxnRef", lateCallback.rawTransactionReference()));
        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, late.status());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(lateFixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void captureFromCompetingRenewalAttemptRequiresReconciliation() {
        RenewalFixture competingFixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        PaymentCheckoutAttempt second = checkoutAttempts.saveAndFlush(checkoutAttempt(competingFixture.payment(), now().plusSeconds(1),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        GatewayCallback winnerCallback = renewalGatewayCallback(competingFixture, true,
                competingFixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(winnerCallback);
        assertEquals(OnlineCallbackResult.Status.APPLIED,
                service.processVnpayIpn(Map.of("vnp_TxnRef", winnerCallback.rawTransactionReference())).status());

        GatewayCallback secondCallback = new GatewayCallback(second.getTransactionReference(), true,
                competingFixture.payment().getAmount(), null, "VNPAY-TXN-2", "00", "00", second.getTransactionReference());
        stubRenewalCallback(secondCallback);
        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,
                service.processVnpayIpn(Map.of("vnp_TxnRef", second.getTransactionReference())).status());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(competingFixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED,
                checkoutAttempts.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void renewalBrowserReturnNeverSettlesEvenWithSuccessLookingParameters() throws Exception {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        String reference = fixture.attempt().getTransactionReference();
        GatewayCallback signed = renewalGatewayCallback(fixture, true, fixture.payment().getAmount(), "00", "00");
        doReturn(Optional.of(signed)).when(paymentGateway).verifyCallback(Map.of(
                "vnp_TxnRef", reference, "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00"));
        mvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_TxnRef", reference)
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00"))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("paymentStatus=PENDING"),
                                org.hamcrest.Matchers.containsString("paymentId=" + fixture.payment().getId()))));
        assertEquals(PaymentStatus.PENDING, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void ordinaryTxnBrowserReturnKeepsExistingSettlementBehavior() throws Exception {
        PaymentResponse payment = service.createPayment(
                request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.ONLINE), jwt(member));
        String url = payment.getPaymentUrl();
        String signature = java.net.URLDecoder.decode(
                url.substring(url.indexOf("signature=") + "signature=".length()), java.nio.charset.StandardCharsets.UTF_8);

        mvc.perform(get("/api/payments/vnpay/return")
                        .param("transactionCode", payment.getTransactionCode())
                        .param("success", "true")
                        .param("signature", signature))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("paymentId=" + payment.getId()),
                                org.hamcrest.Matchers.containsString("paymentStatus=SUCCESS"))));
        assertEquals(PaymentStatus.SUCCESS, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void renewalReadModelDistinguishesOrdinaryPaymentAndExposesExplicitChoiceAndLatestAttempt() throws Exception {
        PaymentResponse ordinary = service.createPayment(
                request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member));
        assertTrue(!ordinary.isRenewal());
        assertNull(ordinary.getRenewalSourceMembershipId());
        assertNull(ordinary.getRenewalAutoRenewChoice());
        assertNull(ordinary.getRenewalCheckoutAttempt());
        // Keep the ordinary purchase fixture from blocking renewalFixture's own
        // pending purchase for the same member/plan; production duplicate protection stays intact.
        service.cancelPayment(ordinary.getId(), jwt(member));

        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, false, false);
        User trueChoiceOwner = user("MEMBER", "renew-readmodel-second");
        RenewalFixture trueChoiceFixture = renewalFixture(trueChoiceOwner, PaymentMethod.COUNTER, true, false);
        PaymentResponse trueChoice = service.getMyHistory(jwt(trueChoiceOwner)).stream()
                .filter(row -> row.getId().equals(trueChoiceFixture.payment().getId())).findFirst().orElseThrow();
        assertTrue(trueChoice.isRenewal());
        assertEquals(true, trueChoice.getRenewalAutoRenewChoice());
        LocalDateTime createdAt = now();
        PaymentCheckoutAttempt earlier = checkoutAttempts.saveAndFlush(checkoutAttempt(fixture.payment(), createdAt,
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        earlier.setStatus(CheckoutAttemptStatus.FAILED);
        checkoutAttempts.saveAndFlush(earlier);
        PaymentCheckoutAttempt latest = checkoutAttempts.saveAndFlush(checkoutAttempt(fixture.payment(), createdAt,
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        latest.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        checkoutAttempts.saveAndFlush(latest);

        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + fixture.payment().getId() + ")].isRenewal").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + fixture.payment().getId() + ")].renewalSourceMembershipId").value(org.hamcrest.Matchers.contains(fixture.source().getId())))
                .andExpect(jsonPath("$[?(@.id == " + fixture.payment().getId() + ")].renewalAutoRenewChoice").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.id == " + fixture.payment().getId() + ")].renewalCheckoutAttempt.attemptId").value(org.hamcrest.Matchers.contains(latest.getId())))
                .andExpect(jsonPath("$[?(@.id == " + fixture.payment().getId() + ")].renewalCheckoutAttempt.status").value(org.hamcrest.Matchers.contains("URL_ISSUED")));
        assertNotNull(earlier.getId());
    }

    @Test
    void renewalReadModelRepresentsNoAttemptExpiredAttemptFailedAttemptAndReconciliation() {
        RenewalFixture noAttempt = renewalFixture(PaymentMethod.ONLINE, null, false);
        PaymentResponse noAttemptResponse = service.getMyHistory(jwt(member)).stream()
                .filter(row -> row.getId().equals(noAttempt.payment().getId())).findFirst().orElseThrow();
        assertTrue(noAttemptResponse.isRenewal());
        assertNull(noAttemptResponse.getRenewalAutoRenewChoice());
        assertNull(noAttemptResponse.getRenewalCheckoutAttempt());

        PaymentCheckoutAttempt expired = checkoutAttempts.saveAndFlush(checkoutAttempt(noAttempt.payment(), now().minusMinutes(16),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", "")));
        PaymentResponse expiredResponse = service.getMyHistory(jwt(member)).stream()
                .filter(row -> row.getId().equals(noAttempt.payment().getId())).findFirst().orElseThrow();
        assertEquals("URL_ISSUED", expiredResponse.getRenewalCheckoutAttempt().status());
        assertTrue(expiredResponse.getRenewalCheckoutAttempt().expiresAt().isBefore(now()));

        expired.setStatus(CheckoutAttemptStatus.FAILED);
        checkoutAttempts.saveAndFlush(expired);
        PaymentResponse failedResponse = service.getMyHistory(jwt(member)).stream()
                .filter(row -> row.getId().equals(noAttempt.payment().getId())).findFirst().orElseThrow();
        assertEquals("FAILED", failedResponse.getRenewalCheckoutAttempt().status());

        noAttempt.payment().setStatus(PaymentStatus.REFUND_REQUIRED);
        payments.saveAndFlush(noAttempt.payment());
        PaymentResponse reconciliation = service.getMyHistory(jwt(member)).stream()
                .filter(row -> row.getId().equals(noAttempt.payment().getId())).findFirst().orElseThrow();
        assertEquals("REFUND_REQUIRED", reconciliation.getStatus());
        assertEquals("FAILED", reconciliation.getRenewalCheckoutAttempt().status());
    }

    @Test
    void paymentHistoryIsOwnerScopedAndRenewalReturnLocatorRequiresVerifiedMatchingAttempt() throws Exception {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        String reference = fixture.attempt().getTransactionReference();
        GatewayCallback callback = renewalGatewayCallback(fixture, true, fixture.payment().getAmount(), "00", "00");
        stubRenewalCallback(callback);

        mvc.perform(get("/api/payments/me").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("[]"));
        assertThrows(ResponseStatusException.class,
                () -> service.getHistoryByMember(member.getId(), jwt(otherMember)));

        assertEquals(Optional.of(fixture.payment().getId()), service.resolveVerifiedRenewalReturnPaymentId(
                Map.of("vnp_TxnRef", reference)));
        doReturn(Optional.empty()).when(paymentGateway).verifyCallback(Map.of("vnp_TxnRef", reference));
        assertTrue(service.resolveVerifiedRenewalReturnPaymentId(Map.of("vnp_TxnRef", reference)).isEmpty());
        mvc.perform(get("/api/payments/vnpay/return").param("vnp_TxnRef", reference))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("paymentId="))));
        assertTrue(service.resolveVerifiedRenewalReturnPaymentId(Map.of("vnp_TxnRef", "RNLmalformed")).isEmpty());
        GatewayCallback mismatch = renewalGatewayCallback(fixture, true,
                fixture.payment().getAmount().add(new BigDecimal("1.00")), "00", "00");
        stubRenewalCallback(mismatch);
        assertTrue(service.resolveVerifiedRenewalReturnPaymentId(Map.of("vnp_TxnRef", reference)).isEmpty());

        String unknown = "RNL00000000000000000000000000000000";
        GatewayCallback unknownCallback = new GatewayCallback(unknown, true, fixture.payment().getAmount(), null,
                "VNPAY-UNKNOWN", "00", "00", unknown);
        stubRenewalCallback(unknownCallback);
        assertTrue(service.resolveVerifiedRenewalReturnPaymentId(Map.of("vnp_TxnRef", unknown)).isEmpty());
        assertEquals(PaymentStatus.PENDING, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    private GatewayCallback renewalGatewayCallback(RenewalFixture fixture, boolean success, BigDecimal amount,
            String responseCode, String transactionStatus) {
        String reference = fixture.attempt().getTransactionReference();
        return new GatewayCallback(reference, success, amount, null, "VNPAY-TXN-1", responseCode,
                transactionStatus, reference);
    }

    private void stubRenewalCallback(GatewayCallback callback) {
        doReturn(Optional.of(callback)).when(paymentGateway)
                .verifyCallback(Map.of("vnp_TxnRef", callback.rawTransactionReference()));
    }

    @Test
    void verifiedRenewalCallbackFailsClosedIntoRefundRequired() {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(member));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(member.getId()).get(0);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        renewalService.createDueRenewalRequests();
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        SelectRenewalPaymentMethodRequest method = new SelectRenewalPaymentMethodRequest();
        method.setMethod(PaymentMethod.ONLINE);
        service.selectRenewalPaymentMethod(renewal.getId(), method, jwt(member));

        String signedUrl = paymentGateway.createPaymentUrl(renewal.getTransactionCode(), renewal.getAmount(), "127.0.0.1");
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        java.util.Map<String, String> params = java.util.Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        OnlineCallbackResult result = service.processOnlineCallback(params);

        assertEquals(OnlineCallbackResult.Status.APPLIED, result.status());
        assertEquals("REFUND_REQUIRED", result.payment().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(renewal.getId()).orElseThrow().getStatus());
    }

    @Test
    void timelyRenewalCaptureCreatesExactNextPeriodWithExplicitAutoRenewChoice() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        LocalDateTime observed = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-1",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, result.outcome());
        Membership next = memberships.findById(result.membershipId()).orElseThrow();
        assertEquals(fixture.source().getEndDate(), next.getStartDate());
        assertEquals(fixture.source().getEndDate().plusMonths(1), next.getEndDate());
        assertEquals(fixture.source().getPlan().getId(), next.getPlan().getId());
        assertEquals(fixture.payment().getMonthlyPriceSnapshot(), next.getPaidAmount());
        assertEquals(1, next.getPurchasedMonths());
        assertTrue(next.getAutoRenew());
        assertEquals(fixture.payment().getId(), next.getSourcePayment().getId());
        Payment appliedPayment = payments.findById(fixture.payment().getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, appliedPayment.getStatus());
        assertEquals(observed, appliedPayment.getPaidAt());
        assertEquals(next.getId(), checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow()
                .getAppliedMembership().getId());
    }

    @Test
    void falseNextPeriodAutoRenewChoiceIsPreservedAndWinningCallbackReplayIsIdempotent() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, false, true);
        VerifiedRenewalCapture capture = new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(),
                "provider-replay", fixture.payment().getAmount(), "00");

        RenewalSettlementResult first = renewalSettlementService.settleVerifiedOnlineCapture(capture);
        RenewalSettlementResult replay = renewalSettlementService.settleVerifiedOnlineCapture(capture);

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, first.outcome());
        assertEquals(RenewalSettlementResult.Outcome.ALREADY_APPLIED, replay.outcome());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
        assertEquals(false, memberships.findById(first.membershipId()).orElseThrow().getAutoRenew());
    }

    @Test
    void winningAttemptFlaggedForReconciliationStillReturnsAppliedMembershipOnExactReplay() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, false, true);
        VerifiedRenewalCapture capture = new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(),
                "provider-flagged-replay", fixture.payment().getAmount(), "00");
        RenewalSettlementResult first = renewalSettlementService.settleVerifiedOnlineCapture(capture);

        PaymentCheckoutAttempt winning = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        winning.setStatus(CheckoutAttemptStatus.RECONCILIATION_REQUIRED);
        winning.setReconciliationReason("Manual investigation flag retained for replay regression");
        checkoutAttempts.saveAndFlush(winning);

        RenewalSettlementResult replay = renewalSettlementService.settleVerifiedOnlineCapture(capture);

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, first.outcome());
        assertEquals(RenewalSettlementResult.Outcome.ALREADY_APPLIED, replay.outcome());
        assertEquals(first.membershipId(), replay.membershipId());
        PaymentCheckoutAttempt persisted = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED, persisted.getStatus());
        assertEquals("Manual investigation flag retained for replay regression", persisted.getReconciliationReason());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void conflictingVerifiedEvidenceFlagsWinningAttemptWithoutReplacingEvidenceOrDowngradingPayment() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        VerifiedRenewalCapture original = new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(),
                "provider-original", fixture.payment().getAmount(), "00");
        RenewalSettlementResult first = renewalSettlementService.settleVerifiedOnlineCapture(original);

        RenewalSettlementResult conflict = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-conflict",
                        fixture.payment().getAmount().subtract(new BigDecimal("1.00")), "00"));

        PaymentCheckoutAttempt persisted = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(RenewalSettlementResult.Outcome.APPLIED, first.outcome());
        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, conflict.outcome());
        assertEquals(first.membershipId(), conflict.membershipId());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED, persisted.getStatus());
        assertEquals("provider-original", persisted.getProviderTransactionId());
        assertEquals(fixture.payment().getAmount(), persisted.getProviderAmount());
        assertEquals("Repeated verified callback reported conflicting provider evidence",
                persisted.getReconciliationReason());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void exactExclusiveEndDateBoundaryRequiresReconciliation() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        clock.setInstant(fixture.source().getEndDate().atStartOfDay(BUSINESS_ZONE).toInstant());

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-late",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED,
                checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void providerAmountMismatchIsDurablyRecordedWithoutMembershipActivation() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-mismatch",
                        fixture.payment().getAmount().subtract(new BigDecimal("1.00")), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        PaymentCheckoutAttempt recorded = checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(new BigDecimal("299999.00"), recorded.getProviderAmount());
        assertEquals("provider-mismatch", recorded.getProviderTransactionId());
        assertEquals("00", recorded.getProviderResponseCode());
        assertTrue(recorded.getVerifiedAt() != null);
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void secondCapturedAttemptIsReconciledWithoutDowngradingWinningPayment() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        PaymentCheckoutAttempt secondAttempt = checkoutAttempt(fixture.payment(), now().plusSeconds(1),
                "RNL" + java.util.UUID.randomUUID().toString().replace("-", ""));
        secondAttempt = checkoutAttempts.saveAndFlush(secondAttempt);

        RenewalSettlementResult winner = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-win",
                        fixture.payment().getAmount(), "00"));
        RenewalSettlementResult duplicateCapture = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(secondAttempt.getTransactionReference(), "provider-second",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, winner.outcome());
        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, duplicateCapture.outcome());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED,
                checkoutAttempts.findById(secondAttempt.getId()).orElseThrow().getStatus());
        assertEquals(winner.membershipId(), checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow()
                .getAppliedMembership().getId());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void cancelledRenewalCaptureIsReconciledAndCannotCreateMembership() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        Payment cancelled = payments.findById(fixture.payment().getId()).orElseThrow();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        cancelled.setNote("Cancelled before provider capture was reported");
        payments.saveAndFlush(cancelled);

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-after-cancel",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals("provider-after-cancel", checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow()
                .getProviderTransactionId());
    }

    @Test
    void missingExplicitNextPeriodChoiceRequiresReconciliation() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, null, true);

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-no-choice",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void verifiedOnlineCaptureBeforeMethodSelectionRemainsRecordedWithoutViolatingNullMethodCheck() {
        RenewalFixture fixture = renewalFixture(null, true, true);

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-neutral",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        Payment persisted = payments.findById(fixture.payment().getId()).orElseThrow();
        assertEquals(PaymentStatus.CANCELLED, persisted.getStatus());
        assertNull(persisted.getMethod());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED,
                checkoutAttempts.findById(fixture.attempt().getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void confirmedCounterReceiptUsesServerTimeAndSameRenewalEngine() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.COUNTER, false, false);
        LocalDateTime receivedAt = now();

        RenewalSettlementResult result = renewalSettlementService.settleConfirmedCounterReceipt(
                fixture.payment().getId(), receptionist.getId());
        RenewalSettlementResult replay = renewalSettlementService.settleConfirmedCounterReceipt(
                fixture.payment().getId(), receptionist.getId());

        assertEquals(RenewalSettlementResult.Outcome.APPLIED, result.outcome());
        assertEquals(RenewalSettlementResult.Outcome.ALREADY_APPLIED, replay.outcome());
        Membership next = memberships.findById(result.membershipId()).orElseThrow();
        assertEquals(fixture.source().getEndDate(), next.getStartDate());
        assertEquals(fixture.source().getEndDate().plusMonths(1), next.getEndDate());
        assertEquals(false, next.getAutoRenew());
        assertEquals(receivedAt, payments.findById(fixture.payment().getId()).orElseThrow().getPaidAt());
    }

    @Test
    void counterReceiptForCancelledRenewalIsRecordedButNeverApplied() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.COUNTER, true, false);
        Payment cancelled = payments.findById(fixture.payment().getId()).orElseThrow();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        payments.saveAndFlush(cancelled);

        RenewalSettlementResult result = renewalSettlementService.settleConfirmedCounterReceipt(
                fixture.payment().getId(), receptionist.getId());

        Payment reconciled = payments.findById(fixture.payment().getId()).orElseThrow();
        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, reconciled.getStatus());
        assertTrue(reconciled.getPaidAt() != null);
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void overlappingMembershipTimelineRequiresReconciliation() {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        Membership conflicting = new Membership();
        conflicting.setMember(member);
        conflicting.setPlan(plan);
        conflicting.setAutoRenew(false);
        conflicting.setStartDate(fixture.source().getEndDate().plusDays(5));
        conflicting.setEndDate(fixture.source().getEndDate().plusMonths(1).plusDays(5));
        conflicting.setStatus("ACTIVE");
        memberships.saveAndFlush(conflicting);

        RenewalSettlementResult result = renewalSettlementService.settleVerifiedOnlineCapture(
                new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(), "provider-overlap",
                        fixture.payment().getAmount(), "00"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(fixture.payment().getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentRenewalSettlementCreatesOnlyOnePeriod() throws Exception {
        RenewalFixture fixture = renewalFixture(PaymentMethod.ONLINE, true, true);
        VerifiedRenewalCapture capture = new VerifiedRenewalCapture(fixture.attempt().getTransactionReference(),
                "provider-concurrent", fixture.payment().getAmount(), "00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RenewalSettlementResult> first = pool.submit(() -> { start.await(); return renewalSettlementService.settleVerifiedOnlineCapture(capture); });
            Future<RenewalSettlementResult> second = pool.submit(() -> { start.await(); return renewalSettlementService.settleVerifiedOnlineCapture(capture); });
            start.countDown();
            List<RenewalSettlementResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            List<RenewalSettlementResult.Outcome> outcomes = results.stream().map(RenewalSettlementResult::outcome).toList();
            assertTrue(outcomes.contains(RenewalSettlementResult.Outcome.APPLIED), results.toString());
            assertTrue(outcomes.contains(RenewalSettlementResult.Outcome.ALREADY_APPLIED), results.toString());
            assertEquals(2, memberships.findByMemberId(member.getId()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void newMembershipPaymentDefaultsToOneMonthAndUsesServerPriceSnapshot() {
        CreatePaymentRequest request = request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER);
        request.setPurchasedMonths(null);
        PaymentResponse payment = service.createPayment(request, jwt(member));

        assertEquals(new BigDecimal("300000.00"), payment.getAmount());
        assertEquals(1, payment.getPurchasedMonths());
        Payment persisted = payments.findById(payment.getId()).orElseThrow();
        assertEquals(1, persisted.getPurchasedMonths());
        assertEquals(new BigDecimal("300000.00"), persisted.getMonthlyPriceSnapshot());
        assertNull(persisted.getRenewalMembership());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());

        service.processAtCounter(payment.getId(), counter(true), jwt(receptionist));
        Membership created = memberships.findByMemberId(member.getId()).stream()
                .filter(value -> value.getSourcePayment().getId().equals(payment.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, created.getPurchasedMonths());
        assertEquals(new BigDecimal("300000.00"), created.getPaidAmount());
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        assertEquals(today, created.getStartDate());
        assertEquals(today.plusMonths(1), created.getEndDate());
    }

    @Test
    void ordinaryPurchaseCannotBeUsedToAppendToAnExistingMembershipAndLeavesItUnchanged() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Membership historical = new Membership();
        historical.setMember(member);
        historical.setPlan(plan);
        historical.setStartDate(today.minusMonths(3));
        historical.setEndDate(today.plusMonths(3));
        historical.setAutoRenew(false);
        historical.setStatus("ACTIVE");
        historical.setPurchasedMonths(6);
        historical.setPaidAmount(new BigDecimal("1800000.00"));
        historical = memberships.saveAndFlush(historical);
        Integer historicalId = historical.getId();

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member)));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        Membership unchanged = memberships.findById(historicalId).orElseThrow();
        assertEquals(today.minusMonths(3), unchanged.getStartDate());
        assertEquals(today.plusMonths(3), unchanged.getEndDate());
        assertEquals(6, unchanged.getPurchasedMonths());
        assertEquals(new BigDecimal("1800000.00"), unchanged.getPaidAmount());
        assertNull(unchanged.getSourcePayment());
        assertTrue(payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).isEmpty());
    }

    @Test
    void unsupportedMembershipDurationIsRejectedBeforePaymentCreation() {
        for (int months : List.of(3, 6, 12)) {
            CreatePaymentRequest unsupported = request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER);
            unsupported.setPurchasedMonths(months);
            assertThrows(IllegalArgumentException.class, () -> service.createPayment(unsupported, jwt(member)));
        }
        assertTrue(payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).isEmpty());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void unsuccessfulMembershipCounterPaymentDoesNotActivateMembership() {
        PaymentResponse payment = service.createPayment(
                request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member));

        PaymentResponse cancelled = service.processAtCounter(payment.getId(), counter(false), jwt(receptionist));

        assertEquals("CANCELLED", cancelled.getStatus());
        assertTrue(memberships.findByMemberId(member.getId()).isEmpty());
    }

    @Test
    void legacyPendingMembershipPaymentKeepsItsRecordedAmountAndLegacyTerm() {
        plan.setDurationMonths(2);
        plan.setPrice(new BigDecimal("500000.00"));
        plans.saveAndFlush(plan);
        Payment legacy = new Payment();
        legacy.setMember(member);
        legacy.setAmount(new BigDecimal("500000.00"));
        legacy.setMethod(PaymentMethod.COUNTER);
        legacy.setPurpose(PaymentPurpose.MEMBERSHIP);
        legacy.setReferenceId(plan.getId());
        legacy.setStatus(PaymentStatus.PENDING);
        legacy.setTransactionCode("LEGACY-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        legacy = payments.saveAndFlush(legacy);

        PaymentResponse settled = service.processAtCounter(legacy.getId(), counter(true), jwt(receptionist));

        Membership created = memberships.findByMemberId(member.getId()).get(0);
        assertEquals("SUCCESS", settled.getStatus());
        assertEquals(0, new BigDecimal("500000.00").compareTo(created.getPaidAmount()));
        assertNull(created.getPurchasedMonths());
        assertEquals(created.getStartDate().plusMonths(2), created.getEndDate());
        assertEquals(legacy.getId(), created.getSourcePayment().getId());
    }

    @Test
    void existingPendingMultiMonthSnapshotIsNotReinterpretedAsNewOneMonthPurchase() {
        Payment pending = new Payment();
        pending.setMember(member);
        pending.setAmount(new BigDecimal("1500000.00"));
        pending.setMethod(PaymentMethod.COUNTER);
        pending.setPurpose(PaymentPurpose.MEMBERSHIP);
        pending.setReferenceId(plan.getId());
        pending.setPurchasedMonths(3);
        pending.setMonthlyPriceSnapshot(new BigDecimal("500000.00"));
        pending.setStatus(PaymentStatus.PENDING);
        pending.setTransactionCode("PENDING-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        pending = payments.saveAndFlush(pending);
        Integer paymentId = pending.getId();

        assertThrows(ResponseStatusException.class, () -> service.createPayment(
                request(PaymentPurpose.MEMBERSHIP, plan.getId(), PaymentMethod.COUNTER), jwt(member)));
        PaymentResponse settled = service.processAtCounter(paymentId, counter(true), jwt(receptionist));
        Membership created = memberships.findByMemberId(member.getId()).get(0);
        assertEquals("SUCCESS", settled.getStatus());
        assertEquals(new BigDecimal("1500000.00"), settled.getAmount());
        assertEquals(3, created.getPurchasedMonths());
        assertEquals(new BigDecimal("1500000.00"), created.getPaidAmount());
        assertEquals(created.getStartDate().plusMonths(3), created.getEndDate());
        assertEquals(paymentId, created.getSourcePayment().getId());
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
    void adminFreeAssignmentDefaultsAutoRenewOffAndWritesAuditWithoutPayment() throws Exception {
        String body = "{\"memberId\":" + member.getId() + ",\"planId\":" + plan.getId()
                + ",\"startDate\":\"" + LocalDate.now(clock.withZone(BUSINESS_ZONE)) + "\"}";
        var response = mvc.perform(post("/api/memberships").header("Authorization", bearer(admin))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autoRenew").value(false))
                .andReturn();
        Integer membershipId = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.id");

        assertTrue(payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .noneMatch(payment -> payment.getPurpose() == PaymentPurpose.MEMBERSHIP
                        && payment.getStatus() == PaymentStatus.SUCCESS));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where actor_user_id = ? "
                + "and action = ? and entity_type = ? and entity_id = ?", Integer.class,
                admin.getId(), "ADMIN_ASSIGN_MEMBERSHIP", "MEMBERSHIP", membershipId.toString()));
        String auditDescription = jdbc.queryForObject("select description from audit_log where actor_user_id = ? "
                + "and action = ? and entity_type = ? and entity_id = ?", String.class,
                admin.getId(), "ADMIN_ASSIGN_MEMBERSHIP", "MEMBERSHIP", membershipId.toString());
        assertTrue(auditDescription.contains("targetMemberId=" + member.getId()));
        assertTrue(auditDescription.contains("planId=" + plan.getId()));
        assertTrue(auditDescription.contains("reason=manual administrative assignment"));
    }

    @Test
    void receptionistMembershipSaleActivatesOnlyAfterCounterPaymentConfirmation() throws Exception {
        String paymentBody = "{\"memberId\":" + member.getId()
                + ",\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"referenceId\":" + plan.getId()
                + ",\"autoRenew\":false,\"purchasedMonths\":1}";

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
                + plan.getId() + ",\"autoRenew\":false,\"purchasedMonths\":1}";
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
                                + plan.getId() + ",\"purchasedMonths\":1}"))
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
                                + plan.getId() + ",\"purchasedMonths\":1}"))
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
    void revenueReportGroupsSuccessfulSettlementByDayMonthAndYearAndFillsZeroPeriods() {
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
        assertEquals(new BigDecimal("300.00"), daily.getPeriods().get(1).revenueByPurpose().get("PT_BOOKING"));
        assertEquals(BigDecimal.ZERO, daily.getPeriods().get(2).totalRevenue());
        assertEquals(BigDecimal.ZERO, daily.getPeriods().get(0).revenueByPurpose().get("MEMBERSHIP"));

        var monthly = reports.revenueReport(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01"),
                RevenueGrouping.MONTH, jwt(admin));
        assertEquals(3, monthly.getPeriods().size());
        assertEquals(new BigDecimal("300.00"), monthly.getPeriods().get(0).totalRevenue());
        assertEquals(new BigDecimal("300.00"), monthly.getPeriods().get(1).totalRevenue());
        assertEquals(BigDecimal.ZERO, monthly.getPeriods().get(2).totalRevenue());
        assertEquals(new BigDecimal("600.00"), monthly.getTotalRevenue());
        assertEquals(new BigDecimal("100.00"), monthly.getRevenueByPurpose().get("MEMBERSHIP"));
        assertEquals(new BigDecimal("200.00"), monthly.getRevenueByPurpose().get("COURSE_REGISTRATION"));
        assertEquals(new BigDecimal("300.00"), monthly.getRevenueByPurpose().get("PT_BOOKING"));

        var yearly = reports.revenueReport(LocalDate.parse("2026-10-01"), LocalDate.parse("2027-01-01"),
                RevenueGrouping.YEAR, jwt(admin));
        assertEquals(2, yearly.getPeriods().size());
        assertEquals(new BigDecimal("600.00"), yearly.getPeriods().get(0).totalRevenue());
        assertEquals(new BigDecimal("40.00"), yearly.getPeriods().get(1).totalRevenue());
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
                                + ",\"purchasedMonths\":1,\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300000.0));
        mvc.perform(post("/api/payments").header("Authorization", bearer(coach.getUser()))
                        .contentType("application/json")
                        .content("{\"method\":\"COUNTER\",\"purpose\":\"MEMBERSHIP\",\"memberId\":"
                                + member.getId() + ",\"referenceId\":" + plan.getId() + ",\"purchasedMonths\":1}"))
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

    private Payment methodNeutralPayment(Membership renewalSource, PaymentStatus status, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(plan.getMonthlyPrice());
        payment.setMethod(method);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setRenewalMembership(renewalSource);
        payment.setPurchasedMonths(1);
        payment.setMonthlyPriceSnapshot(plan.getMonthlyPrice());
        payment.setStatus(status);
        payment.setTransactionCode("NULL-METHOD-" + java.util.UUID.randomUUID().toString().substring(0, 20));
        return payment;
    }

    private PaymentCheckoutAttempt checkoutAttempt(Payment payment, LocalDateTime createdAt, String reference) {
        PaymentCheckoutAttempt attempt = new PaymentCheckoutAttempt();
        attempt.setPayment(payment);
        attempt.setProvider(CheckoutProvider.VNPAY);
        attempt.setTransactionReference(reference);
        attempt.setAmount(payment.getAmount());
        attempt.setCreatedAt(createdAt);
        attempt.setExpiresAt(createdAt.plusMinutes(15));
        attempt.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        return attempt;
    }

    private RenewalFixture renewalFixture(PaymentMethod selectedMethod, Boolean nextAutoRenew, boolean includeAttempt) {
        return renewalFixture(member, selectedMethod, nextAutoRenew, includeAttempt);
    }

    private RenewalFixture renewalFixture(User owner, PaymentMethod selectedMethod, Boolean nextAutoRenew, boolean includeAttempt) {
        PaymentResponse original = service.createPayment(request(PaymentPurpose.MEMBERSHIP, plan.getId(),
                PaymentMethod.COUNTER), jwt(owner));
        service.processAtCounter(original.getId(), counter(true), jwt(receptionist));
        Membership source = memberships.findByMemberId(owner.getId()).stream()
                .filter(value -> value.getSourcePayment().getId().equals(original.getId())).findFirst().orElseThrow();
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(2));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        assertEquals(1, renewalService.createDueRenewalRequests());
        Payment renewal = payments.findByRenewalMembership_Id(source.getId()).orElseThrow();
        renewal.setMethod(selectedMethod);
        renewal.setRenewalAutoRenewChoice(nextAutoRenew);
        payments.saveAndFlush(renewal);
        PaymentCheckoutAttempt attempt = null;
        if (includeAttempt) {
            attempt = checkoutAttempts.saveAndFlush(checkoutAttempt(renewal, now(),
                    newRnlReference()));
        }
        return new RenewalFixture(source, renewal, attempt);
    }

    private static String newRnlReference() {
        return "RNL" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    private record RenewalFixture(Membership source, Payment payment, PaymentCheckoutAttempt attempt) { }

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

    // ---------------------------------------------------------------- fixtures

    /**
     * Clears the payment-related tables, but removes users, coaches and sports only when this class
     * created them: other test classes may leave rows (e.g. member sport levels) that reference theirs.
     */
    private void cleanDatabase() {
        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        checkoutAttempts.deleteAllInBatch();
        // Stage A adds references in both directions: renewal requests point to their source
        // membership while purchased memberships point to their originating payment.
        // The method-neutral CHECK requires a renewal link while method is NULL. Give test-only
        // rows a valid method before breaking the FK cycle used by fixture cleanup.
        jdbc.update("update Payment set method = 'COUNTER' where renewal_membership_id is not null and method is null");
        jdbc.update("update Payment set renewal_membership_id = null");
        jdbc.update("update Membership set source_payment_id = null");
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
        if (purpose == PaymentPurpose.MEMBERSHIP) request.setPurchasedMonths(1);
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
