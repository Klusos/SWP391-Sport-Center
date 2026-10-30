package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest(properties = {
        "app.payment.provider=mock",
        "sportcenter.scheduling.enabled=false"
})
@ActiveProfiles("phase1")
@Import(MembershipAutoRenewalIntegrationTest.TestClockConfiguration.class)
class MembershipAutoRenewalIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@renewal.example.test";
    private static final String MOCK_CALLBACK_SECRET = "phase1-test-payment-callback-secret";

    @Autowired MembershipAutoRenewalService autoRenewal;
    @Autowired MembershipService membershipService;
    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRows;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository plans;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestClock clock;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private MockMvc mvc;
    private LocalDate today;
    private User member;
    private User receptionist;
    private User admin;
    private MembershipPlan plan;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(BUSINESS_ZONE);
        clock.setInstant(today.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        cleanDatabase();
        member = user("MEMBER", "member-renew");
        receptionist = user("RECEPTIONIST", "reception-renew");
        admin = user("ADMIN", "admin-renew");
        plan = plan("Renew plan", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void enablingInsideTheNoticeWindowCreatesARenewalRequestAndNotifiesTheMember() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), false);

        mvc.perform(patch("/api/memberships/" + current.getId() + "/enable-auto-renew").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoRenew").value(true));

        List<Payment> pending = pendingRenewals();
        assertEquals(1, pending.size());
        Payment request = pending.get(0);
        assertEquals(PaymentMethod.COUNTER, request.getMethod());
        assertEquals(PaymentPurpose.MEMBERSHIP, request.getPurpose());
        assertEquals(plan.getId(), request.getReferenceId());
        assertEquals(0, new BigDecimal("400000.00").compareTo(request.getAmount()));
        assertEquals(true, request.getAutoRenew());
        assertEquals(current.getId(), MembershipAutoRenewalService.sourceMembershipId(request));
        assertEquals(1, notificationsFor(member));

        // Turning it off again cancels the unpaid request.
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel-renewal").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(request.getId()).orElseThrow().getStatus());
        assertTrue(pendingRenewals().isEmpty());
    }

    @Test
    void theDailyJobOnlyRenewsTheLastDueMembershipWithoutPendingPayments() throws Exception {
        membership(member, plan, today.minusDays(25), today.plusDays(5), true);
        User due = user("MEMBER", "due-renew");
        membership(due, plan, today.minusDays(27), today.plusDays(3), true);
        User optedOut = user("MEMBER", "off-renew");
        membership(optedOut, plan, today.minusDays(27), today.plusDays(3), false);
        User queued = user("MEMBER", "queued-renew");
        Membership queuedCurrent = membership(queued, plan, today.minusDays(29), today.plusDays(1), true);
        membership(queued, plan, queuedCurrent.getEndDate(), queuedCurrent.getEndDate().plusMonths(1), false);

        mvc.perform(post("/api/memberships/auto-renew/run").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.cancelled").value(0));
        List<Payment> pending = pendingRenewals();
        assertEquals(1, pending.size());
        assertEquals(due.getId(), pending.get(0).getMember().getId());

        // A second run does not duplicate the pending request.
        assertEquals(0, autoRenewal.runDaily().created());
        mvc.perform(post("/api/memberships/auto-renew/run").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void schedulerDoesNotCreateRequestWhenAutoRenewIsDisabled() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), false);

        assertTrue(autoRenewal.createIfDue(current).isEmpty());
        assertTrue(pendingRenewals().isEmpty());
    }

    @Test
    void schedulerDoesNotCreateAnotherRequestWhenSourceAlreadyHasSuccessfulRenewalPayment() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment settled = new Payment();
        settled.setMember(member);
        settled.setAmount(plan.getPrice());
        settled.setMethod(PaymentMethod.ONLINE);
        settled.setPurpose(PaymentPurpose.MEMBERSHIP);
        settled.setReferenceId(plan.getId());
        settled.setAutoRenew(true);
        settled.setStatus(PaymentStatus.SUCCESS);
        settled.setTransactionCode("TXN-SETTLED-RENEWAL");
        settled.setPaidAt(today.atTime(9, 0));
        settled.setNote(MembershipAutoRenewalService.NOTE_PREFIX + current.getId() + " | verified renewal");
        paymentRows.save(settled);

        assertTrue(autoRenewal.createIfDue(current).isEmpty());
        assertTrue(pendingRenewals().isEmpty());
    }

    @Test
    void schedulerRefreshesMembershipAfterOptOutCommitsWhileItWaitsForMemberLock() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        CountDownLatch schedulerReady = new CountDownLatch(1);
        CountDownLatch allowSchedulerToStart = new CountDownLatch(1);

        ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "renewal-scheduler-before-lock"));
        Future<Optional<Payment>> scheduled = null;
        try {
            scheduled = worker.submit(() -> {
                schedulerReady.countDown();
                awaitLatch(allowSchedulerToStart, "scheduler start after opt-out commit");
                return new TransactionTemplate(transactionManager).execute(
                        status -> autoRenewal.createIfDue(current));
            });
            awaitLatch(schedulerReady, "scheduler pre-lock checkpoint");

            // The opt-out service transaction commits before the scheduler enters its transaction.
            membershipService.cancelAutoRenew(current.getId(), jwt(member));
            allowSchedulerToStart.countDown();

            assertTrue(scheduled.get(10, TimeUnit.SECONDS).isEmpty());
            assertTrue(pendingRenewals().isEmpty());
            assertEquals(false, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
        } finally {
            allowSchedulerToStart.countDown();
            stopWorkers(worker, scheduled);
        }
    }

    @Test
    void schedulerThatWinsMemberLockCreatesRequestThenOptOutCancelsIt() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        CountDownLatch schedulerHasLock = new CountDownLatch(1);
        CountDownLatch allowSchedulerToFinish = new CountDownLatch(1);
        CountDownLatch optOutTransactionStarted = new CountDownLatch(1);

        ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable));
        Future<Optional<Payment>> scheduled = null;
        Future<?> optOut = null;
        try {
            scheduled = workers.submit(() -> {
                Thread.currentThread().setName("renewal-scheduler-with-lock");
                return new TransactionTemplate(transactionManager).execute(status -> {
                    Optional<Payment> result = autoRenewal.createIfDue(current);
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                            "scheduler transaction must retain the member lock until commit");
                    schedulerHasLock.countDown();
                    awaitLatch(allowSchedulerToFinish, "scheduler transaction release");
                    return result;
                });
            });
            awaitLatch(schedulerHasLock, "scheduler transaction holding the member lock");

            optOut = workers.submit(() -> {
                Thread.currentThread().setName("renewal-opt-out-waiting");
                return new TransactionTemplate(transactionManager).execute(status -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                            "opt-out must use an independent transaction");
                    optOutTransactionStarted.countDown();
                    return membershipService.cancelAutoRenew(current.getId(), jwt(member));
                });
            });
            awaitLatch(optOutTransactionStarted, "opt-out transaction while scheduler holds the member lock");
            allowSchedulerToFinish.countDown();

            Payment created = scheduled.get(10, TimeUnit.SECONDS).orElseThrow();
            optOut.get(10, TimeUnit.SECONDS);
            Payment afterOptOut = paymentRows.findById(created.getId()).orElseThrow();
            assertEquals(PaymentStatus.CANCELLED, afterOptOut.getStatus());
            assertEquals(false, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
            assertTrue(pendingRenewals().isEmpty());
        } finally {
            allowSchedulerToFinish.countDown();
            stopWorkers(workers, scheduled, optOut);
        }
    }

    @Test
    void counterSettlementCannotConfirmRenewalAfterOptOutCommitted() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();
        membershipService.cancelAutoRenew(current.getId(), jwt(member));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> payments.processAtCounter(request.getId(), counter(), jwt(receptionist)));
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(false, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void payingTheRenewalQueuesTheNextPeriodWithAutoRenewStillOn() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();

        assertEquals("SUCCESS", payments.processAtCounter(request.getId(), counter(), jwt(receptionist)).getStatus());

        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, rows.size());
        assertEquals(current.getEndDate(), rows.get(1).getStartDate());
        assertEquals(current.getEndDate().plusMonths(1), rows.get(1).getEndDate());
        assertEquals(true, rows.get(1).getAutoRenew());
        assertTrue(paymentRows.findById(request.getId()).orElseThrow().getNote()
                .startsWith(MembershipAutoRenewalService.NOTE_PREFIX));
    }

    @Test
    void aManuallyBoughtRenewalKeepsAutoRenewOnAndTheToggleAppliesToTheWholeChain() throws Exception {
        Membership current = membership(member, plan, today.minusDays(10), today.plusDays(20), true);
        CreatePaymentRequest purchase = new CreatePaymentRequest();
        purchase.setPurpose(PaymentPurpose.MEMBERSHIP);
        purchase.setReferenceId(plan.getId());
        purchase.setMethod(PaymentMethod.COUNTER);
        purchase.setAutoRenew(false);
        PaymentResponse created = payments.createPayment(purchase, jwt(member));
        payments.processAtCounter(created.getId(), counter(), jwt(receptionist));

        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        Membership queued = rows.get(1);
        assertEquals(current.getEndDate(), queued.getStartDate());
        assertEquals(true, queued.getAutoRenew());

        // Turning it off from either membership turns it off for the whole chain, and on again likewise.
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel-renewal").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        assertEquals(false, memberships.findById(queued.getId()).orElseThrow().getAutoRenew());
        assertEquals(false, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
        mvc.perform(patch("/api/memberships/" + queued.getId() + "/enable-auto-renew").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        assertEquals(true, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
        assertEquals(true, memberships.findById(queued.getId()).orElseThrow().getAutoRenew());
    }

    @Test
    void anUnpaidRenewalIsCancelledOnceTheMembershipEnds() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();

        clock.advance(Duration.ofDays(2));
        membershipService.expireMemberships();
        MembershipAutoRenewalService.RunResult result = autoRenewal.runDaily();

        assertEquals(1, result.cancelled());
        assertEquals(0, result.created());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(request.getId()).orElseThrow().getStatus());
        assertEquals("EXPIRED", memberships.findById(current.getId()).orElseThrow().getStatus());
    }

    @Test
    void aPlanNoLongerSoldIsNotRenewedButTheMemberIsTold() {
        MembershipPlan retired = plan("Retired plan", "INACTIVE");
        Membership current = membership(member, retired, today.minusDays(28), today.plusDays(2), true);

        assertTrue(autoRenewal.createIfDue(current).isEmpty());
        assertTrue(pendingRenewals().isEmpty());
        assertEquals(1, notificationsFor(member));
    }

    @Test
    void convertedOnlineRenewalKeepsItsSourceAndRepeatedRunsDoNotDuplicate() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));

        PaymentResponse online = createOnlineReplacement(counter);
        Payment saved = paymentRows.findById(online.getId()).orElseThrow();
        assertEquals(PaymentMethod.ONLINE, saved.getMethod());
        assertEquals(MembershipAutoRenewalService.NOTE_PREFIX + current.getId(), saved.getNote());
        assertEquals(0, autoRenewal.runDaily().created());
        assertEquals(0, autoRenewal.runDaily().created());
        assertEquals(1, pendingRenewals().size());
        assertEquals(online.getId(), pendingRenewals().get(0).getId());
    }

    @Test
    void disablingAutoRenewCancelsConvertedOnlinePaymentAndLateSuccessRequiresRefund() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel-renewal")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(online.getId()).orElseThrow().getStatus());
        assertTrue(paymentRows.findById(online.getId()).orElseThrow().getNote()
                .startsWith(MembershipAutoRenewalService.NOTE_PREFIX));

        mvc.perform(get(callbackPath(online.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(false, memberships.findById(current.getId()).orElseThrow().getAutoRenew());
    }

    @Test
    void expiryCleanupDoesNotOverrideVerifiedPaymentMadeBeforeExclusiveDeadline() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        clock.advance(Duration.ofDays(2));
        membershipService.expireMemberships();
        MembershipAutoRenewalService.RunResult expired = autoRenewal.runDaily();

        assertEquals(1, expired.cancelled());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(online.getId()).orElseThrow().getStatus());
        mvc.perform(get(callbackPath(online.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
        assertEquals("EXPIRED", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(current.getEndDate(), memberships.findByMemberId(member.getId()).stream()
                .filter(row -> !row.getId().equals(current.getId())).findFirst().orElseThrow().getStartDate());
        assertEquals(today.atTime(10, 0), paymentRows.findById(online.getId()).orElseThrow().getPaidAt());
        assertEquals(PaymentStatus.SUCCESS, paymentRows.findById(online.getId()).orElseThrow().getStatus());
    }

    @Test
    void appendedExpiryLikeTextCannotAuthorizeAUserCancelledPayment() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        Payment cancelled = paymentRows.findById(online.getId()).orElseThrow();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        cancelled.setNote(MembershipAutoRenewalService.NOTE_PREFIX + current.getId()
                + " cancelled by MEMBER; " + MembershipAutoRenewalService.EXPIRY_CANCELLATION_NOTE);
        paymentRows.save(cancelled);

        mvc.perform(get(callbackPath(online.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void manuallyCancelledConvertedOnlinePaymentCannotRenewAfterLateSuccess() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);
        payments.cancelPayment(online.getId(), jwt(member));

        mvc.perform(get(callbackPath(online.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void paymentAtTheExclusiveEndDateBoundaryRequiresRefund() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);
        String deadline = current.getEndDate().atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss"));

        clock.setInstant(current.getEndDate().atStartOfDay(BUSINESS_ZONE).toInstant());
        mvc.perform(get(callbackPath(online.getPaymentUrl(), deadline)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(LocalDateTime.parse(current.getEndDate() + "T00:00:00"),
                paymentRows.findById(online.getId()).orElseThrow().getPaidAt());
    }

    @Test
    void paymentAfterDeadlineButBeforeExpiryCleanupRequiresRefund() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);
        LocalDateTime afterDeadline = current.getEndDate().atStartOfDay().plusSeconds(1);
        String paymentTime = afterDeadline.format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss"));
        clock.setInstant(afterDeadline.atZone(BUSINESS_ZONE).toInstant());

        mvc.perform(get(callbackPath(online.getPaymentUrl(), paymentTime)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void counterReceiptAtExclusiveDeadlineRequiresRefund() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();
        clock.setInstant(current.getEndDate().atStartOfDay(BUSINESS_ZONE).toInstant());

        PaymentResponse result = payments.processAtCounter(request.getId(), counter(), jwt(receptionist));

        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void verifiedPreDeadlinePaymentCanArriveAfterDeadlineWithoutWaitingForCleanup() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        clock.setInstant(current.getEndDate().atStartOfDay(BUSINESS_ZONE).plusSeconds(10).toInstant());
        mvc.perform(get(callbackPath(online.getPaymentUrl())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(current.getEndDate(), memberships.findByMemberId(member.getId()).stream()
                .filter(row -> !row.getId().equals(current.getId())).findFirst().orElseThrow().getStartDate());
    }

    @Test
    void missingVerifiedPaymentTimeRequiresRefundAndDoesNotActivateMembership() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        mvc.perform(get(callbackPath(online.getPaymentUrl(), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        Payment unresolved = paymentRows.findById(online.getId()).orElseThrow();
        assertTrue(unresolved.getNote().contains("PayDate=missing"));
        assertEquals(null, unresolved.getPaidAt());
    }

    @Test
    void malformedButSignedPaymentTimeRequiresRefund() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        mvc.perform(get(callbackPath(online.getPaymentUrl(), "not-a-date")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        Payment unresolved = paymentRows.findById(online.getId()).orElseThrow();
        assertTrue(unresolved.getNote().contains("PayDate=malformed"));
        assertEquals(null, unresolved.getPaidAt());
    }

    @Test
    void aTamperedPaymentTimeFailsSignatureVerificationWithoutChangingPayment() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);

        mvc.perform(get(tamperedCallbackPath(online.getPaymentUrl(), "20261031000000")))
                .andExpect(status().isForbidden());
        assertEquals(PaymentStatus.PENDING, paymentRows.findById(online.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void successfulOnlineRenewalCreatesExactlyOneQueuedPeriodAndManualMembershipPaymentStaysOrdinary()
            throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment counter = autoRenewal.createIfDue(current).orElseThrow();
        payments.cancelPayment(counter.getId(), jwt(member));
        PaymentResponse online = createOnlineReplacement(counter);
        LocalDateTime verifiedPaymentTime = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));

        mvc.perform(get(callbackPath(online.getPaymentUrl()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(verifiedPaymentTime, paymentRows.findById(online.getId()).orElseThrow().getPaidAt(),
                "Marked Auto-renew success uses the verified provider payment time");
        clock.advance(java.time.Duration.ofMinutes(1));
        mvc.perform(get(callbackPath(online.getPaymentUrl()))).andExpect(status().isOk());
        assertEquals(verifiedPaymentTime, paymentRows.findById(online.getId()).orElseThrow().getPaidAt(),
                "Duplicate callback cannot overwrite Auto-renew paidAt");
        assertEquals(0, autoRenewal.runDaily().created());
        assertEquals(0, autoRenewal.runDaily().cancelled());
        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, rows.size());
        assertEquals(current.getEndDate(), rows.get(1).getStartDate());
        assertEquals(current.getEndDate().plusMonths(1), rows.get(1).getEndDate());
        assertEquals(true, rows.get(1).getAutoRenew());

        User manualMember = user("MEMBER", "manual-online");
        Membership manualCurrent = membership(manualMember, plan, today.minusDays(20), today.plusDays(10), false);
        CreatePaymentRequest manual = new CreatePaymentRequest();
        manual.setPurpose(PaymentPurpose.MEMBERSHIP);
        manual.setReferenceId(plan.getId());
        manual.setMethod(PaymentMethod.ONLINE);
        manual.setAutoRenew(false);
        PaymentResponse manualPayment = payments.createPayment(manual, jwt(manualMember));
        assertTrue(paymentRows.findById(manualPayment.getId()).orElseThrow().getNote() == null);
        mvc.perform(get(callbackPath(manualPayment.getPaymentUrl()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        List<Membership> manualRows = memberships.findByMemberId(manualMember.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, manualRows.size());
        assertEquals(manualCurrent.getEndDate(), manualRows.get(1).getStartDate());
        assertEquals(false, manualRows.get(1).getAutoRenew());
        assertTrue(paymentRows.findById(manualPayment.getId()).orElseThrow().getNote() == null);
    }

    // ---------------------------------------------------------------- helpers

    private List<Payment> pendingRenewals() {
        return paymentRows.findByStatusAndPurposeAndNoteStartingWith(
                PaymentStatus.PENDING, PaymentPurpose.MEMBERSHIP, MembershipAutoRenewalService.NOTE_PREFIX);
    }

    private void stopWorkers(ExecutorService workers, Future<?>... tasks) throws Exception {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS),
                        "Concurrency test workers did not terminate after releasing their latches");
            }
            for (Future<?> task : tasks) {
                if (task != null) {
                    task.get(1, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while cleaning up concurrency test workers", exception);
        }
    }

    private void awaitLatch(CountDownLatch latch, String checkpoint) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for " + checkpoint);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for " + checkpoint, exception);
        }
    }

    private PaymentResponse createOnlineReplacement(Payment counter) {
        CreatePaymentRequest replacement = new CreatePaymentRequest();
        replacement.setPurpose(PaymentPurpose.MEMBERSHIP);
        replacement.setReferenceId(counter.getReferenceId());
        replacement.setMethod(PaymentMethod.ONLINE);
        replacement.setAutoRenew(true);
        replacement.setRenewalSourcePaymentId(counter.getId());
        return payments.createPayment(replacement, jwt(member));
    }

    private String callbackPath(String paymentUrl) {
        return paymentUrl.substring(paymentUrl.indexOf("/api/payments/online/callback"));
    }

    private String callbackPath(String paymentUrl, String paymentTime) {
        Map<String, String> params = callbackParams(paymentUrl);
        if (paymentTime == null) params.remove("paymentTime");
        else params.put("paymentTime", paymentTime);
        params.put("signature", signMockCallback(params.get("transactionCode"), params.get("success"), paymentTime));
        return callbackPathFromParams(params);
    }

    private String tamperedCallbackPath(String paymentUrl, String paymentTime) {
        Map<String, String> params = callbackParams(paymentUrl);
        params.put("paymentTime", paymentTime);
        return callbackPathFromParams(params);
    }

    private Map<String, String> callbackParams(String paymentUrl) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(paymentUrl).getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private String callbackPathFromParams(Map<String, String> params) {
        StringBuilder path = new StringBuilder("/api/payments/online/callback?transactionCode=")
                .append(params.get("transactionCode")).append("&success=").append(params.get("success"));
        if (params.containsKey("paymentTime")) path.append("&paymentTime=").append(params.get("paymentTime"));
        return path.append("&signature=").append(params.get("signature")).toString();
    }

    private String signMockCallback(String transactionCode, String success, String paymentTime) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(MOCK_CALLBACK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String data = transactionCode + "|" + success + "|" + paymentTime;
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long notificationsFor(User owner) {
        return jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, owner.getId());
    }

    private CounterPaymentRequest counter() {
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(true);
        return request;
    }

    private MembershipPlan plan(String name, String status) {
        MembershipPlan created = new MembershipPlan();
        created.setName(name + " " + System.nanoTime());
        created.setPrice(new BigDecimal("400000"));
        created.setDurationMonths(1);
        created.setCourseDiscountPercent(BigDecimal.ZERO);
        created.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        created.setAllowPersonalTrainingPlan(false);
        created.setAllowOneOnOneCoaching(false);
        created.setStatus(status);
        return plans.save(created);
    }

    private Membership membership(User owner, MembershipPlan forPlan, LocalDate start, LocalDate end, boolean autoRenew) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(forPlan);
        membership.setAutoRenew(autoRenew);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
        return memberships.save(membership);
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

    private void cleanDatabase() {
        jdbc.update("delete from audit_log");
        notifications.deleteAllInBatch();
        upgrades.deleteAllInBatch();
        memberships.deleteAllInBatch();
        paymentRows.deleteAllInBatch();
        plans.deleteAllInBatch();
        users.deleteAll(users.findAll().stream().filter(u -> u.getEmail().endsWith(EMAIL_DOMAIN)).toList());
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

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        TestClock membershipAutoRenewalTestClock() {
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

        void advance(Duration duration) {
            current.set(current.get().plus(duration));
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

}
