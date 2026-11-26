package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

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
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipBenefitValues;
import group5.example.sportCenter.service.PaymentService;

/** SQL Server-only Phase 3B/3C verification. Never run against SportCenterDemo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=" + Phase3DSqlServerGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${E2E_SQLSERVER_USERNAME}",
        "spring.datasource.password=${E2E_SQLSERVER_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "app.payment.callback-secret=phase3d-isolated-mock-secret",
        "app.payment.callback-base-url=http://localhost:8080/api/payments/online/callback",
        "debug=false",
        "logging.level.root=WARN",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase3DSqlServerGuard.class)
class MembershipPhase3DSqlServerIT {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Autowired PaymentService paymentsService;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired PaymentRepository paymentRepository;
    @Autowired NotificationRepository notifications;

    private Fixture fixture;

    @BeforeEach
    void failClosedIdentityCheckBeforeFixtures() throws Exception {
        Phase3DSqlServerGuard.verifyRuntime(dataSource, environment, applicationContext);
    }

    @AfterEach
    void removeOnlyThisTestFixture() {
        if (fixture == null) return;
        for (Integer userId : fixture.userIds()) {
            memberships.deleteAll(memberships.findByMemberId(userId));
        }
        for (Integer userId : fixture.userIds()) {
            List<Payment> ownedPayments = paymentRepository.findByMemberIdOrderByCreatedAtDesc(userId);
            paymentRepository.deleteAll(ownedPayments);
        }
        for (Integer userId : fixture.userIds()) {
            notifications.deleteAll(notifications.findByUserId(userId, PageRequest.of(0, 500)).getContent());
        }
        users.deleteAll(fixture.users());
        if (fixture.plan != null) plans.delete(fixture.plan);
        fixture = null;
    }

    @Test
    void migratedSqlServerColumnsAndSnapshotConstraintsMatchCurrentMappings() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertColumn(metadata, "membership", "benefit_snapshot_status", "varchar", 16, 0, true);
            assertColumn(metadata, "membership", "plan_name_snapshot", "nvarchar", 100, 0, true);
            assertColumn(metadata, "membership", "plan_price_snapshot", "decimal", 12, 2, true);
            assertColumn(metadata, "membership", "course_discount_percent_snapshot", "decimal", 5, 2, true);
            assertColumn(metadata, "membership", "renewal_intent_id", "int", 10, 0, true);
            assertColumn(metadata, "payment", "membership_plan_name_snapshot", "nvarchar", 100, 0, true);
            assertColumn(metadata, "payment", "membership_plan_price_snapshot", "decimal", 12, 2, true);
            assertColumn(metadata, "payment", "membership_quote_captured_at", "datetime2", 26, 6, true);

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT name, is_disabled, is_not_trusted, definition "
                            + "FROM sys.check_constraints WHERE parent_object_id IN "
                            + "(OBJECT_ID(N'dbo.membership'), OBJECT_ID(N'dbo.payment'), OBJECT_ID(N'dbo.membership_upgrade')) "
                            + "AND name IN ('ck_membership_benefit_snapshot','ck_membership_paid_value_basis',"
                            + "'ck_payment_membership_quote_snapshot','ck_membership_upgrade_frozen_quote')")) {
                Map<String, String> definitions = new LinkedHashMap<>();
                while (result.next()) {
                    assertFalse(result.getBoolean("is_disabled"), "Snapshot constraint must be enabled");
                    assertFalse(result.getBoolean("is_not_trusted"), "Snapshot constraint must be trusted");
                    definitions.put(result.getString("name"), result.getString("definition"));
                }
                assertEquals(4, definitions.size(), "All expected snapshot/provenance CHECK constraints must exist");
                assertTrue(definitions.get("ck_membership_benefit_snapshot").contains("LEGACY_UNKNOWN"));
                String paidValues = definitions.get("ck_membership_paid_value_basis");
                assertTrue(paidValues.contains("PURCHASE_PAYMENT"));
                assertTrue(paidValues.contains("UPGRADE_CHAIN"));
                assertTrue(paidValues.contains("RENEWAL_PAYMENT"));
            }

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT OBJECT_ID(N'dbo.membership_renewal_intent','U')")) {
                assertTrue(result.next());
                assertNotNull(result.getObject(1), "Phase 2D Renewal Intent table must remain present");
            }
        }
    }

    @Test
    void onlinePurchasePersistsQuoteAndSettlementIgnoresLaterPlanEditsAndDuplicateCallbacks() {
        fixture = fixture();
        PaymentResponse pending = paymentsService.createPayment(
                request(fixture.plan.getId(), PaymentMethod.ONLINE), jwt(fixture.member));
        Payment captured = paymentRepository.findById(pending.getId()).orElseThrow();
        assertEquals(new BigDecimal("300000.00"), captured.getAmount());
        assertEquals(fixture.planName, captured.getMembershipPlanNameSnapshot());
        assertEquals(new BigDecimal("15.00"), captured.getMembershipCourseDiscountPercentSnapshot());
        assertEquals(new BigDecimal("7.50"), captured.getMembershipPtDiscountPercentSnapshot());
        assertEquals(2, captured.getMembershipDurationMonthsSnapshot());
        assertTrue(captured.getMembershipAllowPtPlanSnapshot());
        assertTrue(captured.getMembershipAllowOneOnOneSnapshot());
        assertNotNull(captured.getMembershipQuoteCapturedAt());

        fixture.plan.setName("Changed after quote");
        fixture.plan.setDescription("Changed after quote");
        fixture.plan.setPrice(new BigDecimal("900000.00"));
        fixture.plan.setDurationMonths(5);
        fixture.plan.setCourseDiscountPercent(new BigDecimal("80.00"));
        fixture.plan.setPersonalTrainingDiscountPercent(new BigDecimal("90.00"));
        fixture.plan.setAllowPersonalTrainingPlan(false);
        fixture.plan.setAllowOneOnOneCoaching(false);
        fixture.plan.setStatus("INACTIVE");
        plans.saveAndFlush(fixture.plan);

        Map<String, String> callback = callbackParameters(pending.getPaymentUrl());
        OnlineCallbackResult first = paymentsService.processOnlineCallback(callback);
        assertEquals(OnlineCallbackResult.Status.APPLIED, first.status());
        assertEquals("SUCCESS", first.payment().getStatus());
        Payment settledPayment = paymentRepository.findById(pending.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, settledPayment.getStatus());
        assertEquals(new BigDecimal("300000.00"), settledPayment.getAmount());
        assertNotNull(settledPayment.getPaidAt());

        List<Membership> created = memberships.findByMemberId(fixture.member.getId());
        assertEquals(1, created.size());
        Membership result = created.get(0);
        LocalDate settlementBusinessDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertEquals(settlementBusinessDate, result.getStartDate());
        assertEquals(settlementBusinessDate.plusMonths(2), result.getEndDate());
        assertEquals("COMPLETE", result.getBenefitSnapshotStatus());
        assertEquals("PURCHASE", result.getBenefitSnapshotSource());
        assertEquals(fixture.planName, result.getPlanNameSnapshot());
        assertEquals(new BigDecimal("300000.00"), result.getPlanPriceSnapshot());
        assertEquals(2, result.getPlanDurationMonthsSnapshot());
        assertEquals(new BigDecimal("15.00"), result.getCourseDiscountPercentSnapshot());
        assertEquals(new BigDecimal("7.50"), result.getPtDiscountPercentSnapshot());
        assertTrue(result.getAllowPtPlanSnapshot());
        assertTrue(result.getAllowOneOnOneSnapshot());
        assertEquals("VERIFIED", result.getPaidValueBasisStatus());
        assertEquals(new BigDecimal("300000.00"), result.getPaidValueBasisAmount());
        assertEquals("PURCHASE_PAYMENT", result.getPaidValueBasisSource());

        OnlineCallbackResult duplicate = paymentsService.processOnlineCallback(callback);
        assertEquals(OnlineCallbackResult.Status.ALREADY_PROCESSED, duplicate.status());
        assertEquals(1, memberships.findByMemberId(fixture.member.getId()).size());
    }

    @Test
    void simultaneousOrdinaryPurchasesForOneMemberCreateOnlyOnePendingPayment() throws Exception {
        fixture = fixture();
        Jwt memberJwt = jwt(fixture.member);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(() -> purchaseAfterGate(memberJwt, ready, start));
            Future<String> second = pool.submit(() -> purchaseAfterGate(memberJwt, ready, start));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = List.of(first.get(45, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(45, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count(), outcomes.toString());
        }
        List<Payment> owned = paymentRepository.findByMemberIdOrderByCreatedAtDesc(fixture.member.getId());
        assertEquals(1, owned.size());
        assertEquals(PaymentStatus.PENDING, owned.get(0).getStatus());
        assertTrue(memberships.findByMemberId(fixture.member.getId()).isEmpty());
    }

    @Test
    void effectiveAndFutureMembershipsBlockPurchaseAndFailedCounterPaymentCreatesNoEntitlement() {
        fixture = fixture();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        memberships.saveAndFlush(membership(fixture, today.minusDays(1), today.plusDays(10)));
        ResponseStatusException effective = assertThrows(ResponseStatusException.class,
                () -> paymentsService.createPayment(request(fixture.plan.getId(), PaymentMethod.COUNTER), jwt(fixture.member)));
        assertEquals(HttpStatus.CONFLICT, effective.getStatusCode());
        memberships.deleteAll(memberships.findByMemberId(fixture.member.getId()));

        memberships.saveAndFlush(membership(fixture, today.plusDays(1), today.plusMonths(1)));
        ResponseStatusException future = assertThrows(ResponseStatusException.class,
                () -> paymentsService.createPayment(request(fixture.plan.getId(), PaymentMethod.COUNTER), jwt(fixture.member)));
        assertEquals(HttpStatus.CONFLICT, future.getStatusCode());
        memberships.deleteAll(memberships.findByMemberId(fixture.member.getId()));

        PaymentResponse pending = paymentsService.createPayment(
                request(fixture.plan.getId(), PaymentMethod.COUNTER), jwt(fixture.member));
        CounterPaymentRequest failed = new CounterPaymentRequest();
        failed.setSuccess(false);
        PaymentResponse cancelled = paymentsService.processAtCounter(pending.getId(), failed, jwt(fixture.staff));
        assertEquals("CANCELLED", cancelled.getStatus());
        assertTrue(memberships.findByMemberId(fixture.member.getId()).isEmpty());
    }

    @Test
    void successfulExternalCallbackWithChangedEligibilityPreservesEvidenceForReconciliation() {
        fixture = fixture();
        PaymentResponse pending = paymentsService.createPayment(
                request(fixture.plan.getId(), PaymentMethod.ONLINE), jwt(fixture.member));
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        memberships.saveAndFlush(membership(fixture, today, today.plusMonths(1)));

        OnlineCallbackResult result = paymentsService.processOnlineCallback(callbackParameters(pending.getPaymentUrl()));
        assertEquals(OnlineCallbackResult.Status.APPLIED, result.status());
        assertEquals("REFUND_REQUIRED", result.payment().getStatus());
        Payment evidence = paymentRepository.findById(pending.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, evidence.getStatus());
        assertEquals(new BigDecimal("300000.00"), evidence.getAmount());
        assertNotNull(evidence.getPaidAt());
        assertTrue(evidence.getNote().contains("Membership"));
        assertEquals(1, memberships.findByMemberId(fixture.member.getId()).size(),
                "A blocked successful payment must not create an overlapping Membership");
    }

    @Test
    void legacyNullSnapshotUsesExplicitPlanFallbackWithoutPaidValueEvidence() {
        fixture = fixture();
        Membership legacy = memberships.saveAndFlush(membership(fixture,
                LocalDate.now(BUSINESS_ZONE).minusDays(1), LocalDate.now(BUSINESS_ZONE).plusDays(30)));

        MembershipBenefitValues resolved = MembershipBenefitValues.resolve(legacy);
        assertEquals(MembershipBenefitValues.Source.LEGACY_PLAN_FALLBACK, resolved.source());
        assertEquals(new BigDecimal("15.00"), resolved.courseDiscountPercent());
        assertEquals(new BigDecimal("7.50"), resolved.personalTrainingDiscountPercent());
        assertTrue(resolved.allowPersonalTrainingPlan());
        assertTrue(resolved.allowOneOnOneCoaching());
        assertNull(legacy.getBenefitSnapshotStatus());
        assertTrue(legacy.verifiedPaidValueBasis().isEmpty());
    }

    private String purchaseAfterGate(Jwt memberJwt, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
        try {
            paymentsService.createPayment(request(fixture.plan.getId(), PaymentMethod.COUNTER), memberJwt);
            return "CREATED";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) return "CONFLICT";
            throw exception;
        }
    }

    private Fixture fixture() {
        Fixture created = new Fixture();
        fixture = created;
        String marker = "p3d-" + UUID.randomUUID().toString().replace("-", "");
        created.member = users.saveAndFlush(user("Member " + marker, marker + "-member@p3d.invalid", "MEMBER"));
        created.staff = users.saveAndFlush(user("Staff " + marker, marker + "-staff@p3d.invalid", "RECEPTIONIST"));
        created.plan = new MembershipPlan();
        created.planName = "Plan " + marker;
        created.plan.setName(created.planName);
        created.plan.setDescription("Captured test plan terms");
        created.plan.setPrice(new BigDecimal("300000.00"));
        created.plan.setDurationMonths(2);
        created.plan.setCourseDiscountPercent(new BigDecimal("15.00"));
        created.plan.setPersonalTrainingDiscountPercent(new BigDecimal("7.50"));
        created.plan.setAllowPersonalTrainingPlan(true);
        created.plan.setAllowOneOnOneCoaching(true);
        created.plan.setStatus("ACTIVE");
        created.plan = plans.saveAndFlush(created.plan);
        fixture = created;
        return created;
    }

    private User user(String name, String email, String role) {
        // Seed fixture identities with SQL Server-native identity syntax; the tested workflow uses JPA afterwards.
        jdbc.update("INSERT INTO dbo.users (created_at,name,email,password_hash,role,status) "
                        + "VALUES (SYSDATETIME(),?,?,?,?,?)",
                name, email, "phase3d-test-only-unused", role, "ACTIVE");
        return users.findByEmail(email).orElseThrow();
    }

    private Membership membership(Fixture owner, LocalDate start, LocalDate end) {
        Membership membership = new Membership();
        membership.setMember(owner.member);
        membership.setPlan(owner.plan);
        membership.setAutoRenew(false);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
        return membership;
    }

    private CreatePaymentRequest request(Integer planId, PaymentMethod method) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.MEMBERSHIP);
        request.setReferenceId(planId);
        request.setMethod(method);
        return request;
    }

    private Jwt jwt(User user) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("phase3d-test-token")
                .header("alg", "none")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("role", user.getRole())
                .build();
    }

    private Map<String, String> callbackParameters(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] pieces = pair.split("=", 2);
            params.put(URLDecoder.decode(pieces[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pieces.length == 1 ? "" : pieces[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private void assertColumn(DatabaseMetaData metadata, String table, String column,
            String expectedType, int size, int scale, boolean nullable) throws Exception {
        try (ResultSet result = metadata.getColumns(null, "dbo", table, column)) {
            assertTrue(result.next(), "Missing dbo." + table + "." + column);
            assertEquals(expectedType, result.getString("TYPE_NAME").toLowerCase());
            assertEquals(size, result.getInt("COLUMN_SIZE"));
            assertEquals(scale, result.getInt("DECIMAL_DIGITS"));
            assertEquals(nullable, result.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
            assertFalse(result.next(), "Unexpected duplicate column metadata for " + column);
        }
    }

    private static final class Fixture {
        private User member;
        private User staff;
        private MembershipPlan plan;
        private String planName;

        private List<User> users() {
            return java.util.stream.Stream.of(member, staff).filter(java.util.Objects::nonNull).toList();
        }

        private List<Integer> userIds() {
            return users().stream().map(User::getId).toList();
        }
    }
}
