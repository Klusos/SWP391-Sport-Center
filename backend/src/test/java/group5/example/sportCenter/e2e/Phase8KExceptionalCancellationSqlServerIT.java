package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.AuditLog;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.service.MembershipRenewalSettlementService;
import group5.example.sportCenter.service.RenewalSettlementEvidence;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

/** Synthetic exceptional-cancellation regression, pinned to the approved QA clone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}", "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false", "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false", "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock", "app.payment.mock-gateway.enabled=true", "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase8KExceptionalCancellationSqlServerIT {
    private static final String RUN = System.getProperty("phase8k.runId", "");
    private static final String CLEANUP_RUN = System.getProperty("phase8k.cleanupRunId", "");
    private static final String MARK = "P8K-" + RUN + "-";
    private static final String TX = "P8K" + (RUN.length() > 10 ? RUN.substring(0, 10) : RUN) + "-";
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipRenewalIntentRepository renewalIntents;
    @Autowired MembershipRenewalSettlementService renewalSettlement;
    @MockitoSpyBean PaymentRepository payments;
    @Autowired JwtService jwt;
    @MockitoSpyBean AuditLogRepository auditLogRepository;
    private final List<Integer> userIds = new ArrayList<>(), planIds = new ArrayList<>(), membershipIds = new ArrayList<>(), paymentIds = new ArrayList<>();
    private User member, admin, receptionist;
    private List<Integer> baseline;
    private List<String> historicalRowsBaseline;
    private boolean retainBrowserFixtures;

    @BeforeAll void guardAndBaseline() throws Exception {
        assertFalse(RUN.isBlank(), "Explicit -Dphase8k.runId required");
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        if (!CLEANUP_RUN.isBlank()) for (String cleanupId : CLEANUP_RUN.split(",")) cleanupExactMarkedRun(cleanupId.trim());
        List<String> activeWriters = jdbc.query("SELECT CONCAT('session=',s.session_id,' host=',COALESCE(s.host_name,''),' program=',COALESCE(s.program_name,''),' command=',r.command,' status=',r.status,' wait=',COALESCE(r.wait_type,'')) FROM sys.dm_exec_requests r "
                + "JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id "
                + "WHERE r.database_id=DB_ID() AND r.session_id<>@@SPID AND s.is_user_process=1 "
                + "AND UPPER(r.command) IN ('INSERT','UPDATE','DELETE','MERGE','ALTER','CREATE','DROP','TRUNCATE')", (rs, row) -> rs.getString(1));
        assertTrue(activeWriters.isEmpty(), "Refusing fixtures while another user session has an active write/schema request: " + activeWriters);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, MARK + "%"));
        baseline = counts();
        historicalRowsBaseline = protectedRowsDigest();
    }

    @Test void runningMultiUpgradeCancellationCreatesPerPaymentRefundsAndAllowsSeparateConfirmation() throws Exception {
        member = user("MEMBER", "member"); admin = user("ADMIN", "admin"); receptionist = user("RECEPTIONIST", "reception");
        MembershipPlan plan = plan();
        Membership root = membership(plan, LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(3), "ACTIVE");
        Payment direct = payment(plan, new BigDecimal("1000000.00"), PaymentPurpose.MEMBERSHIP);
        root.setPurchasePayment(direct); root.setStatus("UPGRADED"); root = memberships.saveAndFlush(root);
        List<Integer> expectedPayments = new ArrayList<>(); expectedPayments.add(direct.getId());
        Membership source = root;
        for (int i = 0; i < 2; i++) {
            Payment upgradePayment = payment(plan, new BigDecimal("300000.00"), PaymentPurpose.UPGRADE);
            Membership result = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(2), "ACTIVE");
            result.setPurchasePayment(upgradePayment); result = memberships.saveAndFlush(result);
            upgrades.saveAndFlush(upgrade(source, result, upgradePayment, plan));
            expectedPayments.add(upgradePayment.getId()); source = result;
            if (i == 0) { source.setStatus("UPGRADED"); source = memberships.saveAndFlush(source); }
        }
        String reason = "R".repeat(250); // Current API's approved bounded reason length.
        HttpResponse<String> cancelled = request("PATCH", "/api/memberships/" + source.getId() + "/cancel", admin,
                "{\"reason\":\"" + reason + "\"}");
        assertEquals(200, cancelled.statusCode(), "Cancellation API must accept the valid bounded reason: " + cancelled.body());
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, source.getId()));
        BigDecimal total = BigDecimal.ZERO;
        for (Integer id : expectedPayments) {
            Payment persisted = payments.findById(id).orElseThrow();
            assertEquals(PaymentStatus.REFUND_REQUIRED, persisted.getStatus());
            assertNull(persisted.getRefundedAt());
            total = total.add(persisted.getAmount());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=?", Integer.class, id.toString()));
        }
        assertEquals(new BigDecimal("1600000.00"), total);
        assertTrue(jdbc.queryForObject("SELECT description FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", String.class, source.getId().toString()).length() <= 500);
        HttpResponse<String> queue = request("GET", "/api/payments/refund-required?page=0&size=100", receptionist, null);
        assertEquals(200, queue.statusCode());
        for (Integer id : expectedPayments) assertTrue(queue.body().contains("\"id\":" + id), "Refund Queue must include Payment " + id);
        int auditBeforeRepeat = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", Integer.class, source.getId().toString());
        HttpResponse<String> repeat = request("PATCH", "/api/memberships/" + source.getId() + "/cancel", admin, "{\"reason\":\"retry\"}");
        assertTrue(repeat.statusCode() >= 400, "Conflicting cancellation retry must not silently succeed");
        assertEquals(auditBeforeRepeat, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", Integer.class, source.getId().toString()));
        for (Integer id : expectedPayments) {
            HttpResponse<String> confirmed = request("PATCH", "/api/payments/" + id + "/refund", receptionist, "{\"note\":\"Synthetic QA confirmation only\"}");
            assertEquals(200, confirmed.statusCode());
            assertEquals("REFUNDED", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, id));
        }
    }

    @AfterAll void cleanupOwnedRows() throws Exception {
        if (retainBrowserFixtures) {
            System.out.println("Keeping only this explicitly requested run-owned fixture for the immediate browser workflow.");
            return;
        }
        if (userIds.isEmpty()) return;
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id IN (" + marks(userIds.size()) + ")", userIds.toArray());
        for (Integer id : membershipIds) jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=?", id.toString());
        for (Integer id : paymentIds) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=?", id.toString());
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id=?", id.toString());
        }
        jdbc.update("DELETE FROM dbo.notification WHERE user_id IN (" + marks(userIds.size()) + ")", userIds.toArray());
        jdbc.update("DELETE FROM dbo.membership_upgrade WHERE payment_id IN (" + marks(paymentIds.size()) + ")", paymentIds.toArray());
        jdbc.update("DELETE FROM dbo.membership WHERE id IN (SELECT id FROM dbo.membership WHERE id IN (" + marks(membershipIds.size()) + ") AND renewal_intent_id IS NOT NULL)", membershipIds.toArray());
        jdbc.update("UPDATE dbo.membership SET purchase_payment_id=NULL WHERE id IN (" + marks(membershipIds.size()) + ")", membershipIds.toArray());
        jdbc.update("UPDATE dbo.membership SET renewal_intent_id=NULL WHERE id IN (" + marks(membershipIds.size()) + ")", membershipIds.toArray());
        List<Integer> ownedIntentIds = jdbc.queryForList("SELECT id FROM dbo.membership_renewal_intent WHERE source_membership_id IN (" + marks(membershipIds.size()) + ")", Integer.class, membershipIds.toArray());
        for (Integer intentId : ownedIntentIds) jdbc.update("DELETE FROM dbo.notification WHERE message LIKE ?", "%Renewal Intent #" + intentId + "%");
        jdbc.update("UPDATE dbo.membership_renewal_intent SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL, closed_at=SYSDATETIME(), closed_reason='AUTO_RENEW_DISABLED' WHERE source_membership_id IN (" + marks(membershipIds.size()) + ")", membershipIds.toArray());
        jdbc.update("DELETE FROM dbo.payment WHERE id IN (" + marks(paymentIds.size()) + ")", paymentIds.toArray());
        jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE source_membership_id IN (" + marks(membershipIds.size()) + ")", membershipIds.toArray());
        jdbc.update("DELETE FROM dbo.membership WHERE id IN (" + marks(membershipIds.size()) + ")", membershipIds.toArray());
        jdbc.update("DELETE FROM dbo.users WHERE id IN (" + marks(userIds.size()) + ") AND name LIKE ?", append(userIds, MARK + "%"));
        jdbc.update("DELETE FROM dbo.membership_plan WHERE id IN (" + marks(planIds.size()) + ")", planIds.toArray());
        assertEquals(baseline, counts(), "Protected aggregate row baselines return after owned fixture cleanup");
        assertEquals(historicalRowsBaseline, protectedRowsDigest(), "Protected financial and Membership rows must remain byte-for-byte equivalent at the SQL value level");
    }

    @Test void requiredAuditFailureRollsBackMembershipAndPaymentTransition() throws Exception {
        User member = user("MEMBER", "rollback-member"), admin = user("ADMIN", "rollback-admin"), receptionist = user("RECEPTIONIST", "rollback-reception");
        this.member = member; this.admin = admin; this.receptionist = receptionist;
        MembershipPlan plan = plan();
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment payment = payment(plan, new BigDecimal("400000.00"), PaymentPurpose.MEMBERSHIP);
        target.setPurchasePayment(payment); target = memberships.saveAndFlush(target);
        Integer membershipId = target.getId(), paymentId = payment.getId();
        doAnswer(invocation -> {
            AuditLog entry = invocation.getArgument(0);
            if ("CANCEL".equals(entry.getAction()) && "MEMBERSHIP".equals(entry.getEntityType())
                    && membershipId.toString().equals(entry.getEntityId())) {
                throw new IllegalStateException("test-only required audit persistence failure");
            }
            return invocation.callRealMethod();
        }).when(auditLogRepository).insert(any(AuditLog.class));
        try {
            HttpResponse<String> response = request("PATCH", "/api/memberships/" + membershipId + "/cancel", admin,
                    "{\"reason\":\"Injected audit failure rollback verification\"}");
            assertTrue(response.statusCode() >= 500, "Injected required audit failure must fail the request");
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, membershipId));
            assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, paymentId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_id IN (?,?) AND entity_type IN ('MEMBERSHIP','PAYMENT')",
                    Integer.class, membershipId.toString(), paymentId.toString()));
        } finally { reset(auditLogRepository); }
    }

    @Test void concurrentCancellationRequestsProduceOneEffectiveTransitionAndOneAudit() throws Exception {
        User member = user("MEMBER", "race-member"), admin = user("ADMIN", "race-admin"), receptionist = user("RECEPTIONIST", "race-reception");
        this.member = member; this.admin = admin; this.receptionist = receptionist;
        MembershipPlan plan = plan();
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment payment = payment(plan, new BigDecimal("650000.00"), PaymentPurpose.MEMBERSHIP);
        target.setPurchasePayment(payment); target = memberships.saveAndFlush(target);
        Integer membershipId = target.getId(), paymentId = payment.getId();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> left = workers.submit(() -> cancelAfter(ready, start, membershipId, admin, "concurrent A"));
            Future<Integer> right = workers.submit(() -> cancelAfter(ready, start, membershipId, admin, "concurrent B"));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int a = left.get(20, TimeUnit.SECONDS), b = right.get(20, TimeUnit.SECONDS);
            assertTrue((a == 200 && b >= 400) || (b == 200 && a >= 400), "Exactly one concurrent cancellation should transition the Membership");
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, membershipId));
            assertEquals("REFUND_REQUIRED", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, paymentId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", Integer.class, membershipId.toString()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='EXCEPTIONAL_CANCEL_REFUND'", Integer.class, paymentId.toString()));
        } finally { workers.shutdownNow(); }
    }

    @Test void cancellationOverlappingRefundConfirmationNeverRegressesPaymentState() throws Exception {
        member = user("MEMBER", "refund-race-member"); admin = user("ADMIN", "refund-race-admin");
        receptionist = user("RECEPTIONIST", "refund-race-reception");
        MembershipPlan plan = plan();
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment payment = payment(plan, new BigDecimal("725000.00"), PaymentPurpose.MEMBERSHIP);
        target.setPurchasePayment(payment); target = memberships.saveAndFlush(target);
        Integer membershipId = target.getId(), paymentId = payment.getId();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> cancel = workers.submit(() -> cancelAfter(ready, start, membershipId, admin, "cancel refund overlap"));
            Future<Integer> confirm = workers.submit(() -> confirmRefundAfter(ready, start, paymentId, receptionist));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int cancelStatus = cancel.get(20, TimeUnit.SECONDS), confirmStatus = confirm.get(20, TimeUnit.SECONDS);
            assertEquals(200, cancelStatus, "Authorized cancellation must complete");
            assertTrue(confirmStatus == 200 || confirmStatus >= 400,
                    "Confirmation may succeed after cancellation or be safely rejected before the obligation exists");
        } finally { start.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS)); }
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, membershipId));
        String finalPayment = jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, paymentId);
        assertTrue("REFUND_REQUIRED".equals(finalPayment) || "REFUNDED".equals(finalPayment), "No lost or regressed Payment state: " + finalPayment);
        assertEquals(new BigDecimal("725000.00"), jdbc.queryForObject("SELECT amount FROM dbo.payment WHERE id=?", BigDecimal.class, paymentId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", Integer.class, membershipId.toString()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='EXCEPTIONAL_CANCEL_REFUND'", Integer.class, paymentId.toString()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id=? AND status IN ('REFUND_REQUIRED','REFUNDED')", Integer.class, paymentId));
    }

    private int confirmRefundAfter(CountDownLatch ready, CountDownLatch start, Integer id, User actor) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Refund race start gate timed out");
        return request("PATCH", "/api/payments/" + id + "/refund", actor, "{\"note\":\"Synthetic concurrent confirmation\"}").statusCode();
    }

    @Test void existingRefundRequiredAndRefundedPaymentsArePreservedWithoutDuplicateObligations() throws Exception {
        User member = user("MEMBER", "disposition-member"), admin = user("ADMIN", "disposition-admin"), receptionist = user("RECEPTIONIST", "disposition-reception");
        this.member = member; this.admin = admin; this.receptionist = receptionist;
        MembershipPlan plan = plan();
        Membership root = membership(plan, LocalDate.now().minusMonths(4), LocalDate.now().plusMonths(4), "UPGRADED");
        Payment direct = payment(plan, new BigDecimal("100000.00"), PaymentPurpose.MEMBERSHIP);
        direct.setStatus(PaymentStatus.REFUND_REQUIRED); direct = payments.saveAndFlush(direct);
        root.setPurchasePayment(direct); root = memberships.saveAndFlush(root);
        Membership first = membership(plan, LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(3), "UPGRADED");
        Payment refunded = payment(plan, new BigDecimal("200000.00"), PaymentPurpose.UPGRADE);
        refunded.setStatus(PaymentStatus.REFUNDED); refunded.setRefundedAt(LocalDateTime.now()); refunded.setRefundedBy(receptionist);
        refunded = payments.saveAndFlush(refunded); first.setPurchasePayment(refunded); first = memberships.saveAndFlush(first);
        upgrades.saveAndFlush(upgrade(root, first, refunded, plan));
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment success = payment(plan, new BigDecimal("300000.00"), PaymentPurpose.UPGRADE);
        target.setPurchasePayment(success); target = memberships.saveAndFlush(target);
        upgrades.saveAndFlush(upgrade(first, target, success, plan));
        Integer membershipId = target.getId();
        HttpResponse<String> response = request("PATCH", "/api/memberships/" + membershipId + "/cancel", admin,
                "{\"reason\":\"Preserve prior refund dispositions\"}");
        assertEquals(200, response.statusCode());
        assertEquals("REFUND_REQUIRED", payments.findById(direct.getId()).orElseThrow().getStatus().name());
        assertEquals("REFUNDED", payments.findById(refunded.getId()).orElseThrow().getStatus().name());
        assertEquals("REFUND_REQUIRED", payments.findById(success.getId()).orElseThrow().getStatus().name());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE'", Integer.class, direct.getId().toString()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE'", Integer.class, refunded.getId().toString()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action='EXCEPTIONAL_CANCEL_REFUND'", Integer.class, success.getId().toString()));
        String summary = jdbc.queryForObject("SELECT description FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", String.class, membershipId.toString());
        assertTrue(summary.contains("eligiblePaidAmount=600000"));
        assertTrue(summary.contains("refundAmountDue=400000"));
        HttpResponse<String> queue = request("GET", "/api/payments/refund-required?page=0&size=100", receptionist, null);
        assertEquals(200, queue.statusCode());
        assertTrue(queue.body().contains("\"id\":" + direct.getId()));
        assertTrue(queue.body().contains("\"id\":" + success.getId()));
        assertFalse(queue.body().contains("\"id\":" + refunded.getId()));
    }

    @Test void laterPaymentTransitionFailureRollsBackEarlierPaymentAndMembership() throws Exception {
        User member = user("MEMBER", "payment-rollback-member"), admin = user("ADMIN", "payment-rollback-admin"), receptionist = user("RECEPTIONIST", "payment-rollback-reception");
        this.member = member; this.admin = admin; this.receptionist = receptionist;
        MembershipPlan plan = plan();
        Membership root = membership(plan, LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(3), "UPGRADED");
        Payment direct = payment(plan, new BigDecimal("100000.00"), PaymentPurpose.MEMBERSHIP);
        root.setPurchasePayment(direct); root = memberships.saveAndFlush(root);
        Membership first = membership(plan, LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(2), "UPGRADED");
        Payment upgradeOne = payment(plan, new BigDecimal("200000.00"), PaymentPurpose.UPGRADE);
        first.setPurchasePayment(upgradeOne); first = memberships.saveAndFlush(first);
        upgrades.saveAndFlush(upgrade(root, first, upgradeOne, plan));
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment upgradeTwo = payment(plan, new BigDecimal("300000.00"), PaymentPurpose.UPGRADE);
        target.setPurchasePayment(upgradeTwo); target = memberships.saveAndFlush(target);
        upgrades.saveAndFlush(upgrade(first, target, upgradeTwo, plan));
        Integer targetId = target.getId(), failOnId = upgradeOne.getId();
        doAnswer(invocation -> {
            Payment item = invocation.getArgument(0);
            if (failOnId.equals(item.getId()) && item.getStatus() == PaymentStatus.REFUND_REQUIRED) {
                throw new IllegalStateException("test-only later Payment transition failure");
            }
            return invocation.callRealMethod();
        }).when(payments).save(any(Payment.class));
        try {
            HttpResponse<String> response = request("PATCH", "/api/memberships/" + targetId + "/cancel", admin,
                    "{\"reason\":\"Injected later Payment transition failure\"}");
            assertTrue(response.statusCode() >= 500, "Injected later Payment failure must fail the transaction");
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, targetId));
            for (Integer id : List.of(direct.getId(), upgradeOne.getId(), upgradeTwo.getId())) {
                assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, id));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id=? AND action LIKE 'EXCEPTIONAL_CANCEL%'", Integer.class, id.toString()));
            }
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", Integer.class, targetId.toString()));
        } finally { reset(payments); }
    }

    @Test void renewalThenUpgradeRefundsCurrentCycleAndUpgradeThenRenewalExcludesPriorCycle() throws Exception {
        member = user("MEMBER", "cycle-member"); admin = user("ADMIN", "cycle-admin"); receptionist = user("RECEPTIONIST", "cycle-reception");
        MembershipPlan plan = plan(); LocalDate today = LocalDate.now();
        Membership previous = membership(plan, today.minusMonths(6), today.plusDays(2), "ACTIVE");
        previous.setAutoRenew(true); previous = memberships.saveAndFlush(previous);
        Payment previousPayment = payment(plan, new BigDecimal("900000.00"), PaymentPurpose.MEMBERSHIP);
        previous.setPurchasePayment(previousPayment); previous = memberships.saveAndFlush(previous);
        Membership renewed = settleRenewal(previous, plan, previous.getEndDate(), previous.getEndDate().plusMonths(6), plan.getPrice());
        Payment renewalPayment = renewed.getPurchasePayment(); renewed.setStatus("UPGRADED"); renewed = memberships.saveAndFlush(renewed);
        Payment currentUpgradePayment = payment(plan, new BigDecimal("250000.00"), PaymentPurpose.UPGRADE);
        Membership current = membership(plan, renewed.getStartDate(), renewed.getEndDate(), "ACTIVE");
        current.setPurchasePayment(currentUpgradePayment); current = memberships.saveAndFlush(current);
        upgrades.saveAndFlush(upgrade(renewed, current, currentUpgradePayment, plan));
        HttpResponse<String> a = request("PATCH", "/api/memberships/" + current.getId() + "/cancel", admin,
                "{\"reason\":\"Renewal followed by Upgrade cycle acceptance\"}");
        assertEquals(200, a.statusCode(), a.body());
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, previousPayment.getId()));
        assertEquals("REFUND_REQUIRED", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, renewalPayment.getId()));
        assertEquals("REFUND_REQUIRED", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, currentUpgradePayment.getId()));
        String summaryA = jdbc.queryForObject("SELECT description FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", String.class, current.getId().toString());
        assertTrue(summaryA.contains("cycleRootMembershipId=" + renewed.getId()));
        assertTrue(summaryA.contains("eligiblePaidAmount=2250000"));

        member = user("MEMBER", "cycle-member-b");
        Membership root = membership(plan, today.minusMonths(6), today.plusDays(2), "UPGRADED");
        Payment direct = payment(plan, new BigDecimal("800000.00"), PaymentPurpose.MEMBERSHIP);
        root.setPurchasePayment(direct); root = memberships.saveAndFlush(root);
        Membership upgraded = membership(plan, root.getStartDate(), root.getEndDate(), "ACTIVE");
        upgraded.setAutoRenew(true); upgraded = memberships.saveAndFlush(upgraded);
        Payment oldUpgrade = payment(plan, new BigDecimal("200000.00"), PaymentPurpose.UPGRADE);
        upgraded.setPurchasePayment(oldUpgrade); upgraded = memberships.saveAndFlush(upgraded);
        upgrades.saveAndFlush(upgrade(root, upgraded, oldUpgrade, plan));
        Membership renewedAfterUpgrade = settleRenewal(upgraded, plan, upgraded.getEndDate(), upgraded.getEndDate().plusMonths(6), plan.getPrice());
        Payment currentRenewal = renewedAfterUpgrade.getPurchasePayment();
        HttpResponse<String> b = request("PATCH", "/api/memberships/" + renewedAfterUpgrade.getId() + "/cancel", admin,
                "{\"reason\":\"Upgrade followed by Renewal cycle acceptance\"}");
        assertEquals(200, b.statusCode(), b.body());
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, direct.getId()));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, oldUpgrade.getId()));
        assertEquals("REFUND_REQUIRED", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, currentRenewal.getId()));
        String summaryB = jdbc.queryForObject("SELECT description FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id=? AND action='CANCEL'", String.class, renewedAfterUpgrade.getId().toString());
        assertTrue(summaryB.contains("cycleRootMembershipId=" + renewedAfterUpgrade.getId()));
        assertTrue(summaryB.contains("eligiblePaidAmount=2000000"));
    }

    @Test void crossMemberUpgradeLineageIsRejectedWithoutPartialMutation() throws Exception {
        member = user("MEMBER", "cross-target-member"); admin = user("ADMIN", "cross-admin");
        receptionist = user("RECEPTIONIST", "cross-reception");
        MembershipPlan plan = plan();
        Membership target = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1), "ACTIVE");
        Payment upgradePayment = payment(plan, new BigDecimal("350000.00"), PaymentPurpose.UPGRADE);
        target.setPurchasePayment(upgradePayment); target = memberships.saveAndFlush(target);
        Integer targetId = target.getId(), paymentId = upgradePayment.getId();

        User targetMember = member;
        member = user("MEMBER", "cross-foreign-member");
        Membership foreignSource = membership(plan, LocalDate.now().minusMonths(2), LocalDate.now().plusDays(10), "UPGRADED");
        member = targetMember;
        upgrades.saveAndFlush(upgrade(foreignSource, target, upgradePayment, plan));

        HttpResponse<String> response = request("PATCH", "/api/memberships/" + targetId + "/cancel", admin,
                "{\"reason\":\"Reject cross-member lineage\"}");
        assertTrue(response.statusCode() >= 400, "Cross-member financial lineage must fail closed");
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, targetId));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, paymentId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_id=? AND action='CANCEL'", Integer.class, targetId.toString()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_id=? AND action LIKE 'EXCEPTIONAL_CANCEL%'", Integer.class, paymentId.toString()));
    }

    @Test void cyclicAndAmbiguousUpgradeLineageAreRejectedWithoutPartialMutation() throws Exception {
        member = user("MEMBER", "lineage-member"); admin = user("ADMIN", "lineage-admin");
        receptionist = user("RECEPTIONIST", "lineage-reception");
        MembershipPlan plan = plan();
        Membership first = membership(plan, LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(1), "ACTIVE");
        Membership second = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(2), "UPGRADED");
        Payment firstPayment = payment(plan, new BigDecimal("250000.00"), PaymentPurpose.UPGRADE);
        Payment secondPayment = payment(plan, new BigDecimal("300000.00"), PaymentPurpose.UPGRADE);
        first.setPurchasePayment(firstPayment); first = memberships.saveAndFlush(first);
        second.setPurchasePayment(secondPayment); second = memberships.saveAndFlush(second);
        upgrades.saveAndFlush(upgrade(second, first, firstPayment, plan));
        upgrades.saveAndFlush(upgrade(first, second, secondPayment, plan));

        HttpResponse<String> cyclic = request("PATCH", "/api/memberships/" + first.getId() + "/cancel", admin,
                "{\"reason\":\"Reject cyclic lineage\"}");
        assertTrue(cyclic.statusCode() >= 400, "Cyclic Upgrade topology must fail closed");
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, first.getId()));
        assertEquals("UPGRADED", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, second.getId()));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, firstPayment.getId()));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, secondPayment.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_id=? AND action='CANCEL'", Integer.class, first.getId().toString()));

        Membership ambiguousTarget = membership(plan, LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), "ACTIVE");
        Membership sourceA = membership(plan, LocalDate.now().minusMonths(2), LocalDate.now().plusDays(5), "UPGRADED");
        Membership sourceB = membership(plan, LocalDate.now().minusMonths(3), LocalDate.now().plusDays(8), "UPGRADED");
        Payment paymentA = payment(plan, new BigDecimal("200000.00"), PaymentPurpose.UPGRADE);
        Payment paymentB = payment(plan, new BigDecimal("220000.00"), PaymentPurpose.UPGRADE);
        ambiguousTarget.setPurchasePayment(paymentA); ambiguousTarget = memberships.saveAndFlush(ambiguousTarget);
        upgrades.saveAndFlush(upgrade(sourceA, ambiguousTarget, paymentA, plan));
        upgrades.saveAndFlush(upgrade(sourceB, ambiguousTarget, paymentB, plan));

        HttpResponse<String> ambiguous = request("PATCH", "/api/memberships/" + ambiguousTarget.getId() + "/cancel", admin,
                "{\"reason\":\"Reject ambiguous incoming Upgrade links\"}");
        assertTrue(ambiguous.statusCode() >= 400, "Multiple incoming Upgrade links must fail closed");
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, ambiguousTarget.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE id IN (?,?) AND status='SUCCESS'", Integer.class, paymentA.getId(), paymentB.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_id=? AND action='CANCEL'", Integer.class, ambiguousTarget.getId().toString()));
    }

    @Test void databaseRejectsDuplicatePaymentReferenceAcrossUpgradeRows() throws Exception {
        member = user("MEMBER", "duplicate-payment-member"); admin = user("ADMIN", "duplicate-payment-admin");
        receptionist = user("RECEPTIONIST", "duplicate-payment-reception");
        MembershipPlan plan = plan();
        Membership source = membership(plan, LocalDate.now().minusMonths(2), LocalDate.now().plusDays(5), "UPGRADED");
        Membership resultA = membership(plan, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(20), "ACTIVE");
        Membership resultB = membership(plan, LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), "ACTIVE");
        Payment payment = payment(plan, new BigDecimal("275000.00"), PaymentPurpose.UPGRADE);
        resultA.setPurchasePayment(payment); memberships.saveAndFlush(resultA);
        upgrades.saveAndFlush(upgrade(source, resultA, payment, plan));

        assertThrows(RuntimeException.class, () -> upgrades.saveAndFlush(upgrade(source, resultB, payment, plan)),
                "The unique Upgrade Payment reference must reject duplicate lineage");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_upgrade WHERE payment_id=?", Integer.class, payment.getId()));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment WHERE id=?", String.class, payment.getId()));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, resultA.getId()));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM dbo.membership WHERE id=?", String.class, resultB.getId()));
    }

    @Test void seedSyntheticExceptionalCancellationForAuthenticatedBrowser() {
        member = user("MEMBER", "browser-cancel-member");
        receptionist = user("RECEPTIONIST", "browser-cancel-reception");
        MembershipPlan plan = plan();
        LocalDate today = LocalDate.now();
        Membership target = membership(plan, today.minusDays(10), today.plusDays(20), "ACTIVE");
        Payment paid = payment(plan, new BigDecimal("375000.00"), PaymentPurpose.MEMBERSHIP);
        target.setPurchasePayment(paid);
        target = memberships.saveAndFlush(target);
        if (Boolean.getBoolean("phase8k.keepBrowserFixtures")) {
            retainBrowserFixtures = true;
            System.out.printf("Browser fixture IDs (synthetic only): member=%d membership=%d payment=%d plan=%d%n",
                    member.getId(), target.getId(), paid.getId(), plan.getId());
        }
    }

    @Test void cleanupRequestedBrowserRunLeavesProtectedBaselinesIntact() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ?", Integer.class, MARK + "%"));
        assertEquals(baseline, counts());
        assertEquals(historicalRowsBaseline, protectedRowsDigest());
    }

    private Membership settleRenewal(Membership source, MembershipPlan target, LocalDate start, LocalDate end, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now(); MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setSourceMembership(source); intent.setTargetPlan(target); intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true); intent.setIntentStatus(RenewalIntentStatus.OPEN); intent.setCreatedAt(now.minusDays(1));
        intent.setValidFrom(source.getEndDate().minusDays(3).atStartOfDay()); intent.setValidUntil(source.getEndDate().atStartOfDay());
        intent.setQuoteCapturedAt(now.minusDays(1)); intent.setSourceEndDateSnapshot(source.getEndDate());
        intent.setSuccessorStartDate(start); intent.setSuccessorEndDate(end); intent.setPlanNameSnapshot(target.getName());
        intent.setPlanDescriptionSnapshot(target.getDescription()); intent.setPlanPriceSnapshot(target.getPrice()); intent.setQuotedAmount(amount);
        intent.setDurationMonthsSnapshot(target.getDurationMonths()); intent.setCourseDiscountPercentSnapshot(target.getCourseDiscountPercent());
        intent.setPtDiscountPercentSnapshot(target.getPersonalTrainingDiscountPercent()); intent.setAllowPtPlanSnapshot(target.getAllowPersonalTrainingPlan());
        intent.setAllowOneOnOneSnapshot(target.getAllowOneOnOneCoaching()); intent = renewalIntents.saveAndFlush(intent);
        Payment candidate = new Payment(); candidate.setMember(member); candidate.setAmount(amount); candidate.setMethod(PaymentMethod.ONLINE);
        candidate.setPurpose(PaymentPurpose.MEMBERSHIP); candidate.setReferenceId(target.getId()); candidate.setStatus(PaymentStatus.PENDING);
        candidate.setTransactionCode(TX + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        candidate.setCreatedAt(now); candidate.setAttemptValidUntil(now.plusMinutes(15)); candidate.setRenewalIntent(intent);
        candidate = payments.saveAndFlush(candidate); paymentIds.add(candidate.getId());
        String reference = "QA-" + UUID.randomUUID();
        var settled = renewalSettlement.settle(RenewalSettlementEvidence.verifiedOnlineCollection(intent.getId(), candidate.getId(), amount,
                "VND", reference, now.plusMinutes(1), "PHASE8K_SYNTHETIC", reference));
        assertEquals("ACCEPTED", settled.outcome().name(), settled.detail());
        membershipIds.add(settled.successor().getId()); return settled.successor();
    }

    private int cancelAfter(CountDownLatch ready, CountDownLatch start, Integer id, User actor, String reason) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Cancellation race start gate timed out");
        return request("PATCH", "/api/memberships/" + id + "/cancel", actor, "{\"reason\":\"" + reason + "\"}").statusCode();
    }

    private User user(String role, String suffix) {
        User u = new User(); u.setName(MARK + suffix); u.setEmail("phase8k." + RUN + "." + suffix + "." + UUID.randomUUID() + "@example.invalid");
        u.setPasswordHash("synthetic-not-used"); u.setRole(role); u.setStatus("ACTIVE"); u = users.saveAndFlush(u); userIds.add(u.getId()); return u;
    }
    private MembershipPlan plan() {
        MembershipPlan p = new MembershipPlan(); p.setName(MARK + "Plan"); p.setPrice(new BigDecimal("2000000.00")); p.setDurationMonths(6);
        p.setCourseDiscountPercent(BigDecimal.ZERO); p.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        p.setAllowPersonalTrainingPlan(false); p.setAllowOneOnOneCoaching(false); p.setStatus("ACTIVE");
        p = plans.saveAndFlush(p); planIds.add(p.getId()); return p;
    }
    private Membership membership(MembershipPlan p, LocalDate from, LocalDate to, String status) {
        Membership m = new Membership(); m.setMember(member); m.setPlan(p); m.setAutoRenew(false); m.setStartDate(from); m.setEndDate(to); m.setStatus(status);
        m = memberships.saveAndFlush(m); membershipIds.add(m.getId()); return m;
    }
    private Payment payment(MembershipPlan p, BigDecimal amount, PaymentPurpose purpose) {
        Payment value = new Payment(); value.setMember(member); value.setAmount(amount); value.setMethod(PaymentMethod.COUNTER); value.setPurpose(purpose);
        value.setReferenceId(p.getId()); value.setStatus(PaymentStatus.SUCCESS); value.setTransactionCode(TX + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        value.setCreatedAt(LocalDateTime.now()); value.setPaidAt(LocalDateTime.now()); value.setProcessedBy(receptionist);
        value = payments.saveAndFlush(value); paymentIds.add(value.getId()); return value;
    }
    private MembershipUpgrade upgrade(Membership source, Membership result, Payment payment, MembershipPlan target) {
        MembershipUpgrade u = new MembershipUpgrade(); u.setPayment(payment); u.setSourceMembership(source); u.setResultMembership(result); u.setTargetPlan(target);
        u.setSourcePeriodStartDate(source.getStartDate()); u.setSourcePeriodEndDate(source.getEndDate()); u.setCreditValuationDate(source.getEndDate().minusDays(3));
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate());
        u.setSourceTotalDays(days); u.setSourceUnusedDays(3); u.setRemainingDays(3); u.setSourcePaidValueBasisAmount(new BigDecimal("1000000.00"));
        u.setTargetPlanNameSnapshot(target.getName()); u.setTargetPlanDescriptionSnapshot(target.getDescription()); u.setTargetPlanPriceSnapshot(target.getPrice());
        u.setTargetDurationMonthsSnapshot(target.getDurationMonths()); u.setTargetCourseDiscountPercentSnapshot(target.getCourseDiscountPercent());
        u.setTargetPtDiscountPercentSnapshot(target.getPersonalTrainingDiscountPercent()); u.setTargetAllowPtPlanSnapshot(target.getAllowPersonalTrainingPlan());
        u.setTargetAllowOneOnOneSnapshot(target.getAllowOneOnOneCoaching()); u.setAppliedCreditAmount(new BigDecimal("100000.00"));
        u.setCreatedAt(LocalDateTime.now()); u.setExpiresAt(LocalDateTime.now().plusDays(1)); return u;
    }
    private HttpResponse<String> request(String method, String path, User actor, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + jwt.generateToken(actor));
        if (body != null) b.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private List<Integer> counts() {
        return List.of("users", "membership_plan", "membership", "payment", "membership_upgrade", "audit_log", "notification")
                .stream().map(t -> jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + t, Integer.class)).toList();
    }
    private List<String> protectedRowsDigest() {
        List<String> result = new ArrayList<>();
        for (String table : List.of("membership", "payment", "membership_upgrade", "membership_cancellation_request")) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                jdbc.query("SELECT * FROM dbo.[" + table + "] ORDER BY id", rs -> {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            Object value = rs.getObject(i);
                            String canonical = value == null ? "<NULL>" : value instanceof byte[] binary
                                    ? java.util.HexFormat.of().formatHex(binary) : String.valueOf(value);
                            byte[] bytes = canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            digest.update(bytes); digest.update((byte) 0xff);
                        }
                        digest.update((byte) 0xfe);
                    }
                    return null;
                });
                result.add(table + ":" + java.util.HexFormat.of().formatHex(digest.digest()));
            } catch (Exception failure) { throw new IllegalStateException("Could not snapshot protected table " + table, failure); }
        }
        return List.copyOf(result);
    }
    private static String marks(int n) { return String.join(",", java.util.Collections.nCopies(n, "?")); }
    private static Object[] append(List<Integer> ids, Object tail) { List<Object> args = new ArrayList<>(ids); args.add(tail); return args.toArray(); }

    private void cleanupExactMarkedRun(String runId) {
        String prefix = "P8K-" + runId + "-%";
        List<Integer> priorUsers = jdbc.queryForList("SELECT id FROM dbo.users WHERE name LIKE ? AND email LIKE ?", Integer.class,
                prefix, "phase8k." + runId + ".%@example.invalid");
        List<Integer> priorPlans = jdbc.queryForList("SELECT id FROM dbo.membership_plan WHERE name LIKE ?", Integer.class, prefix);
        if (priorUsers.isEmpty() && priorPlans.isEmpty()) return;
        List<Integer> priorMemberships = priorUsers.isEmpty() ? List.of()
                : jdbc.queryForList("SELECT id FROM dbo.membership WHERE member_id IN (" + marks(priorUsers.size()) + ")", Integer.class, priorUsers.toArray());
        List<Integer> priorIntentIds = priorMemberships.isEmpty() ? List.of()
                : jdbc.queryForList("SELECT id FROM dbo.membership_renewal_intent WHERE source_membership_id IN (" + marks(priorMemberships.size()) + ")", Integer.class, priorMemberships.toArray());
        List<Integer> priorPayments = priorUsers.isEmpty() ? List.of()
                : jdbc.queryForList("SELECT id FROM dbo.payment WHERE member_id IN (" + marks(priorUsers.size()) + ")", Integer.class, priorUsers.toArray());
        if (!priorUsers.isEmpty()) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id IN (" + marks(priorUsers.size()) + ")", priorUsers.toArray());
            jdbc.update("DELETE FROM dbo.notification WHERE user_id IN (" + marks(priorUsers.size()) + ")", priorUsers.toArray());
        }
        for (Integer intentId : priorIntentIds) jdbc.update("DELETE FROM dbo.notification WHERE message LIKE ?", "%Renewal Intent #" + intentId + "%");
        if (!priorMemberships.isEmpty() || !priorPayments.isEmpty()) {
            String membershipClause = priorMemberships.isEmpty() ? "1=0" : "source_membership_id IN (" + marks(priorMemberships.size()) + ") OR result_membership_id IN (" + marks(priorMemberships.size()) + ")";
            String paymentClause = priorPayments.isEmpty() ? "1=0" : "payment_id IN (" + marks(priorPayments.size()) + ")";
            List<Object> args = new ArrayList<>(); if (!priorMemberships.isEmpty()) { args.addAll(priorMemberships); args.addAll(priorMemberships); } if (!priorPayments.isEmpty()) args.addAll(priorPayments);
            jdbc.update("DELETE FROM dbo.membership_upgrade WHERE (" + membershipClause + ") OR (" + paymentClause + ")", args.toArray());
            if (!priorMemberships.isEmpty()) {
                jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='MEMBERSHIP' AND entity_id IN (" + marks(priorMemberships.size()) + ")",
                        priorMemberships.stream().map(String::valueOf).toArray());
                jdbc.update("DELETE FROM dbo.membership WHERE id IN (SELECT id FROM dbo.membership WHERE id IN (" + marks(priorMemberships.size()) + ") AND renewal_intent_id IS NOT NULL)", priorMemberships.toArray());
                jdbc.update("UPDATE dbo.membership SET purchase_payment_id=NULL WHERE id IN (" + marks(priorMemberships.size()) + ")", priorMemberships.toArray());
                jdbc.update("UPDATE dbo.membership SET renewal_intent_id=NULL WHERE id IN (" + marks(priorMemberships.size()) + ")", priorMemberships.toArray());
                jdbc.update("UPDATE dbo.membership_renewal_intent SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL, closed_at=SYSDATETIME(), closed_reason='AUTO_RENEW_DISABLED' WHERE source_membership_id IN (" + marks(priorMemberships.size()) + ")", priorMemberships.toArray());
                if (!priorPayments.isEmpty()) jdbc.update("DELETE FROM dbo.payment WHERE id IN (" + marks(priorPayments.size()) + ")", priorPayments.toArray());
                jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE source_membership_id IN (" + marks(priorMemberships.size()) + ")", priorMemberships.toArray());
                jdbc.update("DELETE FROM dbo.membership WHERE id IN (" + marks(priorMemberships.size()) + ")", priorMemberships.toArray());
            }
            if (!priorPayments.isEmpty()) {
                jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='PAYMENT' AND entity_id IN (" + marks(priorPayments.size()) + ")",
                        priorPayments.stream().map(String::valueOf).toArray());
                jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id IN (" + marks(priorPayments.size()) + ")",
                        priorPayments.stream().map(String::valueOf).toArray());
            }
        }
        if (!priorUsers.isEmpty()) jdbc.update("DELETE FROM dbo.users WHERE id IN (" + marks(priorUsers.size()) + ") AND name LIKE ?",
                append(priorUsers, prefix));
        if (!priorPlans.isEmpty()) jdbc.update("DELETE FROM dbo.membership_plan WHERE id IN (" + marks(priorPlans.size()) + ") AND name LIKE ?",
                append(priorPlans, prefix));
    }
}
