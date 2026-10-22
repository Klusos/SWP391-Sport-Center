package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.CheckoutProvider;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentCheckoutAttempt;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.scheduler.MembershipScheduler;
import group5.example.sportCenter.service.CourseRegistrationExpirationService;
import group5.example.sportCenter.service.MembershipRenewalService;
import group5.example.sportCenter.service.MembershipService;
import group5.example.sportCenter.service.PaymentService;
import group5.example.sportCenter.service.PtBookingExpirationService;
import group5.example.sportCenter.service.RenewalSettlementResult;
import group5.example.sportCenter.service.RenewalSettlementService;
import group5.example.sportCenter.service.VerifiedRenewalCapture;

/**
 * Explicit test-only settlement rehearsal for an exactly configured development database
 * or disposable clone. It is excluded from ordinary verify and never deletes fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("renewal-sqlserver")
class RenewalSettlementSqlServerIT {
    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PRICE = "300000.00";
    private static final String RUN_MARKER = "RNLIT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();
    private static volatile RunMode runMode;

    @Autowired RenewalSettlementService settlement;
    @Autowired MembershipRenewalService renewalService;
    @Autowired MembershipService membershipService;
    @Autowired PaymentService paymentService;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired PaymentRepository payments;
    @Autowired PaymentCheckoutAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;

    // Scheduled callbacks are replaced with no-ops. Tests invoke only the renewal
    // service methods they need, so background jobs cannot mutate unrelated rows.
    @MockitoBean MembershipScheduler membershipScheduler;
    @MockitoBean CourseRegistrationExpirationService courseRegistrationExpirationService;
    @MockitoBean PtBookingExpirationService ptBookingExpirationService;

    @BeforeAll
    static void failClosedBeforeSpringConnects() throws Exception {
        if (!"true".equalsIgnoreCase(System.getProperty("sportcenter.renewal.sqlserver.enabled"))) {
            throw new IllegalStateException("Use the dedicated renewal-sqlserver-rehearsal Maven profile");
        }
        boolean developmentOptIn = isTrueEnv("RENEWAL_SQLSERVER_DEVELOPMENT_OPT_IN");
        boolean disposableOptIn = isTrueEnv("RENEWAL_SQLSERVER_DISPOSABLE_OPT_IN");
        if (developmentOptIn == disposableOptIn) {
            throw new IllegalStateException("Set exactly one of the development or disposable SQL Server opt-ins to true");
        }
        runMode = developmentOptIn ? RunMode.DEVELOPMENT : RunMode.DISPOSABLE;
        String url = requiredEnv("RENEWAL_SQLSERVER_JDBC_URL");
        String expectedUrl = requiredEnv("RENEWAL_SQLSERVER_EXPECTED_JDBC_URL");
        String expectedServer = requiredEnv("RENEWAL_SQLSERVER_EXPECTED_SERVER");
        String expectedDatabase = requiredEnv("RENEWAL_SQLSERVER_EXPECTED_DATABASE");
        String username = requiredEnv("RENEWAL_SQLSERVER_USERNAME");
        String password = requiredEnv("RENEWAL_SQLSERVER_PASSWORD");
        requiredEnv("RENEWAL_SQLSERVER_JWT_SECRET");

        if (!url.equals(expectedUrl) || expectedServer.isBlank() || expectedDatabase.isBlank()
                || !url.toLowerCase().contains("databasename=" + expectedDatabase.toLowerCase())) {
            throw new IllegalStateException("Configured JDBC target does not exactly match the supplied disposable target");
        }
        if (runMode == RunMode.DEVELOPMENT && !"SportCenterDB".equals(expectedDatabase)) {
            throw new IllegalStateException("Development rehearsal mode is restricted to the exact SportCenterDB database name");
        }
        rejectApplicationDefaultUrl(url, runMode == RunMode.DEVELOPMENT);

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement();
                    ResultSet identity = statement.executeQuery("SELECT @@SERVERNAME, DB_NAME(), "
                            + "CONVERT(varchar(20), DATABASEPROPERTYEX(DB_NAME(), 'Status'))")) {
                if (!identity.next()
                        || !expectedServer.equalsIgnoreCase(identity.getString(1))
                        || !expectedDatabase.equals(identity.getString(2))
                        || !"ONLINE".equalsIgnoreCase(identity.getString(3))) {
                    throw new IllegalStateException("Connected SQL Server identity does not match the supplied disposable target");
                }
                verifyCheckpointSchema(connection);
                System.out.println("Renewal SQL Server rehearsal mode=" + runMode
                        + ", runMarker=" + RUN_MARKER + ", fixture records are persistent TEST DATA");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not print URLs, usernames, or driver diagnostics that may contain connection details.
            throw new IllegalStateException("Read-only SQL Server target/schema preflight failed; no test fixtures were created");
        }
    }

    @Test
    void concurrentReplayOfSameVerifiedAttemptCreatesOneMembership() throws Exception {
        Fixture f = fixture(PaymentMethod.ONLINE, 2);
        VerifiedRenewalCapture capture = capture(f, "same-replay");
        CountDownLatch start = new CountDownLatch(1);
        List<RenewalSettlementResult> results = concurrently(start,
                () -> settlement.settleVerifiedOnlineCapture(capture),
                () -> settlement.settleVerifiedOnlineCapture(capture));
        assertEquals(1, results.stream().filter(r -> r.outcome() == RenewalSettlementResult.Outcome.APPLIED).count());
        assertEquals(1, results.stream().filter(r -> r.outcome() == RenewalSettlementResult.Outcome.ALREADY_APPLIED).count());
        assertEquals(1, linkedMembershipCount(f.payment().getId()));
        report("TEST DATA appliedMembership=" + results.stream().filter(r -> r.membershipId() != null)
                .map(RenewalSettlementResult::membershipId).findFirst().orElseThrow()
                + ", parentPayment=" + f.payment().getId());
        labelPaymentAsTestData(f.payment().getId());
    }

    @Test
    void twoCapturedAttemptsForOnePaymentHaveOneWinnerAndPreserveBothEvidenceRows() throws Exception {
        Fixture f = fixture(PaymentMethod.ONLINE, 2);
        PaymentCheckoutAttempt second = saveAttempt(f.payment());
        CountDownLatch start = new CountDownLatch(1);
        List<RenewalSettlementResult> results = concurrently(start,
                () -> settlement.settleVerifiedOnlineCapture(capture(f, "two-attempt-a")),
                () -> settlement.settleVerifiedOnlineCapture(capture(f, second, "two-attempt-b")));
        assertEquals(1, results.stream().filter(r -> r.outcome() == RenewalSettlementResult.Outcome.APPLIED).count());
        assertEquals(1, results.stream().filter(r -> r.outcome() == RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED).count());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(f.payment().getId()).orElseThrow().getStatus());
        assertEquals(1, linkedMembershipCount(f.payment().getId()));
        assertTrue(attempts.findByPayment_IdOrderByCreatedAtAscIdAsc(f.payment().getId()).stream()
                .allMatch(a -> a.getVerifiedAt() != null && a.getProviderTransactionId() != null));
        report("TEST DATA appliedMembership=" + results.stream().filter(r -> r.membershipId() != null)
                .map(RenewalSettlementResult::membershipId).findFirst().orElseThrow()
                + ", parentPayment=" + f.payment().getId());
        labelPaymentAsTestData(f.payment().getId());
    }

    @Test
    void captureRacingCancellationNeverDuplicatesOrDowngradesSuccessfulPayment() throws Exception {
        Fixture f = fixture(PaymentMethod.ONLINE, 2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        RenewalSettlementResult captureResult;
        try {
            Future<RenewalSettlementResult> capture = pool.submit(() -> { await(start); return settlement.settleVerifiedOnlineCapture(capture(f, "cancel-race")); });
            Future<IllegalArgumentException> cancel = pool.submit(() -> {
                await(start);
                try {
                    paymentService.cancelPayment(f.payment().getId(), jwt(f.member()));
                    return null;
                } catch (IllegalArgumentException exception) {
                    return exception;
                }
            });
            start.countDown();
            captureResult = capture.get(60, TimeUnit.SECONDS);
            IllegalArgumentException cancellationResult = cancel.get(60, TimeUnit.SECONDS);
            if (cancellationResult != null) assertEquals(PaymentStatus.SUCCESS,
                    payments.findById(f.payment().getId()).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow();
        }
        Payment stored = payments.findById(f.payment().getId()).orElseThrow();
        assertTrue(stored.getStatus() == PaymentStatus.SUCCESS || stored.getStatus() == PaymentStatus.REFUND_REQUIRED);
        assertTrue(linkedMembershipCount(f.payment().getId()) <= 1);
        if (stored.getStatus() == PaymentStatus.SUCCESS) assertEquals(1, linkedMembershipCount(f.payment().getId()));
        if (captureResult.membershipId() != null) report("TEST DATA appliedMembership="
                + captureResult.membershipId() + ", parentPayment=" + f.payment().getId());
        labelPaymentAsTestData(f.payment().getId());
    }

    @Test
    void lateCaptureRacingMembershipExpiryIsReconciledWithoutCreatingPeriod() throws Exception {
        assumeTrue(runMode == RunMode.DISPOSABLE,
                "Global membership expiry race is clone-only because expiration scans all due memberships");
        Fixture f = fixture(PaymentMethod.ONLINE, 0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RenewalSettlementResult> capture = pool.submit(() -> { await(start); return settlement.settleVerifiedOnlineCapture(capture(f, "expiry-race")); });
            Future<Integer> expiry = pool.submit(() -> { await(start); return membershipService.expireMemberships(); });
            start.countDown();
            capture.get(60, TimeUnit.SECONDS);
            expiry.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(f.payment().getId()).orElseThrow().getStatus());
        assertEquals("EXPIRED", memberships.findById(f.source().getId()).orElseThrow().getStatus());
        assertEquals(0, linkedMembershipCount(f.payment().getId()));
        assertNotNull(attempts.findById(f.attempt().getId()).orElseThrow().getVerifiedAt());
        labelPaymentAsTestData(f.payment().getId());
    }

    @Test
    void competingRenewalChecksCreateOnlyOneRequestForMemberPeriod() throws Exception {
        Fixture f = sourceOnly(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> results = concurrently(start,
                () -> renewalService.createRenewalRequestIfDue(f.source().getId()),
                () -> renewalService.createRenewalRequestIfDue(f.source().getId()));
        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertTrue(payments.findByRenewalMembership_Id(f.source().getId()).isPresent());
        Payment generated = payments.findByRenewalMembership_Id(f.source().getId()).orElseThrow();
        report("TEST DATA schedulerRenewalPayment=" + generated.getId() + ", sourceMembership=" + f.source().getId());
        labelPaymentAsTestData(generated.getId());
    }

    @Test
    void counterSettlementHandlesTimelyAndLateReceipts() {
        Fixture timely = fixture(PaymentMethod.COUNTER, 2);
        RenewalSettlementResult applied = settlement.settleConfirmedCounterReceipt(
                timely.payment().getId(), timely.staff().getId());
        assertEquals(RenewalSettlementResult.Outcome.APPLIED, applied.outcome());
        assertEquals(1, linkedMembershipCount(timely.payment().getId()));

        Fixture late = fixture(PaymentMethod.COUNTER, -1);
        RenewalSettlementResult reconciled = settlement.settleConfirmedCounterReceipt(
                late.payment().getId(), late.staff().getId());
        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, reconciled.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(late.payment().getId()).orElseThrow().getStatus());
        assertEquals(0, linkedMembershipCount(late.payment().getId()));
        report("TEST DATA appliedMembership=" + applied.membershipId() + ", parentPayment=" + timely.payment().getId());
        labelPaymentAsTestData(timely.payment().getId());
        labelPaymentAsTestData(late.payment().getId());
    }

    @Test
    void captureEvidenceSurvivesFilteredUniqueIndexApplicationFailure() {
        assumeTrue(runMode == RunMode.DISPOSABLE,
                "This scenario persists a deliberately inconsistent applied-membership fixture; clone-only");
        Fixture f = fixture(PaymentMethod.ONLINE, 2);
        Fixture other = fixture(PaymentMethod.ONLINE, 2);
        PaymentCheckoutAttempt conflictingWinner = saveAttempt(f.payment());
        conflictingWinner.setAppliedMembership(other.source());
        attempts.saveAndFlush(conflictingWinner);

        RenewalSettlementResult result = settlement.settleVerifiedOnlineCapture(capture(f, "unique-failure"));

        assertEquals(RenewalSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        PaymentCheckoutAttempt evidence = attempts.findById(f.attempt().getId()).orElseThrow();
        assertNotNull(evidence.getVerifiedAt());
        assertEquals("provider-unique-failure", evidence.getProviderTransactionId());
        assertEquals(CheckoutAttemptStatus.RECONCILIATION_REQUIRED, evidence.getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(f.payment().getId()).orElseThrow().getStatus());
        assertEquals(0, linkedMembershipCount(f.payment().getId()));
        labelPaymentAsTestData(f.payment().getId());
    }

    @Test
    void deployedFilteredUniqueIndexesRejectDuplicateAppliedMembershipAndPayment() {
        assumeTrue(runMode == RunMode.DISPOSABLE,
                "This scenario attempts and persists invalid cross-links; clone-only");
        Fixture first = fixture(PaymentMethod.ONLINE, 2);
        Fixture second = fixture(PaymentMethod.ONLINE, 2);
        Fixture third = fixture(PaymentMethod.ONLINE, 2);

        first.attempt().setAppliedMembership(second.source());
        attempts.saveAndFlush(first.attempt());
        third.attempt().setAppliedMembership(second.source());
        assertThrows(DataIntegrityViolationException.class, () -> attempts.saveAndFlush(third.attempt()));

        PaymentCheckoutAttempt secondWinner = saveAttempt(first.payment());
        secondWinner.setAppliedMembership(third.source());
        assertThrows(DataIntegrityViolationException.class, () -> attempts.saveAndFlush(secondWinner));
    }

    private Fixture sourceOnly(int endOffsetDays) {
        String marker = fixtureMarker();
        User member = user(marker, "MEMBER");
        User staff = user(marker + "-staff", "RECEPTIONIST");
        MembershipPlan plan = plan(marker);
        Payment sourcePayment = payment(member, plan, PaymentMethod.COUNTER, PaymentStatus.SUCCESS, null);
        LocalDate today = LocalDate.now(HCM);
        Membership source = new Membership();
        source.setMember(member);
        source.setPlan(plan);
        source.setAutoRenew(true);
        source.setStartDate(today.minusMonths(1));
        source.setEndDate(today.plusDays(endOffsetDays));
        source.setStatus(endOffsetDays < 0 ? "EXPIRED" : "ACTIVE");
        source.setPurchasedMonths(1);
        source.setPaidAmount(new java.math.BigDecimal(PRICE));
        source.setSourcePayment(sourcePayment);
        source = memberships.saveAndFlush(source);
        report("TEST DATA fixture=" + marker + ", memberUser=" + member.getId() + ", staffUser=" + staff.getId()
                + ", plan=" + plan.getId() + ", sourceMembership=" + source.getId()
                + ", sourcePayment=" + sourcePayment.getId());
        return new Fixture(member, staff, plan, source, null, null);
    }

    private Fixture fixture(PaymentMethod method, int endOffsetDays) {
        Fixture base = sourceOnly(endOffsetDays);
        Payment renewal = payment(base.member(), base.plan(), method, PaymentStatus.PENDING, base.source());
        renewal.setAutoRenew(true);
        renewal.setRenewalAutoRenewChoice(Boolean.TRUE);
        renewal = payments.saveAndFlush(renewal);
        PaymentCheckoutAttempt attempt = method == PaymentMethod.ONLINE ? saveAttempt(renewal) : null;
        report("TEST DATA renewalPayment=" + renewal.getId() + ", method=" + method
                + (attempt == null ? "" : ", attempt=" + attempt.getId()));
        return new Fixture(base.member(), base.staff(), base.plan(), base.source(), renewal, attempt);
    }

    private PaymentCheckoutAttempt saveAttempt(Payment payment) {
        LocalDateTime created = LocalDateTime.now(HCM).withNano(0);
        PaymentCheckoutAttempt attempt = new PaymentCheckoutAttempt();
        attempt.setPayment(payment);
        attempt.setProvider(CheckoutProvider.VNPAY);
        attempt.setTransactionReference("RNL" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        attempt.setAmount(payment.getAmount());
        attempt.setCreatedAt(created);
        attempt.setExpiresAt(created.plusMinutes(15));
        attempt.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        attempt = attempts.saveAndFlush(attempt);
        report("TEST DATA checkoutAttempt=" + attempt.getId() + ", parentPayment=" + payment.getId()
                + ", transactionReference=" + attempt.getTransactionReference());
        return attempt;
    }

    private User user(String marker, String role) {
        User user = new User();
        user.setName("TEST DATA " + marker);
        user.setEmail(marker + "@renewal-rehearsal.invalid");
        user.setPasswordHash("not-used-by-service-level-rehearsal");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private MembershipPlan plan(String marker) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName("TEST DATA " + marker);
        plan.setPrice(new java.math.BigDecimal(PRICE));
        plan.setMonthlyPrice(new java.math.BigDecimal(PRICE));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(java.math.BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(java.math.BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("ACTIVE");
        return plans.saveAndFlush(plan);
    }

    private Payment payment(User member, MembershipPlan plan, PaymentMethod method, PaymentStatus status,
            Membership renewalSource) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(new java.math.BigDecimal(PRICE));
        payment.setMethod(method);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setAutoRenew(false);
        payment.setPurchasedMonths(1);
        payment.setMonthlyPriceSnapshot(new java.math.BigDecimal(PRICE));
        payment.setRenewalMembership(renewalSource);
        payment.setStatus(status);
        payment.setTransactionCode("TST-" + RUN_MARKER.substring(RUN_MARKER.length() - 12)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        payment.setNote("TEST DATA renewal rehearsal payment");
        return payments.saveAndFlush(payment);
    }

    private VerifiedRenewalCapture capture(Fixture fixture, String id) {
        return capture(fixture, fixture.attempt(), id);
    }

    private VerifiedRenewalCapture capture(Fixture fixture, PaymentCheckoutAttempt attempt, String id) {
        return new VerifiedRenewalCapture(attempt.getTransactionReference(), "provider-" + id,
                fixture.payment().getAmount(), "00");
    }

    private int linkedMembershipCount(Integer paymentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Membership WHERE source_payment_id = ?",
                Integer.class, paymentId);
        return count == null ? 0 : count;
    }

    private Jwt jwt(User user) {
        return Jwt.withTokenValue("renewal-test-" + user.getId()).header("alg", "none")
                .subject(user.getId().toString()).build();
    }

    private static void verifyCheckpointSchema(Connection connection) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(
                "SELECT CASE WHEN OBJECT_ID(N'dbo.payment_checkout_attempt', N'U') IS NOT NULL "
                        + "AND COL_LENGTH(N'dbo.payment', N'renewal_auto_renew_choice') IS NOT NULL "
                        + "AND COL_LENGTH(N'dbo.payment', N'renewal_membership_id') IS NOT NULL "
                        + "AND COL_LENGTH(N'dbo.payment_checkout_attempt', N'applied_membership_id') IS NOT NULL "
                        + "THEN 1 ELSE 0 END")) {
            if (!r.next() || r.getInt(1) != 1) throw new IllegalStateException("Required renewal columns are missing");
        }
        assertIndex(connection, "ux_payment_checkout_attempt_applied_membership", "applied_membership_id");
        assertIndex(connection, "ux_payment_checkout_attempt_applied_payment", "payment_id");
        assertIndex(connection, "ux_payment_checkout_attempt_reference", "transaction_reference");
        assertIndex(connection, "ix_payment_checkout_attempt_payment_created", "payment_id");
        String fkSql = "SELECT COUNT(*) FROM sys.foreign_keys fk WHERE fk.is_disabled = 0 "
                + "AND fk.is_not_trusted = 0 AND ((fk.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt') "
                + "AND fk.name = N'fk_payment_checkout_attempt_applied_membership' "
                + "AND fk.referenced_object_id = OBJECT_ID(N'dbo.Membership')) OR "
                + "(fk.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt') "
                + "AND fk.name = N'fk_payment_checkout_attempt_payment' "
                + "AND fk.referenced_object_id = OBJECT_ID(N'dbo.payment')))";
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(fkSql)) {
            if (!r.next() || r.getInt(1) != 2) throw new IllegalStateException("Required attempt foreign keys are missing or untrusted");
        }
        String checks = "SELECT COUNT(*) FROM sys.check_constraints c WHERE c.is_disabled = 0 AND c.is_not_trusted = 0 "
                + "AND ((c.parent_object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'ck_payment_renewal_auto_renew_choice') "
                + "OR (c.parent_object_id = OBJECT_ID(N'dbo.payment_checkout_attempt') AND c.name IN "
                + "(N'ck_payment_checkout_attempt_provider',N'ck_payment_checkout_attempt_reference_namespace',"
                + "N'ck_payment_checkout_attempt_status',N'ck_payment_checkout_attempt_amount',N'ck_payment_checkout_attempt_expiry')))";
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(checks)) {
            if (!r.next() || r.getInt(1) != 6) throw new IllegalStateException("Required renewal CHECK constraints are not trusted/enabled");
        }
    }

    private static void assertIndex(Connection connection, String name, String keyColumn) throws Exception {
        String sql = "SELECT COUNT(*) FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id "
                + "JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id "
                + "WHERE i.object_id=OBJECT_ID(N'dbo.payment_checkout_attempt') AND i.name=? AND i.is_disabled=0 "
                + "AND i.is_hypothetical=0 AND c.name=? AND ic.key_ordinal=1 AND ic.is_included_column=0 "
                + "AND NOT EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id=i.object_id AND inc.index_id=i.index_id AND inc.is_included_column=1) "
                + "AND NOT EXISTS (SELECT 1 FROM sys.index_columns descKey WHERE descKey.object_id=i.object_id AND descKey.index_id=i.index_id AND descKey.key_ordinal>0 AND descKey.is_descending_key=1) "
                + "AND ((? IN (N'ux_payment_checkout_attempt_applied_membership',N'ux_payment_checkout_attempt_applied_payment') "
                + "AND i.is_unique=1 AND i.has_filter=1 AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal>0)=1 "
                + "AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition),N'[',N''),N']',N''),N' ',N''),N'(',N''),N')',N'')=N'APPLIED_MEMBERSHIP_IDISNOTNULL') "
                + "OR (?=N'ux_payment_checkout_attempt_reference' AND i.is_unique=1 AND i.has_filter=0 "
                + "AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal>0)=1) "
                + "OR (?=N'ix_payment_checkout_attempt_payment_created' AND i.is_unique=0 AND i.has_filter=0 "
                + "AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal>0)=3 "
                + "AND EXISTS (SELECT 1 FROM sys.index_columns x JOIN sys.columns xc ON xc.object_id=x.object_id AND xc.column_id=x.column_id WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal=2 AND xc.name=N'created_at' AND x.is_descending_key=0) "
                + "AND EXISTS (SELECT 1 FROM sys.index_columns x JOIN sys.columns xc ON xc.object_id=x.object_id AND xc.column_id=x.column_id WHERE x.object_id=i.object_id AND x.index_id=i.index_id AND x.key_ordinal=3 AND xc.name=N'id' AND x.is_descending_key=0)))";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, keyColumn);
            statement.setString(3, name);
            statement.setString(4, name);
            statement.setString(5, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Required attempt index is missing or incompatible: " + name);
            }
        }
    }

    private String fixtureMarker() {
        return "renewal-it-" + RUN_MARKER + "-" + FIXTURE_SEQUENCE.incrementAndGet();
    }

    private static void rejectApplicationDefaultUrl(String targetUrl, boolean developmentMode) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = RenewalSettlementSqlServerIT.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) throw new IllegalStateException("Cannot inspect normal application database target");
            properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        if (!developmentMode && targetUrl.equals(properties.getProperty("spring.datasource.url", "").trim())) {
            throw new IllegalStateException("The rehearsal target matches the normal application datasource");
        }
    }

    private static boolean isTrueEnv(String name) {
        return "true".equalsIgnoreCase(System.getenv(name));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required target setting is missing: " + name);
        return value.trim();
    }

    private static void report(String ids) {
        String line = "Renewal SQL Server rehearsal fixture: " + ids;
        System.out.println(line);
    }

    private void labelPaymentAsTestData(Integer paymentId) {
        Payment fixturePayment = payments.findById(paymentId).orElseThrow();
        String note = fixturePayment.getNote();
        if (note == null || !note.startsWith("TEST DATA")) {
            fixturePayment.setNote("TEST DATA [" + RUN_MARKER + "]: "
                    + (note == null ? "rehearsal fixture" : note));
            payments.saveAndFlush(fixturePayment);
        }
    }

    @FunctionalInterface private interface Task<T> { T run() throws Exception; }
    private static <T> List<T> concurrently(CountDownLatch start, Task<T> first, Task<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = pool.submit(() -> { await(start); return first.run(); });
            Future<T> b = pool.submit(() -> { await(start); return second.run(); });
            start.countDown();
            return List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent start");
    }

    private record Fixture(User member, User staff, MembershipPlan plan, Membership source,
            Payment payment, PaymentCheckoutAttempt attempt) {}

    private enum RunMode { DEVELOPMENT, DISPOSABLE }
}
