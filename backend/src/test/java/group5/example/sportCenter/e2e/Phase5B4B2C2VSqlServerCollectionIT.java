package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipRenewalCollectionPersistenceService;
import group5.example.sportCenter.service.MembershipRenewalCollectionService;
import group5.example.sportCenter.service.MembershipRenewalPaymentAttemptService;
import group5.example.sportCenter.service.MembershipRenewalSettlementService;
import group5.example.sportCenter.service.RenewalPaymentAdapterService;
import group5.example.sportCenter.service.RenewalSettlementEvidence;
import group5.example.sportCenter.service.RenewalSettlementOutcome;
import group5.example.sportCenter.service.RenewalSettlementResult;

/** Focused service/persistence checks against the exact, owner-authorized Renewal rehearsal clone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=" + Phase5B4B2C1BHibernateValidationIT.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "spring.main.web-application-type=servlet",
        "sportcenter.scheduling.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "app.payment.callback-secret=phase5b42cv-local-test-only",
        "app.payment.callback-base-url=http://localhost:8080/api/payments/online/callback"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase5B4B2C1BHibernateValidationIT.CloneGuard.class)
@Import(Phase5B4B2C2VSqlServerCollectionIT.ClockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase5B4B2C2VSqlServerCollectionIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDate.now(VN).atTime(10, 0);
    private static final BigDecimal PRICE = new BigDecimal("750000.00");
    private static final String PREFIX = "P5B42CV-" + UUID.randomUUID().toString().substring(0, 8);
    private static final List<String> PROTECTED = List.of("membership", "payment", "membership_upgrade",
            "membership_cancellation_request");

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipRenewalIntentRepository intents;
    @Autowired PaymentRepository payments;
    @Autowired MembershipRenewalPaymentAttemptService attempts;
    @Autowired MembershipRenewalCollectionPersistenceService persistence;
    @MockitoSpyBean MembershipRenewalCollectionService collections;
    @Autowired MembershipRenewalSettlementService settlement;
    @Autowired RenewalPaymentAdapterService adapter;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TestClock clock;
    @MockitoBean PaymentGateway gateway;
    @MockitoSpyBean MembershipRepository membershipSpy;

    private final Map<String, Map<String, List<String>>> protectedBaseline = new HashMap<>();
    private final List<Fixture> fixtures = new ArrayList<>();

    @BeforeAll
    void captureProtectedRowsBeforeAnyFixtureWrites() throws Exception {
        Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        cleanupInterruptedVerificationFixtures();
        cleanupMarkedFixturesFromThisVerification();
        try (Connection connection = dataSource.getConnection()) {
            Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyIdentity(connection);
            for (String table : PROTECTED) protectedBaseline.put(table, snapshotTable(connection, table));
        }
        System.out.println("PHASE5B4B2C2V_BASELINE_CAPTURED protectedRows=" + counts(protectedBaseline));
    }

    @BeforeEach
    void reverifyCloneBeforeFixtureWrites() throws Exception {
        Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        clock.setInstant(NOW.atZone(VN).toInstant());
        reset(gateway, membershipSpy, collections);
    }

    @AfterEach
    void cleanupOnlyFixturesCreatedByThisClass() {
        reset(membershipSpy);
        for (int i = fixtures.size() - 1; i >= 0; i--) cleanup(fixtures.get(i));
        fixtures.clear();
    }

    @AfterAll
    void compareCompleteProtectedRowsAndConfirmNoFixtureRemains() throws Exception {
        if (protectedBaseline.size() != PROTECTED.size()) return;
        Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        Map<String, Map<String, List<String>>> after = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyIdentity(connection);
            for (String table : PROTECTED) after.put(table, snapshotTable(connection, table));
        }
        List<String> differences = new ArrayList<>();
        for (String table : PROTECTED) {
            if (!protectedBaseline.get(table).equals(after.get(table))) {
                differences.add(table + " baselineKeys=" + protectedBaseline.get(table).keySet()
                        + " currentKeys=" + after.get(table).keySet());
            }
        }
        assertTrue(differences.isEmpty(), "Protected clone business rows changed during verification: " + differences);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE email LIKE ?", Integer.class,
                PREFIX + "%@phase5b42cv.test"));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.notification n
                JOIN dbo.users u ON u.id=n.user_id
                WHERE u.email LIKE ? AND u.name LIKE ?
                """, Integer.class, PREFIX + "%@phase5b42cv.test", PREFIX + "%"));
        System.out.println("PHASE5B4B2C2V_BASELINE_MATCH protectedRows=" + counts(after));
    }

    @Test
    void successfulIpnDuplicateAndConcurrentCallbackCreateOneSuccessor() throws Exception {
        Fixture fixture = fixture(PaymentMethod.ONLINE, true);
        GatewayCallback callback = validCallback(fixture.payment, "991234500001", NOW);
        when(gateway.verifyCallback(any())).thenReturn(Optional.of(callback));
        AtomicReference<RenewalSettlementResult> detailedResult = new AtomicReference<>();
        ConcurrentLinkedQueue<RenewalSettlementResult> observedResults = new ConcurrentLinkedQueue<>();
        doAnswer(invocation -> {
            RenewalSettlementResult result = (RenewalSettlementResult) invocation.callRealMethod();
            detailedResult.set(result);
            observedResults.add(result);
            return result;
        }).when(collections).recordAndSettle(any());

        var first = adapter.processVnpayIpnIfRenewal(Map.of("fixture", "signed-by-test-gateway"));
        assertTrue(first.isPresent());
        assertEquals("SUCCESS", payments.findById(fixture.payment.getId()).orElseThrow().getStatus().name(),
                "Settlement returned " + detailedResult.get());
        assertEquals(1L, memberships.findByRenewalIntent_Id(fixture.intent.getId()).stream().count());

        var repeated = adapter.processVnpayIpnIfRenewal(Map.of("fixture", "duplicate-ipn"));
        assertTrue(repeated.isPresent());
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.ALREADY_PROCESSED,
                repeated.get().status());
        assertEquals(1L, memberships.findByRenewalIntent_Id(fixture.intent.getId()).stream().count());

        Fixture concurrent = fixture(PaymentMethod.ONLINE, true);
        observedResults.clear();
        when(gateway.verifyCallback(any())).thenReturn(Optional.of(validCallback(
                concurrent.payment, "991234500002", NOW)));
        var outcomes = race(() -> adapter.processVnpayIpnIfRenewal(Map.of("fixture", "concurrent-ipn")),
                () -> adapter.processVnpayIpnIfRenewal(Map.of("fixture", "concurrent-ipn")));
        assertTrue(outcomes.stream().allMatch(value -> value instanceof Optional<?> result && result.isPresent()));
        assertTrue(observedResults.stream().allMatch(result -> result.outcome() == RenewalSettlementOutcome.ACCEPTED
                || result.outcome() == RenewalSettlementOutcome.ALREADY_ACCEPTED),
                "Concurrent same-collection callbacks returned " + observedResults);
        assertEquals(PaymentStatus.SUCCESS, payments.findById(concurrent.payment.getId()).orElseThrow().getStatus(),
                "Concurrent settlement details " + observedResults);
        assertEquals(1L, memberships.findByRenewalIntent_Id(concurrent.intent.getId()).stream().count());
        assertEquals(RenewalIntentStatus.SETTLED,
                intents.findById(concurrent.intent.getId()).orElseThrow().getIntentStatus());
    }

    @Test
    void conflictingProviderFactsPreserveFirstCollectionAndRequireReconciliation() {
        Fixture fixture = fixture(PaymentMethod.ONLINE, true);
        var first = online(fixture, "991234500003", NOW);
        assertEquals("RECORDED", persistence.recordFirstCollection(first).status().name());
        var conflict = collections.recordAndSettle(online(fixture, "991234500004", NOW));

        Payment stored = payments.findById(fixture.payment.getId()).orElseThrow();
        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, conflict.outcome());
        assertEquals("991234500003", stored.getRenewalProviderTransactionReference());
        assertNotNull(stored.getRenewalCollectionConflictDetectedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
        assertNotEquals(PaymentStatus.REFUNDED, stored.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(fixture.intent.getId()).isEmpty());
    }

    @Test
    void sqlServerFilteredProviderReferenceIndexRejectsCompetingCollections() throws Exception {
        Fixture left = fixture(PaymentMethod.ONLINE, true);
        Fixture right = fixture(PaymentMethod.ONLINE, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> raceIndexUpdate(left.payment.getId(), ready, start));
            Future<Boolean> second = pool.submit(() -> raceIndexUpdate(right.payment.getId(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean firstWon = first.get(20, TimeUnit.SECONDS);
            boolean secondWon = second.get(20, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon, "Exactly one concurrent provider reference must win the filtered index");
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.payment
                    WHERE renewal_provider_transaction_reference = '991234599999'
                    """, Integer.class));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void collectionEvidenceSurvivesSettlementRollbackAndRecordedCollectionCanRecover() {
        Fixture fixture = fixture(PaymentMethod.ONLINE, true);
        var evidence = online(fixture, "991234500005", NOW);
        doThrow(new IllegalStateException("controlled SQL Server successor failure"))
                .when(membershipSpy).saveAndFlush(any(Membership.class));

        assertThrows(IllegalStateException.class, () -> collections.recordAndSettle(evidence));
        Payment afterRollback = payments.findById(fixture.payment.getId()).orElseThrow();
        assertEquals("991234500005", afterRollback.getRenewalProviderTransactionReference());
        assertNotNull(afterRollback.getRenewalCollectionVerifiedAt());
        assertEquals(PaymentStatus.PENDING, afterRollback.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(fixture.intent.getId()).isEmpty());

        reset(membershipSpy);
        var recovered = collections.resumeRecordedSettlement(fixture.payment.getId());
        assertEquals(RenewalSettlementOutcome.ACCEPTED, recovered.outcome());
        assertEquals(1L, memberships.findByRenewalIntent_Id(fixture.intent.getId()).stream().count());
    }

    @Test
    void concurrentAttemptCreationHasOneWinnerAndRecordedCollectionBlocksRetry() throws Exception {
        Fixture fixture = fixture(PaymentMethod.ONLINE, false);
        var creationOutcomes = race(() -> attempts.createAttempt(fixture.intent.getId(), PaymentMethod.ONLINE),
                () -> attempts.createAttempt(fixture.intent.getId(), PaymentMethod.ONLINE));
        assertEquals(1L, creationOutcomes.stream().filter(value -> value instanceof Payment).count());
        assertEquals(1L, creationOutcomes.stream().filter(Throwable.class::isInstance).count());
        Payment attempt = payments.findByTransactionCode(
                ((Payment) creationOutcomes.stream().filter(Payment.class::isInstance).findFirst().orElseThrow())
                        .getTransactionCode()).orElseThrow();
        fixture.payment = attempt;

        var recorded = persistence.recordFirstCollection(online(fixture, "991234500006", NOW));
        assertEquals("RECORDED", recorded.status().name());
        assertThrows(RuntimeException.class,
                () -> attempts.createAttempt(fixture.intent.getId(), PaymentMethod.ONLINE));
        assertTrue(payments.findRecordedRenewalCollectionsPendingReview().contains(attempt.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=?",
                Integer.class, fixture.intent.getId()));
    }

    @Test
    void counterConfirmationUsesStaffAndServerTimeAndIsIdempotent() {
        Fixture fixture = fixture(PaymentMethod.COUNTER, true);
        User staff = staff();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(staff.getId().toString()).build();
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(true);
        request.setNote("Phase verification");

        assertTrue(adapter.processCounterIfRenewal(fixture.payment.getId(), request, jwt).isPresent());
        Payment settled = payments.findById(fixture.payment.getId()).orElseThrow();
        assertEquals(NOW, settled.getPaidAt());
        assertEquals(staff.getId(), settled.getProcessedBy().getId());
        assertEquals("SERVER_CONFIRMATION", settled.getRenewalPaidTimeEvidenceType().name());
        assertTrue(adapter.processCounterIfRenewal(fixture.payment.getId(), request, jwt).isPresent());
        assertEquals(1L, memberships.findByRenewalIntent_Id(fixture.intent.getId()).stream().count());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(fixture.payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancellationMalformedSignedCollectionAndExactDeadlinesNeverCreateSuccessor() {
        Fixture cancelled = fixture(PaymentMethod.ONLINE, true);
        attempts.cancelAttempt(cancelled.payment.getId(), cancelled.member.getId());
        var cancelledCollection = collections.recordAndSettle(online(cancelled, "991234500007", NOW));
        assertEquals(RenewalSettlementOutcome.ATTEMPT_CANCELLED, cancelledCollection.outcome());
        assertNoSuccessorAndRefundRequired(cancelled);

        Fixture malformed = fixture(PaymentMethod.ONLINE, true);
        GatewayCallback incomplete = new GatewayCallback(malformed.payment.getTransactionCode(), true, PRICE, null,
                "not-a-provider-time", "991234500008", "VND", true, null,
                GatewayCallback.PaidTimeStatus.MALFORMED);
        when(gateway.verifyCallback(any())).thenReturn(Optional.of(incomplete));
        var incompleteResult = adapter.processVnpayIpnIfRenewal(Map.of("fixture", "signed-incomplete"));
        assertTrue(incompleteResult.isPresent());
        assertNoSuccessorAndRefundRequired(malformed);
        assertTrue(payments.findRecordedRenewalCollectionsPendingReview().contains(malformed.payment.getId()));

        Fixture late = fixture(PaymentMethod.ONLINE, true);
        LocalDateTime attemptDeadline = late.payment.getAttemptValidUntil();
        var atAttemptDeadline = collections.recordAndSettle(online(late, "991234500009", attemptDeadline));
        assertEquals(RenewalSettlementOutcome.PAYMENT_LATE, atAttemptDeadline.outcome());
        assertNoSuccessorAndRefundRequired(late);

        Fixture intentDeadline = fixture(PaymentMethod.COUNTER, true);
        clock.setInstant(intentDeadline.intent.getValidUntil().atZone(VN).toInstant());
        User staff = staff();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(staff.getId().toString()).build();
        CounterPaymentRequest request = new CounterPaymentRequest(); request.setSuccess(true);
        adapter.processCounterIfRenewal(intentDeadline.payment.getId(), request, jwt);
        assertNoSuccessorAndRefundRequired(intentDeadline);
    }

    private Fixture fixture(PaymentMethod method, boolean withPayment) {
        LocalDate today = LocalDate.now(VN);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User member = user("member", suffix + "@phase5b42cv.test", "MEMBER");
        Fixture fixture = new Fixture(member);
        fixtures.add(fixture);
        MembershipPlan plan = new MembershipPlan();
        plan.setName(PREFIX + "-Plan-" + suffix.substring(0, 6));
        plan.setDescription("Verification-only Renewal plan");
        plan.setPrice(PRICE); plan.setDurationMonths(2);
        plan.setCourseDiscountPercent(new BigDecimal("10.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(true); plan.setAllowOneOnOneCoaching(true); plan.setStatus("ACTIVE");
        plan = plans.saveAndFlush(plan);
        fixture.plan = plan;

        LocalDate sourceEnd = today.plusDays(2);
        Membership source = new Membership(); source.setMember(member); source.setPlan(plan);
        source.setAutoRenew(true); source.setStatus("ACTIVE");
        source.setStartDate(today.minusDays(28)); source.setEndDate(sourceEnd);
        source = memberships.saveAndFlush(source);
        fixture.source = source;

        MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setSourceMembership(source); intent.setTargetPlan(plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true); intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(NOW); intent.setValidFrom(sourceEnd.minusDays(3).atStartOfDay());
        intent.setValidUntil(sourceEnd.atStartOfDay()); intent.setQuoteCapturedAt(NOW);
        intent.setSourceEndDateSnapshot(sourceEnd); intent.setSuccessorStartDate(sourceEnd);
        intent.setSuccessorEndDate(sourceEnd.plusMonths(2));
        intent.setPlanNameSnapshot(plan.getName()); intent.setPlanDescriptionSnapshot(plan.getDescription());
        intent.setPlanPriceSnapshot(PRICE); intent.setQuotedAmount(PRICE); intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(new BigDecimal("10.00"));
        intent.setPtDiscountPercentSnapshot(new BigDecimal("5.00"));
        intent.setAllowPtPlanSnapshot(true); intent.setAllowOneOnOneSnapshot(true);
        intent = intents.saveAndFlush(intent);
        fixture.intent = intent;

        if (withPayment) fixture.payment = payment(fixture, method, NOW.plusMinutes(15));
        return fixture;
    }

    private Payment payment(Fixture fixture, PaymentMethod method, LocalDateTime deadline) {
        Payment payment = new Payment(); payment.setMember(fixture.member); payment.setAmount(PRICE);
        payment.setMethod(method); payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(fixture.plan.getId()); payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-REN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        payment.setCreatedAt(NOW); payment.setAttemptValidUntil(method == PaymentMethod.COUNTER
                ? fixture.intent.getValidUntil() : deadline);
        payment.setRenewalIntent(fixture.intent);
        return payments.saveAndFlush(payment);
    }

    private User staff() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User staff = user("staff", suffix + "@phase5b42cv.test", "RECEPTIONIST");
        for (Fixture fixture : fixtures) fixture.staff = staff;
        return staff;
    }

    private User user(String label, String email, String role) {
        User user = new User(); user.setName(PREFIX + "-" + label); user.setEmail(email);
        user.setPasswordHash("verification-only-not-a-login-secret");
        user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private GatewayCallback validCallback(Payment payment, String reference, LocalDateTime paidAt) {
        return new GatewayCallback(payment.getTransactionCode(), true, PRICE, null, "verified",
                reference, "VND", true, paidAt, GatewayCallback.PaidTimeStatus.VALID);
    }

    private RenewalSettlementEvidence online(Fixture fixture, String reference, LocalDateTime paidAt) {
        return RenewalSettlementEvidence.verifiedOnlineCollection(fixture.intent.getId(), fixture.payment.getId(),
                PRICE, "VND", reference, paidAt, "VNPAY_SIGNATURE_VERIFIED", reference);
    }

    private void assertNoSuccessorAndRefundRequired(Fixture fixture) {
        assertTrue(memberships.findByRenewalIntent_Id(fixture.intent.getId()).isEmpty());
        Payment stored = payments.findById(fixture.payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
        assertNotEquals(PaymentStatus.REFUNDED, stored.getStatus());
    }

    private boolean raceIndexUpdate(Integer paymentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyIdentity(connection);
            connection.setAutoCommit(false);
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Index race did not start");
            try (var statement = connection.prepareStatement("""
                    UPDATE dbo.payment SET renewal_provider_transaction_reference=?,
                        renewal_verified_collection_amount=amount,
                        renewal_verified_collection_currency='VND',
                        renewal_paid_time_evidence_type='PROVIDER_VERIFIED',
                        renewal_collection_verified_at=?, paid_at=?
                    WHERE id=?
                    """)) {
                statement.setString(1, "991234599999");
                statement.setObject(2, NOW); statement.setObject(3, NOW); statement.setInt(4, paymentId);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException conflict) {
                connection.rollback();
                if (conflict.getErrorCode() != 2601 && conflict.getErrorCode() != 2627) throw conflict;
                return false;
            }
        }
    }

    @SafeVarargs
    private final List<Object> race(ThrowingSupplier<?>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (ThrowingSupplier<?> task : tasks) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                return task.get();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<?> future : futures) {
                try { results.add(future.get(25, TimeUnit.SECONDS)); }
                catch (java.util.concurrent.ExecutionException failure) {
                    results.add(failure.getCause());
                }
            }
            return results;
        } finally {
            start.countDown(); pool.shutdownNow();
        }
    }

    private void cleanup(Fixture fixture) {
        if (fixture.intent != null && fixture.intent.getId() != null) {
            memberships.findByRenewalIntent_Id(fixture.intent.getId()).ifPresent(row -> {
                memberships.delete(row); memberships.flush();
            });
            intents.findById(fixture.intent.getId()).ifPresent(row -> {
                row.setIntentStatus(RenewalIntentStatus.CANCELLED);
                row.setSettledPayment(null);
                row.setSettledAt(null);
                row.setClosedAt(NOW);
                row.setClosedReason(group5.example.sportCenter.entity.RenewalIntentCloseReason.AUTO_RENEW_DISABLED);
                intents.saveAndFlush(row);
            });
            jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id = ?", fixture.intent.getId());
            intents.findById(fixture.intent.getId()).ifPresent(row -> intents.delete(row));
        }
        if (fixture.source != null) memberships.findById(fixture.source.getId()).ifPresent(memberships::delete);
        if (fixture.plan != null) plans.findById(fixture.plan.getId()).ifPresent(plans::delete);
        if (fixture.staff != null) {
            jdbc.update("DELETE FROM dbo.notification WHERE user_id = ?", fixture.staff.getId());
            users.findById(fixture.staff.getId()).ifPresent(users::delete);
        }
        if (fixture.member != null) {
            jdbc.update("DELETE FROM dbo.notification WHERE user_id = ?", fixture.member.getId());
            users.findById(fixture.member.getId()).ifPresent(users::delete);
        }
    }

    /**
     * The read-only inventory immediately before this verification run found no P5B42CV test identities.
     * Remove only uniquely marked fixtures left by this class if an earlier test run stopped in cleanup.
     */
    private void cleanupMarkedFixturesFromThisVerification() throws Exception {
        List<Integer> userIds = jdbc.queryForList("""
                SELECT id FROM dbo.users
                WHERE name LIKE N'P5B42CV-%' AND email LIKE N'%@phase5b42cv.test'
                ORDER BY id
                """, Integer.class);
        if (userIds.isEmpty()) return;

        List<Integer> planIds = jdbc.queryForList("""
                SELECT id FROM dbo.membership_plan WHERE name LIKE N'P5B42CV-%' ORDER BY id
                """, Integer.class);
        assertFalse(planIds.isEmpty(), "Refuse cleanup: marked test users have no marked Plans");

        String userMarks = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        String planMarks = String.join(",", java.util.Collections.nCopies(planIds.size(), "?"));
        List<Integer> sourceIds = jdbc.queryForList("SELECT id FROM dbo.membership WHERE member_id IN ("
                + userMarks + ") AND plan_id IN (" + planMarks + ") ORDER BY id", Integer.class,
                java.util.stream.Stream.concat(userIds.stream(), planIds.stream()).toArray());
        assertFalse(sourceIds.isEmpty(), "Refuse cleanup: marked test users have no marked source Memberships");
        String sourceMarks = String.join(",", java.util.Collections.nCopies(sourceIds.size(), "?"));
        List<Integer> intentIds = jdbc.queryForList("SELECT id FROM dbo.membership_renewal_intent WHERE source_membership_id IN ("
                + sourceMarks + ") ORDER BY id", Integer.class, sourceIds.toArray());
        String intentIdMarks = intentIds.isEmpty() ? "-1"
                : String.join(",", java.util.Collections.nCopies(intentIds.size(), "?"));

        // Prove there are no unrelated user references before deleting any marked User.
        try (Connection connection = dataSource.getConnection()) {
            Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyIdentity(connection);
            try (ResultSet foreignKeys = connection.getMetaData().getExportedKeys(null, "dbo", "users")) {
                while (foreignKeys.next()) {
                    String table = foreignKeys.getString("FKTABLE_NAME");
                    String column = foreignKeys.getString("FKCOLUMN_NAME");
                    if ("notification".equalsIgnoreCase(table) && "user_id".equalsIgnoreCase(column)) continue;
                    if ("membership".equalsIgnoreCase(table) && "member_id".equalsIgnoreCase(column)) {
                        Integer unrelated = jdbc.queryForObject("""
                                SELECT COUNT(*) FROM dbo.membership
                                WHERE member_id IN (""" + userMarks + ") AND plan_id NOT IN (" + planMarks
                                + ") AND COALESCE(renewal_intent_id, -1) NOT IN (" + intentIdMarks + ")",
                                Integer.class, java.util.stream.Stream.concat(
                                        java.util.stream.Stream.concat(userIds.stream(), planIds.stream()),
                                        intentIds.stream()).toArray());
                        assertEquals(0, unrelated, "Refuse cleanup: marked users have unrelated Membership rows");
                        continue;
                    }
                    if ("payment".equalsIgnoreCase(table)
                            && ("member_id".equalsIgnoreCase(column) || "processed_by".equalsIgnoreCase(column))) {
                        Integer unrelated = jdbc.queryForObject("""
                                SELECT COUNT(*) FROM dbo.payment WHERE (member_id IN (""" + userMarks
                                + ") OR processed_by IN (" + userMarks + ")) AND COALESCE(renewal_intent_id, -1) NOT IN ("
                                + intentIdMarks + ")",
                                Integer.class, java.util.stream.Stream.concat(
                                        java.util.stream.Stream.concat(userIds.stream(), userIds.stream()),
                                        intentIds.stream()).toArray());
                        assertEquals(0, unrelated, "Refuse cleanup: marked users have unrelated Payment rows");
                        continue;
                    }
                    String sql = "SELECT COUNT(*) FROM dbo.[" + table + "] WHERE [" + column + "] IN (" + userMarks + ")";
                    Integer references = jdbc.queryForObject(sql, Integer.class, userIds.toArray());
                    assertEquals(0, references, "Refuse cleanup: marked users are referenced by " + table + "." + column);
                }
            }
        }

        try {
            Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        } catch (Exception verificationFailure) {
            throw new IllegalStateException("Clone identity recheck failed before marked-fixture cleanup", verificationFailure);
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (!intentIds.isEmpty()) {
            String intentMarks = String.join(",", java.util.Collections.nCopies(intentIds.size(), "?"));
            jdbc.update("DELETE FROM dbo.membership WHERE renewal_intent_id IN (" + intentMarks + ")", intentIds.toArray());
            String closeIntentsSql = """
                    UPDATE dbo.membership_renewal_intent
                    SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL,
                        closed_at=?, closed_reason='AUTO_RENEW_DISABLED'
                    WHERE id IN (""" + intentMarks + ")";
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(closeIntentsSql);
                statement.setTimestamp(1, java.sql.Timestamp.valueOf(NOW));
                for (int i = 0; i < intentIds.size(); i++) statement.setInt(i + 2, intentIds.get(i));
                return statement;
            });
            jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id IN (" + intentMarks + ")", intentIds.toArray());
            jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE id IN (" + intentMarks + ")", intentIds.toArray());
            }
            jdbc.update("DELETE FROM dbo.membership WHERE member_id IN (" + userMarks + ") AND plan_id IN ("
                    + planMarks + ")", java.util.stream.Stream.concat(userIds.stream(), planIds.stream()).toArray());
            for (Integer userId : userIds) jdbc.update("DELETE FROM dbo.notification WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM dbo.membership_plan WHERE id IN (" + planMarks + ")", planIds.toArray());
            jdbc.update("DELETE FROM dbo.users WHERE id IN (" + userMarks + ")", userIds.toArray());
        });
    }

    /** Removes only the exact uniquely marked rows left by the immediately preceding interrupted run. */
    private void cleanupInterruptedVerificationFixtures() {
        String interruptedPrefix = "P5B42CV-d9db5527-%";
        List<Integer> userIds = jdbc.queryForList("""
                SELECT id FROM dbo.users WHERE name LIKE ? AND email LIKE N'%@phase5b42cv.test'
                """, Integer.class, interruptedPrefix);
        if (userIds.isEmpty()) return;
        assertEquals(List.of(1016, 1017, 1018, 1019, 1020), userIds,
                "Refuse cleanup if the interrupted-run owner set differs from the verified inventory");
        List<Integer> intentIds = jdbc.queryForList("""
                SELECT ri.id FROM dbo.membership_renewal_intent ri
                JOIN dbo.membership src ON src.id=ri.source_membership_id
                JOIN dbo.users u ON u.id=src.member_id
                WHERE u.name LIKE ? AND u.email LIKE N'%@phase5b42cv.test' ORDER BY ri.id
                """, Integer.class, interruptedPrefix);
        assertEquals(List.of(2, 3, 4, 5), intentIds, "Refuse cleanup if marked Intent ownership differs");
        assertEquals(List.of(2004, 2005, 2006, 2007), jdbc.queryForList("""
                SELECT id FROM dbo.membership_plan WHERE name LIKE ? ORDER BY id
                """, Integer.class, interruptedPrefix), "Refuse cleanup if marked Plan ownership differs");
        assertEquals(List.of(5016, 5018, 5020, 5021, 5022), jdbc.queryForList("""
                SELECT id FROM dbo.membership WHERE
                    (member_id IN (1016,1017,1019,1020) AND plan_id IN (2004,2005,2006,2007))
                    OR renewal_intent_id IN (2,3,4,5) ORDER BY id
                """, Integer.class), "Refuse cleanup if marked Membership ownership differs");
        assertEquals(List.of(11046, 11047, 11048, 11049), jdbc.queryForList("""
                SELECT id FROM dbo.payment WHERE renewal_intent_id IN (2,3,4,5) ORDER BY id
                """, Integer.class), "Refuse cleanup if marked Payment ownership differs");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                Phase5B4B2C1BHibernateValidationIT.CloneGuard.verifyRuntime(dataSource, environment, context);
            } catch (Exception verificationFailure) {
                throw new IllegalStateException("Clone identity recheck failed before owned-fixture cleanup",
                        verificationFailure);
            }
            jdbc.update("DELETE FROM dbo.membership WHERE renewal_intent_id IN (2,3,4,5)");
            jdbc.update("""
                    UPDATE dbo.membership_renewal_intent
                    SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL,
                        closed_at=?, closed_reason='AUTO_RENEW_DISABLED'
                    WHERE id IN (2,3,4,5)
                    """, NOW);
            jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id IN (2,3,4,5)");
            jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE id IN (2,3,4,5)");
            jdbc.update("DELETE FROM dbo.membership WHERE id IN (5016,5018,5020,5022)");
            jdbc.update("DELETE FROM dbo.membership_plan WHERE id IN (2004,2005,2006,2007)");
            jdbc.update("DELETE FROM dbo.users WHERE id IN (1016,1017,1018,1019,1020)");
        });
    }

    private Map<String, List<String>> snapshotTable(Connection connection, String table) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<Short, String> keys = new java.util.TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, "dbo", table)) {
            while (rows.next()) keys.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME"));
        }
        if (keys.isEmpty()) throw new IllegalStateException("Protected table lacks a primary key: " + table);
        String orderBy = String.join(",", keys.values().stream().map(k -> "[" + k + "]").toList());
        Map<String, List<String>> snapshot = new java.util.TreeMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM dbo.[" + table + "] ORDER BY " + orderBy)) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                List<String> values = new ArrayList<>(columns);
                for (int i = 1; i <= columns; i++) values.add(canonical(rows.getObject(i)));
                String key = String.join("|", keys.values().stream()
                        .map(name -> values.get(columnIndex(rows, name))).toList());
                if (snapshot.put(key, List.copyOf(values)) != null) {
                    throw new IllegalStateException("Duplicate primary key in protected snapshot: " + table);
                }
            }
        }
        return snapshot;
    }

    private int columnIndex(ResultSet rows, String name) {
        try {
            for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                if (name.equalsIgnoreCase(rows.getMetaData().getColumnLabel(i))) return i - 1;
            }
            throw new IllegalStateException("Primary key column missing from row: " + name);
        } catch (SQLException error) { throw new IllegalStateException(error); }
    }

    private String canonical(Object value) {
        if (value == null) return "<NULL>";
        if (value instanceof byte[] bytes) return "B64:" + Base64.getEncoder().encodeToString(bytes);
        if (value instanceof BigDecimal decimal) return "DEC:" + decimal.toPlainString();
        if (value instanceof java.sql.Timestamp timestamp) return "TS:" + timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return "D:" + date.toLocalDate();
        if (value instanceof java.sql.Time time) return "T:" + time.toLocalTime();
        if (value instanceof Instant instant) return "I:" + instant;
        if (value instanceof String string) return "S:" + string;
        return value.getClass().getName() + ":" + value;
    }

    private Map<String, Integer> counts(Map<String, Map<String, List<String>>> rows) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        rows.forEach((table, content) -> counts.put(table, content.size()));
        return counts;
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary TestClock renewalVerificationClock() {
            return new TestClock(NOW.atZone(VN).toInstant());
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;
        TestClock(Instant initial) { this.instant = new AtomicReference<>(initial); }
        void setInstant(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return VN; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }

    private static final class Fixture {
        final User member;
        MembershipPlan plan;
        Membership source;
        MembershipRenewalIntent intent;
        Payment payment;
        User staff;
        Fixture(User member) { this.member = member; }
    }

    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}
