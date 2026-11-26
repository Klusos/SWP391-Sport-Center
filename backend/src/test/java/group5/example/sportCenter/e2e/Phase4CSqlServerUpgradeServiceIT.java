package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreateMembershipUpgradePathRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradePathRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipUpgradePathService;
import group5.example.sportCenter.service.PaymentService;

/** Service-level Phase 4C checks against the existing, guarded SQL Server clone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=" + Phase4CSqlServerGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false",
        "spring.main.web-application-type=servlet",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "app.payment.callback-secret=phase4c-local-test-only",
        "app.payment.callback-base-url=http://localhost:8080/api/payments/online/callback"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase4CSqlServerGuard.class)
@Import(Phase4CSqlServerUpgradeServiceIT.ClockConfiguration.class)
class Phase4CSqlServerUpgradeServiceIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_SUFFIX = "@phase4c-sql.test";

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentService payments;
    @Autowired MembershipUpgradePathService pathService;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipUpgradePathRepository paths;
    @Autowired PaymentRepository paymentRows;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired TestClock clock;
    @Autowired PlatformTransactionManager transactionManager;

    private final String prefix = "P4CIT-" + UUID.randomUUID().toString().substring(0, 8);
    private LocalDate today;

    @BeforeEach
    void verifyCloneAndSetClock() throws Exception {
        Phase4CSqlServerGuard.verifyRuntime(dataSource, environment, context);
        today = LocalDate.now(VN);
        clock.setInstant(today.atTime(10, 0).atZone(VN).toInstant());
        cleanupOwnedFixtures();
    }

    @AfterEach
    void cleanupOnlyThisRunAndVerifyNoFixturesRemain() {
        cleanupOwnedFixtures();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE email LIKE ?",
                Integer.class, prefix + "-%" + EMAIL_SUFFIX));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE name LIKE ?",
                Integer.class, prefix + "-%"));
    }

    @Test
    void frozenQuoteSurvivesPathAndPlanEditsAndSettlementSupportsChainedCredit() {
        Fixture f = fixture("frozen-chain", "1500000.00", 2);
        String capturedTargetName = f.target.getName();
        PaymentResponse pending = createUpgrade(f, f.target, PaymentMethod.COUNTER);
        MembershipUpgrade frozen = upgrades.findByPaymentId(pending.getId()).orElseThrow();
        assertEquals(new BigDecimal("500000.00"), frozen.getSourcePaidValueBasisAmount());
        assertEquals(30, frozen.getSourceTotalDays());
        assertEquals(20, frozen.getSourceUnusedDays());
        assertEquals(new BigDecimal("333333.00"), frozen.getAppliedCreditAmount());
        assertEquals(new BigDecimal("1166667.00"), pending.getAmount());
        assertEquals(capturedTargetName, jdbc.queryForObject(
                "SELECT target_plan_name_snapshot FROM dbo.membership_upgrade WHERE payment_id=?",
                String.class, pending.getId()));
        assertEquals(new BigDecimal("1166667.00"), jdbc.queryForObject(
                "SELECT amount FROM dbo.payment WHERE id=?", BigDecimal.class, pending.getId()));

        f.target.setName(prefix + "-edited");
        f.target.setDescription("changed after frozen quote");
        f.target.setPrice(new BigDecimal("1900000.00"));
        f.target.setDurationMonths(7);
        f.target.setCourseDiscountPercent(new BigDecimal("1.00"));
        f.target.setPersonalTrainingDiscountPercent(new BigDecimal("2.00"));
        f.target.setAllowPersonalTrainingPlan(false);
        f.target.setAllowOneOnOneCoaching(false);
        f.target.setStatus("INACTIVE");
        plans.saveAndFlush(f.target);
        pathService.setStatus(f.pathId, false, jwt(f.admin));
        User blockedMember = user("disabled-path-member", "MEMBER");
        membership(blockedMember, f.sourcePlan, today.minusDays(10), today.plusDays(20), new BigDecimal("500000.00"));
        assertThrows(ResponseStatusException.class,
                () -> payments.createPayment(upgradeRequest(f.target, PaymentMethod.COUNTER), jwt(blockedMember)));

        assertEquals("SUCCESS", settleCounter(pending, f).getStatus());
        Membership result = activeMembership(f.member);
        assertEquals(today, result.getStartDate());
        assertEquals(today.plusMonths(2), result.getEndDate());
        assertEquals(capturedTargetName, result.getPlanNameSnapshot());
        assertEquals(new BigDecimal("1500000.00"), result.getPlanPriceSnapshot());
        assertEquals(2, result.getPlanDurationMonthsSnapshot());
        assertEquals(new BigDecimal("20.00"), result.getCourseDiscountPercentSnapshot());
        assertTrue(result.getAllowPtPlanSnapshot());
        assertTrue(result.getAllowOneOnOneSnapshot());
        assertEquals("UPGRADE_CHAIN", result.getPaidValueBasisSource());
        assertEquals(new BigDecimal("1500000.00"), result.getPaidValueBasisAmount());
        assertEquals("UPGRADED", memberships.findById(f.source.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.membership WHERE member_id=? AND status='ACTIVE'",
                Integer.class, f.member.getId()));
        assertEquals(new BigDecimal("1500000.00"), jdbc.queryForObject(
                "SELECT paid_value_basis_amount FROM dbo.membership WHERE id=?", BigDecimal.class, result.getId()));

        // Re-enable the now-active source Plan and create the next directed edge.
        f.target.setStatus("ACTIVE");
        plans.saveAndFlush(f.target);
        MembershipPlan elite = plan("chain-target", "2500000.00", 1);
        Integer chainedPath = pathService.create(
                new CreateMembershipUpgradePathRequest(f.target.getId(), elite.getId()), jwt(f.admin)).id();
        PaymentResponse chained = createUpgrade(f, elite, PaymentMethod.COUNTER);
        MembershipUpgrade chainedQuote = upgrades.findByPaymentId(chained.getId()).orElseThrow();
        assertEquals(new BigDecimal("1500000.00"), chainedQuote.getSourcePaidValueBasisAmount());
        assertEquals(new BigDecimal("1500000.00"), chainedQuote.getAppliedCreditAmount());
        assertEquals(new BigDecimal("1000000.00"), chained.getAmount());
        assertEquals("SUCCESS", settleCounter(chained, f).getStatus());
        Membership finalMembership = activeMembership(f.member);
        assertEquals(elite.getId(), finalMembership.getPlan().getId());
        assertEquals(new BigDecimal("2500000.00"), finalMembership.getPaidValueBasisAmount());
        assertEquals("UPGRADE_CHAIN", finalMembership.getPaidValueBasisSource());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.membership_upgrade WHERE source_membership_id=? AND result_membership_id IS NOT NULL",
                Integer.class, result.getId()));
        assertTrue(chainedPath > 0);
    }

    @Test
    void unknownValueZeroPayableAndMinimumDayBoundaryAreEnforced() {
        Fixture unknown = fixture("unknown", "1500000.00", 1, false);
        assertConflict(() -> createUpgrade(unknown, unknown.target, PaymentMethod.COUNTER));

        Fixture twoDays = fixture("two-days", "1500000.00", 1, true, today.minusDays(28), today.plusDays(2),
                new BigDecimal("500000.00"));
        assertConflict(() -> createUpgrade(twoDays, twoDays.target, PaymentMethod.COUNTER));

        Fixture threeDays = fixture("three-days", "1500000.00", 1, true, today.minusDays(27), today.plusDays(3),
                new BigDecimal("500000.00"));
        PaymentResponse boundary = createUpgrade(threeDays, threeDays.target, PaymentMethod.COUNTER);
        MembershipUpgrade quote = upgrades.findByPaymentId(boundary.getId()).orElseThrow();
        assertEquals(3, quote.getSourceUnusedDays());
        assertEquals(new BigDecimal("50000.00"), quote.getAppliedCreditAmount());
        assertEquals("CANCELLED", payments.cancelPayment(boundary.getId(), jwt(threeDays.member)).getStatus());

        Fixture zero = fixture("zero-payable", "1100000.00", 1, true, today.minusDays(10), today.plusDays(20),
                new BigDecimal("5000000.00"));
        assertConflict(() -> createUpgrade(zero, zero.target, PaymentMethod.COUNTER));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=? AND purpose='UPGRADE'",
                Integer.class, zero.member.getId()));
    }

    @Test
    void onlyValidAdminDirectedPlanPathsCanBeConfigured() {
        Fixture f = fixture("admin-paths", "1500000.00", 1);
        assertThrows(ResponseStatusException.class, () -> pathService.create(
                new CreateMembershipUpgradePathRequest(f.sourcePlan.getId(), f.sourcePlan.getId()), jwt(f.admin)));
        assertThrows(ResponseStatusException.class, () -> pathService.create(
                new CreateMembershipUpgradePathRequest(f.sourcePlan.getId(), f.target.getId()), jwt(f.admin)));
        assertThrows(ResponseStatusException.class, () -> pathService.create(
                new CreateMembershipUpgradePathRequest(f.sourcePlan.getId(), f.target.getId()), jwt(f.member)));

        MembershipPlan inactive = plan("inactive-path-target", "1800000.00", 1);
        inactive.setStatus("INACTIVE");
        plans.saveAndFlush(inactive);
        assertThrows(ResponseStatusException.class, () -> pathService.create(
                new CreateMembershipUpgradePathRequest(f.sourcePlan.getId(), inactive.getId()), jwt(f.admin)));

        MembershipPlan cheaper = plan("cheaper-path-target", "400000.00", 1);
        assertThrows(ResponseStatusException.class, () -> pathService.create(
                new CreateMembershipUpgradePathRequest(f.sourcePlan.getId(), cheaper.getId()), jwt(f.admin)));
    }

    @Test
    void failedExpiredLegacyAndNewlyIneligibleSettlementsPreserveSource() {
        Fixture f = fixture("reconcile", "1500000.00", 1);
        PaymentResponse failed = createUpgrade(f, f.target, PaymentMethod.COUNTER);
        assertEquals("CANCELLED", payments.processAtCounter(failed.getId(), counter(false), jwt(f.receptionist)).getStatus());
        assertEquals("ACTIVE", memberships.findById(f.source.getId()).orElseThrow().getStatus());

        PaymentResponse expired = createUpgrade(f, f.target, PaymentMethod.COUNTER);
        clock.advance(Duration.ofDays(2));
        assertEquals("REFUND_REQUIRED", payments.processAtCounter(expired.getId(), counter(true), jwt(f.receptionist)).getStatus());
        assertEquals("ACTIVE", memberships.findById(f.source.getId()).orElseThrow().getStatus());
        clock.setInstant(today.atTime(10, 0).atZone(VN).toInstant());

        Payment legacy = legacyPendingUpgrade(f);
        PaymentResponse reconciled = payments.processAtCounter(legacy.getId(), counter(true), jwt(f.receptionist));
        assertEquals("REFUND_REQUIRED", reconciled.getStatus());
        assertNotNull(paymentRows.findById(legacy.getId()).orElseThrow().getPaidAt());
        assertTrue(upgrades.findByPaymentId(legacy.getId()).orElseThrow().getResultMembership() == null);
        assertEquals("ACTIVE", memberships.findById(f.source.getId()).orElseThrow().getStatus());

        Fixture lateOnline = fixture("late-online", "1500000.00", 1);
        PaymentResponse online = createUpgrade(lateOnline, lateOnline.target, PaymentMethod.ONLINE);
        Map<String, String> signedCallback = query(online.getPaymentUrl());
        LocalDateTime signedPaidAt = LocalDateTime.parse(signedCallback.get("paymentTime"),
                java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss"));
        clock.advance(Duration.ofMinutes(16));
        OnlineCallbackResult lateResult = payments.processOnlineCallback(signedCallback);
        assertEquals("REFUND_REQUIRED", lateResult.payment().getStatus());
        assertEquals(signedPaidAt, paymentRows.findById(online.getId()).orElseThrow().getPaidAt());
        assertEquals("ACTIVE", memberships.findById(lateOnline.source.getId()).orElseThrow().getStatus());
        clock.setInstant(today.atTime(10, 0).atZone(VN).toInstant());

        Fixture conflict = fixture("late-conflict", "1500000.00", 1);
        PaymentResponse quote = createUpgrade(conflict, conflict.target, PaymentMethod.COUNTER);
        memberships.saveAndFlush(membership(conflict.member, conflict.source.getPlan(),
                conflict.source.getEndDate(), conflict.source.getEndDate().plusMonths(1), new BigDecimal("500000.00")));
        assertEquals("REFUND_REQUIRED", payments.processAtCounter(quote.getId(), counter(true), jwt(conflict.receptionist)).getStatus());
        assertEquals("ACTIVE", memberships.findById(conflict.source.getId()).orElseThrow().getStatus());
    }

    @Test
    void repeatedAndConcurrentOnlineCallbacksApplyOneUpgradeAndPreserveProviderTime() throws Exception {
        Fixture f = fixture("callbacks", "1500000.00", 1);
        PaymentResponse online = createUpgrade(f, f.target, PaymentMethod.ONLINE);
        Map<String, String> callback = query(online.getPaymentUrl());
        LocalDateTime providerTime = LocalDateTime.parse(callback.get("paymentTime"),
                java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmmss"));

        List<OnlineCallbackResult> outcomes = concurrent(() -> payments.processOnlineCallback(callback),
                () -> payments.processOnlineCallback(callback));
        assertEquals(1, outcomes.stream().filter(x -> x.status() == OnlineCallbackResult.Status.APPLIED).count());
        assertTrue(outcomes.stream().allMatch(x -> x.status() == OnlineCallbackResult.Status.APPLIED
                || x.status() == OnlineCallbackResult.Status.ALREADY_PROCESSED));
        assertEquals(providerTime, paymentRows.findById(online.getId()).orElseThrow().getPaidAt());
        assertEquals(2, memberships.findByMemberId(f.member.getId()).size());
        assertEquals("UPGRADED", memberships.findById(f.source.getId()).orElseThrow().getStatus());
        assertEquals(1, upgrades.findByPaymentId(online.getId()).orElseThrow().getResultMembership() == null ? 0 : 1);
        assertEquals(OnlineCallbackResult.Status.ALREADY_PROCESSED,
                payments.processOnlineCallback(callback).status());
        assertEquals(2, memberships.findByMemberId(f.member.getId()).size());
    }

    @Test
    void simultaneousQuoteSettlementPurchaseConflictAndRollbackUseIndependentTransactions() throws Exception {
        Fixture quoteRace = fixture("quote-race", "1500000.00", 1);
        List<Boolean> quoteResults = concurrent(
                () -> attemptCreate(quoteRace, quoteRace.target),
                () -> attemptCreate(quoteRace, quoteRace.target));
        assertEquals(1, quoteResults.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=? AND purpose='UPGRADE' AND status='PENDING'",
                Integer.class, quoteRace.member.getId()));

        Fixture race = fixture("settle-race", "1500000.00", 1);
        PaymentResponse payment = createUpgrade(race, race.target, PaymentMethod.COUNTER);
        List<Boolean> settlements = concurrent(
                () -> attemptCounterSettlement(payment.getId(), race),
                () -> attemptCounterSettlement(payment.getId(), race));
        assertEquals(1, settlements.stream().filter(Boolean::booleanValue).count());
        assertEquals("UPGRADED", memberships.findById(race.source.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(race.member.getId()).stream().filter(x -> "ACTIVE".equals(x.getStatus())).count());

        Fixture purchaseRace = fixture("purchase-race", "1500000.00", 1);
        List<Boolean> purchaseVsUpgrade = concurrent(
                () -> attemptCreate(purchaseRace, purchaseRace.target),
                () -> attemptPurchase(purchaseRace));
        assertEquals(1, purchaseVsUpgrade.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=? AND status='PENDING'",
                Integer.class, purchaseRace.member.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=? AND purpose='MEMBERSHIP'",
                Integer.class, purchaseRace.member.getId()));

        Fixture rollback = fixture("rollback", "1500000.00", 1);
        PaymentResponse rollbackPayment = createUpgrade(rollback, rollback.target, PaymentMethod.COUNTER);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.execute(status -> {
            assertEquals("SUCCESS", payments.processAtCounter(rollbackPayment.getId(), counter(true),
                    jwt(rollback.receptionist)).getStatus());
            status.setRollbackOnly();
            return null;
        });
        assertEquals("PENDING", paymentRows.findById(rollbackPayment.getId()).orElseThrow().getStatus().name());
        assertEquals("ACTIVE", memberships.findById(rollback.source.getId()).orElseThrow().getStatus());
        assertNull(upgrades.findByPaymentId(rollbackPayment.getId()).orElseThrow().getResultMembership());
        assertEquals(1, memberships.findByMemberId(rollback.member.getId()).size());
    }

    @Test
    void filteredUniqueSuccessPerSourceIndexRejectsSecondSuccessfulResult() {
        Fixture f = fixture("filtered-index", "1500000.00", 1);
        Membership result = membership(f.member, f.target, today, today.plusMonths(1), new BigDecimal("1500000.00"));
        MembershipUpgrade first = emptyUpgradeAttempt(f);
        MembershipUpgrade second = emptyUpgradeAttempt(f);
        upgrades.saveAllAndFlush(List.of(first, second));
        List<Boolean> winners = raceFilteredIndexUpdates(first.getId(), second.getId(), result.getId());
        assertEquals(1, winners.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_upgrade WHERE source_membership_id=? AND result_membership_id IS NOT NULL",
                Integer.class, f.source.getId()));
    }

    private MembershipUpgrade emptyUpgradeAttempt(Fixture f) {
        Payment payment = new Payment();
        payment.setMember(f.member);
        payment.setAmount(new BigDecimal("1000000.00"));
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.UPGRADE);
        payment.setReferenceId(f.target.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setCreatedAt(LocalDateTime.now(clock));
        payment.setPaidAt(LocalDateTime.now(clock));
        payment = paymentRows.saveAndFlush(payment);
        MembershipUpgrade attempt = new MembershipUpgrade();
        attempt.setPayment(payment);
        attempt.setSourceMembership(f.source);
        attempt.setTargetPlan(f.target);
        attempt.setRemainingDays(20);
        attempt.setExpiresAt(LocalDateTime.now(clock).plusHours(1));
        attempt.setCreatedAt(LocalDateTime.now(clock));
        return attempt;
    }

    private List<Boolean> raceFilteredIndexUpdates(Integer firstId, Integer secondId, Integer resultId) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Integer id : List.of(firstId, secondId)) {
                futures.add(executor.submit(() -> {
                    try {
                        return transaction.execute(status -> {
                            MembershipUpgrade attempt = upgrades.findById(id).orElseThrow();
                            loaded.countDown();
                            try {
                                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("unique-index barrier timeout");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                            attempt.setResultMembership(memberships.findById(resultId).orElseThrow());
                            upgrades.saveAndFlush(attempt);
                            return true;
                        });
                    } catch (DataIntegrityViolationException expectedUniqueIndexConflict) {
                        return false;
                    }
                }));
            }
            assertTrue(loaded.await(10, TimeUnit.SECONDS), "Both transactions must reach the filtered-index barrier");
            release.countDown();
            return List.of(futures.get(0).get(35, TimeUnit.SECONDS), futures.get(1).get(35, TimeUnit.SECONDS));
        } catch (Exception e) {
            throw new AssertionError("Concurrent filtered unique-index test did not complete", e);
        } finally {
            release.countDown();
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private Fixture fixture(String label, String targetPrice, int targetMonths) {
        return fixture(label, targetPrice, targetMonths, true, today.minusDays(10), today.plusDays(20),
                new BigDecimal("500000.00"));
    }

    private Fixture fixture(String label, String targetPrice, int targetMonths, boolean verified) {
        return fixture(label, targetPrice, targetMonths, verified, today.minusDays(10), today.plusDays(20),
                verified ? new BigDecimal("500000.00") : null);
    }

    private Fixture fixture(String label, String targetPrice, int targetMonths, boolean verified,
            LocalDate start, LocalDate end, BigDecimal paidBasis) {
        User member = user(label + "-member", "MEMBER");
        User admin = user(label + "-admin", "ADMIN");
        User receptionist = user(label + "-reception", "RECEPTIONIST");
        MembershipPlan sourcePlan = plan(label + "-source", "500000.00", 1);
        MembershipPlan targetPlan = plan(label + "-target", targetPrice, targetMonths);
        Membership source = membership(member, sourcePlan, start, end, paidBasis);
        if (!verified) {
            source.setPaidValueBasisStatus("UNKNOWN");
            source.setPaidValueBasisSource("LEGACY_UNKNOWN");
            source.setPaidValueBasisAmount(null);
            memberships.saveAndFlush(source);
        }
        Integer pathId = pathService.create(
                new CreateMembershipUpgradePathRequest(sourcePlan.getId(), targetPlan.getId()), jwt(admin)).id();
        return new Fixture(member, admin, receptionist, sourcePlan, targetPlan, source, pathId);
    }

    private User user(String label, String role) {
        User user = new User();
        user.setName(prefix + " " + label);
        user.setEmail(prefix + "-" + label + EMAIL_SUFFIX);
        user.setPasswordHash("not-used-by-service-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private MembershipPlan plan(String label, String price, int duration) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName(prefix + "-" + label);
        plan.setDescription("Phase 4C isolated service fixture");
        plan.setPrice(new BigDecimal(price));
        plan.setDurationMonths(duration);
        plan.setCourseDiscountPercent(new BigDecimal("20.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("10.00"));
        plan.setAllowPersonalTrainingPlan(true);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("ACTIVE");
        return plans.saveAndFlush(plan);
    }

    private Membership membership(User member, MembershipPlan plan, LocalDate start, LocalDate end, BigDecimal basis) {
        Membership m = new Membership();
        m.setMember(member);
        m.setPlan(plan);
        m.setAutoRenew(false);
        m.setStartDate(start);
        m.setEndDate(end);
        m.setStatus("ACTIVE");
        m.setBenefitSnapshotStatus("COMPLETE");
        m.setBenefitSnapshotSource("PURCHASE");
        m.setPlanNameSnapshot(plan.getName());
        m.setPlanDescriptionSnapshot(plan.getDescription());
        m.setPlanPriceSnapshot(plan.getPrice());
        m.setPlanDurationMonthsSnapshot(plan.getDurationMonths());
        m.setCourseDiscountPercentSnapshot(plan.getCourseDiscountPercent());
        m.setPtDiscountPercentSnapshot(plan.getPersonalTrainingDiscountPercent());
        m.setAllowPtPlanSnapshot(true);
        m.setAllowOneOnOneSnapshot(true);
        if (basis != null) {
            m.setPaidValueBasisStatus("VERIFIED");
            m.setPaidValueBasisAmount(basis);
            m.setPaidValueBasisSource("PURCHASE_PAYMENT");
        } else {
            m.setPaidValueBasisStatus("UNKNOWN");
            m.setPaidValueBasisSource("LEGACY_UNKNOWN");
        }
        return memberships.saveAndFlush(m);
    }

    private PaymentResponse createUpgrade(Fixture f, MembershipPlan target, PaymentMethod method) {
        return payments.createPayment(upgradeRequest(target, method), jwt(f.member));
    }

    private CreatePaymentRequest upgradeRequest(MembershipPlan target, PaymentMethod method) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.UPGRADE);
        request.setReferenceId(target.getId());
        request.setMethod(method);
        return request;
    }

    private boolean attemptCreate(Fixture f, MembershipPlan target) {
        try {
            createUpgrade(f, target, PaymentMethod.COUNTER);
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        }
    }

    private boolean attemptPurchase(Fixture f) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.MEMBERSHIP);
        request.setReferenceId(f.sourcePlan.getId());
        request.setMethod(PaymentMethod.COUNTER);
        try {
            payments.createPayment(request, jwt(f.member));
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        }
    }

    private boolean attemptCounterSettlement(Integer paymentId, Fixture f) {
        try {
            return "SUCCESS".equals(payments.processAtCounter(paymentId, counter(true), jwt(f.receptionist)).getStatus());
        } catch (ResponseStatusException expectedAlreadySettled) {
            return false;
        } catch (IllegalArgumentException expectedAlreadyProcessed) {
            return false;
        }
    }

    private PaymentResponse settleCounter(PaymentResponse payment, Fixture f) {
        return payments.processAtCounter(payment.getId(), counter(true), jwt(f.receptionist));
    }

    private Payment legacyPendingUpgrade(Fixture f) {
        Payment payment = new Payment();
        payment.setMember(f.member);
        payment.setAmount(new BigDecimal("1000000.00"));
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.UPGRADE);
        payment.setReferenceId(f.target.getId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-LEGACY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setCreatedAt(LocalDateTime.now(clock));
        payment = paymentRows.saveAndFlush(payment);
        MembershipUpgrade attempt = new MembershipUpgrade();
        attempt.setPayment(payment);
        attempt.setSourceMembership(f.source);
        attempt.setTargetPlan(f.target);
        attempt.setRemainingDays(20);
        attempt.setExpiresAt(LocalDateTime.now(clock).plusDays(1));
        attempt.setCreatedAt(LocalDateTime.now(clock));
        upgrades.saveAndFlush(attempt);
        return payment;
    }

    private Membership activeMembership(User member) {
        return memberships.findByMemberId(member.getId()).stream()
                .filter(row -> "ACTIVE".equals(row.getStatus())).max(Comparator.comparing(Membership::getId)).orElseThrow();
    }

    private CounterPaymentRequest counter(boolean success) {
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(success);
        return request;
    }

    private Jwt jwt(User user) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("phase4c-test-token")
                .header("alg", "none")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("role", user.getRole()).build();
    }

    private void assertConflict(Runnable operation) {
        assertThrows(ResponseStatusException.class, operation::run);
    }

    private <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<T> a = executor.submit(() -> { ready.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("start gate timeout"); return first.call(); });
            Future<T> b = executor.submit(() -> { ready.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("start gate timeout"); return second.call(); });
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both SQL Server workers must reach the barrier");
            release.countDown();
            return List.of(a.get(35, TimeUnit.SECONDS), b.get(35, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Concurrent SQL Server workers must stop");
        }
    }

    private Map<String, String> query(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return params;
    }

    private void cleanupOwnedFixtures() {
        List<Integer> userIds = jdbc.queryForList("SELECT id FROM dbo.users WHERE email LIKE ?", Integer.class,
                prefix + "-%" + EMAIL_SUFFIX);
        List<Integer> planIds = jdbc.queryForList("SELECT id FROM dbo.membership_plan WHERE name LIKE ?", Integer.class,
                prefix + "-%");
        if (userIds.isEmpty() && planIds.isEmpty()) return;
        String users = prefix + "-%" + EMAIL_SUFFIX;
        String plans = prefix + "-%";
        jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='MEMBERSHIP_UPGRADE_PATH' AND entity_id IN "
                + "(SELECT CONVERT(varchar(64),id) FROM dbo.membership_plan_upgrade WHERE from_plan_id IN "
                + "(SELECT id FROM dbo.membership_plan WHERE name LIKE ?) OR to_plan_id IN "
                + "(SELECT id FROM dbo.membership_plan WHERE name LIKE ?))", plans, plans);
        jdbc.update("DELETE FROM dbo.notification WHERE user_id IN (SELECT id FROM dbo.users WHERE email LIKE ?)", users);
        jdbc.update("DELETE FROM dbo.membership_upgrade WHERE payment_id IN "
                + "(SELECT id FROM dbo.payment WHERE member_id IN (SELECT id FROM dbo.users WHERE email LIKE ?)) "
                + "OR source_membership_id IN (SELECT id FROM dbo.membership WHERE member_id IN "
                + "(SELECT id FROM dbo.users WHERE email LIKE ?))", users, users);
        jdbc.update("DELETE FROM dbo.membership WHERE member_id IN (SELECT id FROM dbo.users WHERE email LIKE ?)", users);
        jdbc.update("DELETE FROM dbo.payment WHERE member_id IN (SELECT id FROM dbo.users WHERE email LIKE ?)", users);
        jdbc.update("DELETE FROM dbo.membership_plan_upgrade WHERE from_plan_id IN "
                + "(SELECT id FROM dbo.membership_plan WHERE name LIKE ?) OR to_plan_id IN "
                + "(SELECT id FROM dbo.membership_plan WHERE name LIKE ?)", plans, plans);
        jdbc.update("DELETE FROM dbo.membership_plan WHERE name LIKE ?", plans);
        jdbc.update("DELETE FROM dbo.users WHERE email LIKE ?", users);
    }

    private record Fixture(User member, User admin, User receptionist, MembershipPlan sourcePlan,
            MembershipPlan target, Membership source, Integer pathId) { }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary TestClock phase4cClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.EPOCH);
        void setInstant(Instant value) { instant.set(value); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return VN; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }
}
