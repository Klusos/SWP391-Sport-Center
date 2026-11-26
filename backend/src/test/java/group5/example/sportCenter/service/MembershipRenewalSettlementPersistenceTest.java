package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentCloseReason;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:renewalSettlementJpa;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("phase1")
@Import({ MembershipRenewalSettlementService.class, AuditLogService.class, MembershipRenewalSettlementPersistenceTest.FixedClockConfiguration.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MembershipRenewalSettlementPersistenceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDate SOURCE_END = LocalDate.of(2026, 11, 23);

    @Autowired MembershipRenewalSettlementService settlement;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @MockitoSpyBean MembershipRepository memberships;
    @Autowired MembershipRenewalIntentRepository intents;
    @MockitoSpyBean PaymentRepository payments;
    @MockitoBean NotificationService notifications;
    @Autowired AuditLogRepository auditLogs;

    private User member;
    private MembershipPlan plan;
    private Membership source;
    private MembershipRenewalIntent intent;
    private Payment payment;
    private Payment secondAttempt;

    @BeforeEach
    void createIsolatedFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        member = new User(); member.setName("Renewal test"); member.setEmail(suffix + "@renewal.test");
        member.setPasswordHash("not-a-real-password"); member.setRole("MEMBER"); member.setStatus("ACTIVE");
        member = users.saveAndFlush(member);

        plan = new MembershipPlan(); plan.setName("Renewal test plan"); plan.setPrice(new BigDecimal("800000.00"));
        plan.setDurationMonths(2); plan.setCourseDiscountPercent(new BigDecimal("12.50"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("7.00"));
        plan.setAllowPersonalTrainingPlan(true); plan.setAllowOneOnOneCoaching(false); plan.setStatus("ACTIVE");
        plan = plans.saveAndFlush(plan);

        source = new Membership(); source.setMember(member); source.setPlan(plan); source.setAutoRenew(true);
        source.setStartDate(SOURCE_END.minusMonths(1)); source.setEndDate(SOURCE_END); source.setStatus("ACTIVE");
        source = memberships.saveAndFlush(source);

        intent = new MembershipRenewalIntent(); intent.setSourceMembership(source); intent.setTargetPlan(plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true); intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(NOW.minusDays(2)); intent.setValidFrom(SOURCE_END.minusDays(3).atStartOfDay());
        intent.setValidUntil(SOURCE_END.atStartOfDay()); intent.setQuoteCapturedAt(NOW.minusDays(2));
        intent.setSourceEndDateSnapshot(SOURCE_END); intent.setSuccessorStartDate(SOURCE_END);
        intent.setSuccessorEndDate(SOURCE_END.plusMonths(2)); intent.setPlanNameSnapshot("Frozen name");
        intent.setPlanDescriptionSnapshot("Frozen description"); intent.setPlanPriceSnapshot(new BigDecimal("800000.00"));
        intent.setQuotedAmount(new BigDecimal("800000.00")); intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(new BigDecimal("12.50"));
        intent.setPtDiscountPercentSnapshot(new BigDecimal("7.00"));
        intent.setAllowPtPlanSnapshot(true); intent.setAllowOneOnOneSnapshot(false);
        intent = intents.saveAndFlush(intent);

        payment = new Payment(); payment.setMember(member); payment.setAmount(new BigDecimal("800000.00"));
        payment.setMethod(PaymentMethod.ONLINE); payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId()); payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + suffix); payment.setCreatedAt(NOW.minusMinutes(5));
        payment.setAttemptValidUntil(NOW.plusMinutes(10)); payment.setRenewalIntent(intent);
        payment = payments.saveAndFlush(payment);
    }

    @AfterEach
    void removeOnlyThisTestFixture() {
        if (intent == null) return;
        memberships.findByRenewalIntent_Id(intent.getId()).ifPresent(successor -> {
            memberships.delete(successor);
            memberships.flush();
        });
        intents.findById(intent.getId()).ifPresent(row -> {
            row.setIntentStatus(RenewalIntentStatus.CANCELLED);
            row.setSettledPayment(null); row.setSettledAt(null);
            row.setClosedAt(NOW); row.setClosedReason(RenewalIntentCloseReason.AUTO_RENEW_DISABLED);
            intents.saveAndFlush(row);
        });
        payments.findById(payment.getId()).ifPresent(payments::delete);
        if (secondAttempt != null) payments.findById(secondAttempt.getId()).ifPresent(payments::delete);
        payments.flush();
        intents.findById(intent.getId()).ifPresent(intents::delete);
        intents.flush();
        memberships.findById(source.getId()).ifPresent(memberships::delete);
        memberships.flush();
        plans.findById(plan.getId()).ifPresent(plans::delete);
        plans.flush();
        users.findById(member.getId()).ifPresent(users::delete);
        users.flush();
    }

    @Test
    void successfulSettlementPersistsIntentPaymentAndSuccessorAsOneOutcome() {
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(intent.getId(), payment.getId(),
                PaymentMethod.ONLINE, NOW, "VNPAY_SIGNATURE_VERIFIED", "h2-provider-reference");

        var result = settlement.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ACCEPTED, result.outcome());
        assertTrue(auditLogs.findAll().stream().anyMatch(entry -> "SETTLE".equals(entry.getAction())
                && "RENEWAL_PAYMENT".equals(entry.getEntityType())
                && String.valueOf(payment.getId()).equals(entry.getEntityId())
                && entry.getDescription().contains("PENDING -> SUCCESS")));
        Payment savedPayment = payments.findById(payment.getId()).orElseThrow();
        MembershipRenewalIntent savedIntent = intents.findById(intent.getId()).orElseThrow();
        Membership savedSuccessor = memberships.findByRenewalIntent_Id(intent.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, savedPayment.getStatus());
        assertEquals(NOW, savedPayment.getPaidAt());
        assertEquals(NOW, savedPayment.getRenewalSettlementAcceptedAt());
        assertEquals(RenewalIntentStatus.SETTLED, savedIntent.getIntentStatus());
        assertEquals(payment.getId(), savedIntent.getSettledPayment().getId());
        assertEquals(source.getEndDate(), savedSuccessor.getStartDate());
        assertEquals(source.getEndDate().plusMonths(2), savedSuccessor.getEndDate());
        assertEquals("Frozen name", savedSuccessor.getPlanNameSnapshot());
        assertEquals(new BigDecimal("800000.00"), savedSuccessor.getPaidValueBasisAmount());
        assertEquals("RENEWAL_PAYMENT", savedSuccessor.getPaidValueBasisSource());
    }

    @Test
    void successorInsertFailureRollsBackPaymentAndIntentSettlement() {
        doThrow(new IllegalStateException("simulated successor persistence failure"))
                .when(memberships).saveAndFlush(any(Membership.class));
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(intent.getId(), payment.getId(),
                PaymentMethod.ONLINE, NOW, "VNPAY_SIGNATURE_VERIFIED", "h2-rollback-reference");

        assertThrows(IllegalStateException.class, () -> settlement.settle(evidence));

        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertNull(payments.findById(payment.getId()).orElseThrow().getRenewalSettlementAcceptedAt());
        assertEquals(RenewalIntentStatus.OPEN, intents.findById(intent.getId()).orElseThrow().getIntentStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void competingSettlementAttemptsHaveExactlyOneWinner() throws Exception {
        secondAttempt = new Payment(); secondAttempt.setMember(member);
        secondAttempt.setAmount(payment.getAmount()); secondAttempt.setMethod(PaymentMethod.ONLINE);
        secondAttempt.setPurpose(PaymentPurpose.MEMBERSHIP); secondAttempt.setReferenceId(plan.getId());
        secondAttempt.setStatus(PaymentStatus.PENDING);
        secondAttempt.setTransactionCode("TXN-2-" + UUID.randomUUID().toString().substring(0, 12));
        secondAttempt.setCreatedAt(payment.getCreatedAt()); secondAttempt.setAttemptValidUntil(payment.getAttemptValidUntil());
        secondAttempt.setRenewalIntent(intent); secondAttempt = payments.saveAndFlush(secondAttempt);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RenewalSettlementResult> first = pool.submit(() -> settleAtBarrier(payment, ready, start));
            Future<RenewalSettlementResult> second = pool.submit(() -> settleAtBarrier(secondAttempt, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            RenewalSettlementResult firstResult = first.get(10, TimeUnit.SECONDS);
            RenewalSettlementResult secondResult = second.get(10, TimeUnit.SECONDS);
            long accepted = java.util.stream.Stream.of(firstResult, secondResult)
                    .filter(result -> result.outcome() == RenewalSettlementOutcome.ACCEPTED).count();
            long reconciled = java.util.stream.Stream.of(firstResult, secondResult)
                    .filter(result -> result.outcome() == RenewalSettlementOutcome.RECONCILIATION_REQUIRED).count();
            assertEquals(1, accepted);
            assertEquals(1, reconciled);
            assertEquals(1, memberships.findByRenewalIntent_Id(intent.getId()).stream().count());
            assertEquals(RenewalIntentStatus.SETTLED, intents.findById(intent.getId()).orElseThrow().getIntentStatus());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private RenewalSettlementResult settleAtBarrier(Payment attempt, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("settlement race did not start");
        return settlement.settle(RenewalSettlementEvidence.verifiedOnlineCollection(intent.getId(), attempt.getId(),
                PaymentMethod.ONLINE, NOW, "VNPAY_SIGNATURE_VERIFIED", "concurrent-" + attempt.getId()));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedRenewalClock() {
            return Clock.fixed(NOW.atZone(VN).toInstant(), VN);
        }
    }
}
