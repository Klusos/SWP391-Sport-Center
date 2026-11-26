package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import group5.example.sportCenter.dto.MemberRenewalAttemptActionResponse;
import group5.example.sportCenter.dto.MemberRenewalIntentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.payment.VnpayPaymentGateway;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipRenewalSettlementService;
import group5.example.sportCenter.service.MembershipRenewalLifecycleService;
import group5.example.sportCenter.service.RenewalSettlementEvidence;
import group5.example.sportCenter.service.RenewalSettlementResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP Renewal journey against the already-authorized isolated SQL Server clone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=" + Phase5BFinalRenewalHttpIT.Phase8CJUnitCloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "app.payment.callback-secret=phase5b-final-http-test-only",
        "app.payment.callback-base-url=http://localhost:8080/api/payments/online/callback",
        "debug=false",
        "logging.level.root=INFO"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase5BFinalRenewalHttpIT.Phase8CJUnitCloneGuard.class)
@Import(Phase5BFinalRenewalHttpIT.TestGatewayConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase5BFinalRenewalHttpIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal QUOTE = new BigDecimal("725000.00");
    private static final String RUN_ID = System.getProperty("phase8cj.runId", "");
    private static final String PREFIX = RUN_ID + "-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String FIXTURE_PASSWORD = "phase5-final-fixture-password";
    private static final String TEST_SIGNATURE = "phase5-final-valid-signature";
    private static final String SYNTHETIC_VNPAY_TMN = "TESTTMN1";
    private static final String SYNTHETIC_VNPAY_SECRET = "TESTHASHSECRET0123456789";
    private static final List<String> PROTECTED = List.of("membership", "payment", "membership_upgrade",
            "membership_cancellation_request");
    private static final DateTimeFormatter PROVIDER_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipRenewalIntentRepository intents;
    @Autowired PaymentRepository payments;
    @MockitoSpyBean MembershipRenewalSettlementService settlementService;
    @Autowired MembershipRenewalLifecycleService renewalLifecycle;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;
    private final List<Fixture> fixtures = new ArrayList<>();
    private Map<String, Map<String, List<String>>> baseline;

    @BeforeAll
    void verifyCloneAndCaptureHistoricalBaseline() throws Exception {
        Phase8CJUnitCloneGuard.verifyRuntime(dataSource, environment, context);
        assertFalse(PREFIX.startsWith("-"), "Set an explicit Phase 8C-J run identifier");
        cleanupPriorMarkedFixtures();
        baseline = snapshotProtectedRows();
    }

    @Test
    void memberCompletesAuthenticatedRenewalJourneyOverHttp() throws Exception {
        Fixture owner = fixture(true);
        User other = user("other", fixtureEmail());
        Fixture otherFixture = new Fixture(other);
        fixtures.add(otherFixture);

        String ownerToken = login(owner.user);
        String otherToken = login(other);
        HttpResponse<String> mineResponse = request("GET", "/api/memberships/renewal-intents/me", ownerToken, null);
        assertEquals(200, mineResponse.statusCode());
        MemberRenewalIntentResponse[] mine = objectMapper.readValue(mineResponse.body(), MemberRenewalIntentResponse[].class);
        assertEquals(1, mine.length);
        assertEquals(owner.intent.getId(), mine[0].intentId());
        assertEquals(QUOTE, mine[0].quotedAmount());

        HttpResponse<String> ownIntent = request("GET", "/api/memberships/renewal-intents/" + owner.intent.getId(),
                ownerToken, null);
        assertEquals(200, ownIntent.statusCode());
        HttpResponse<String> foreignIntent = request("GET", "/api/memberships/renewal-intents/" + owner.intent.getId(),
                otherToken, null);
        assertEquals(404, foreignIntent.statusCode());

        HttpResponse<String> created = request("POST",
                "/api/memberships/renewal-intents/" + owner.intent.getId() + "/attempts", ownerToken, null);
        assertEquals(200, created.statusCode());
        MemberRenewalAttemptActionResponse createdAction = objectMapper.readValue(created.body(),
                MemberRenewalAttemptActionResponse.class);
        assertNotNull(createdAction.checkoutUrl());
        Integer paymentId = createdAction.attempt().paymentId();
        Payment pending = payments.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.PENDING, pending.getStatus());
        assertEquals(QUOTE, pending.getAmount());
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isEmpty());

        HttpResponse<String> duplicateRequest = request("POST",
                "/api/memberships/renewal-intents/" + owner.intent.getId() + "/attempts", ownerToken, null);
        assertEquals(200, duplicateRequest.statusCode());
        MemberRenewalAttemptActionResponse duplicateAction = objectMapper.readValue(duplicateRequest.body(),
                MemberRenewalAttemptActionResponse.class);
        assertEquals(paymentId, duplicateAction.attempt().paymentId());
        assertEquals(1, payments.findByRenewalIntent_IdOrderByCreatedAtAsc(owner.intent.getId()).size());

        Fixture concurrentOwner = fixture(true);
        String concurrentToken = login(concurrentOwner.user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = workers.submit(() -> attemptAfter(ready, start, concurrentOwner, concurrentToken));
            Future<HttpResponse<String>> second = workers.submit(() -> attemptAfter(ready, start, concurrentOwner, concurrentToken));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            HttpResponse<String> firstResponse = first.get(15, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.statusCode());
            assertEquals(200, secondResponse.statusCode());
            int firstPayment = objectMapper.readValue(firstResponse.body(), MemberRenewalAttemptActionResponse.class).attempt().paymentId();
            int secondPayment = objectMapper.readValue(secondResponse.body(), MemberRenewalAttemptActionResponse.class).attempt().paymentId();
            assertEquals(firstPayment, secondPayment, "Concurrent requests must converge on one attempt");
            assertEquals(1, payments.findByRenewalIntent_IdOrderByCreatedAtAsc(concurrentOwner.intent.getId()).size());
        } finally {
            workers.shutdownNow();
        }

        Map<String, String> callback = callbackParameters(pending.getTransactionCode(), true,
                "9926112200001", TEST_SIGNATURE, validProviderPaidAt(pending));
        HttpClient localHttp = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> browserReturn = localHttp.send(HttpRequest.newBuilder()
                .uri(callbackUri("/api/payments/vnpay/return", callback)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, browserReturn.statusCode());
        assertTrue(browserReturn.headers().firstValue("location").orElse("").contains("paymentStatus=PENDING"));
        assertEquals(PaymentStatus.PENDING, payments.findById(paymentId).orElseThrow().getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isEmpty(),
                "Browser Return must not settle a Renewal");

        AtomicReference<RenewalSettlementResult> settlementResult = new AtomicReference<>();
        doAnswer(invocation -> {
            RenewalSettlementResult result = (RenewalSettlementResult) invocation.callRealMethod();
            settlementResult.set(result);
            return result;
        }).when(settlementService).settle(any());
        HttpResponse<String> ipn = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                callbackUri("/api/payments/vnpay/ipn", callback)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ipn.statusCode());
        assertEquals("00", objectMapper.readTree(ipn.body()).path("RspCode").asText());
        assertNotNull(settlementResult.get(), "IPN must reach the Renewal settlement engine");
        assertEquals("ACCEPTED", settlementResult.get().outcome().name(), settlementResult.get().detail());
        Payment settledPayment = payments.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, settledPayment.getStatus());
        var successor = memberships.findByRenewalIntent_Id(owner.intent.getId()).orElseThrow();
        assertEquals(owner.source.getEndDate(), successor.getStartDate());
        assertEquals(owner.source.getEndDate().plusMonths(2), successor.getEndDate());

        HttpResponse<String> duplicateIpn = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                callbackUri("/api/payments/vnpay/ipn", callback)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("02", objectMapper.readTree(duplicateIpn.body()).path("RspCode").asText());
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isPresent());

        HttpResponse<String> refreshedResponse = request("GET", "/api/memberships/renewal-intents/me", ownerToken, null);
        MemberRenewalIntentResponse[] refreshed = objectMapper.readValue(refreshedResponse.body(),
                MemberRenewalIntentResponse[].class);
        assertEquals("SETTLED", refreshed[0].latestResult());
        assertEquals(successor.getId(), refreshed[0].successorMembershipId());

        Map<String, String> invalid = callbackParameters(pending.getTransactionCode(), true,
                "9926112200002", "invalid-signature", validProviderPaidAt(pending));
        HttpResponse<String> invalidIpn = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                callbackUri("/api/payments/vnpay/ipn", invalid)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("97", objectMapper.readTree(invalidIpn.body()).path("RspCode").asText());
    }

    @Test
    void realVnpaySignedIpnSettlesRenewalAndRejectsInvalidFactsOverSqlServerHttp() throws Exception {
        Fixture accepted = fixture(true);
        String memberToken = login(accepted.user);
        HttpResponse<String> created = request("POST",
                "/api/memberships/renewal-intents/" + accepted.intent.getId() + "/attempts", memberToken, null);
        assertEquals(200, created.statusCode());
        int paymentId = objectMapper.readValue(created.body(), MemberRenewalAttemptActionResponse.class)
                .attempt().paymentId();
        Payment pending = payments.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.PENDING, pending.getStatus());
        assertEquals(PaymentMethod.ONLINE, pending.getMethod());
        assertTrue(memberships.findByRenewalIntent_Id(accepted.intent.getId()).isEmpty());

        Map<String, String> valid = signedVnpayIpn(pending, QUOTE, "998877660001", null);
        HttpResponse<String> browserReturn = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                callbackUri("/api/payments/vnpay/return", valid)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, browserReturn.statusCode());
        assertTrue(browserReturn.headers().firstValue("location").orElse("").contains("paymentStatus=PENDING"));
        assertEquals(PaymentStatus.PENDING, payments.findById(paymentId).orElseThrow().getStatus());

        HttpResponse<String> ipn = sendVnpayIpn(valid);
        assertEquals("00", objectMapper.readTree(ipn.body()).path("RspCode").asText(), ipn.body());
        Payment settled = payments.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, settled.getStatus());
        assertEquals("998877660001", settled.getRenewalProviderTransactionReference());
        assertEquals(0, QUOTE.compareTo(settled.getRenewalVerifiedCollectionAmount()));
        assertEquals("VND", settled.getRenewalVerifiedCollectionCurrency());
        assertEquals("PROVIDER_VERIFIED", settled.getRenewalPaidTimeEvidenceType().name());
        assertNotNull(settled.getRenewalCollectionVerifiedAt());
        assertNotNull(settled.getRenewalSettlementAcceptedAt());
        MembershipRenewalIntent acceptedIntent = intents.findById(accepted.intent.getId()).orElseThrow();
        assertEquals(RenewalIntentStatus.SETTLED, acceptedIntent.getIntentStatus());
        assertEquals(paymentId, acceptedIntent.getSettledPayment().getId());
        Membership successor = memberships.findByRenewalIntent_Id(accepted.intent.getId()).orElseThrow();
        assertEquals(accepted.source.getEndDate(), successor.getStartDate());
        assertEquals(accepted.source.getEndDate().plusMonths(2), successor.getEndDate());
        assertEquals(accepted.plan.getName(), successor.getPlanNameSnapshot());
        assertEquals(0, accepted.plan.getCourseDiscountPercent().compareTo(successor.getCourseDiscountPercentSnapshot()));
        assertEquals(0, accepted.plan.getPersonalTrainingDiscountPercent()
                .compareTo(successor.getPtDiscountPercentSnapshot()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE renewal_intent_id=?",
                Integer.class, accepted.intent.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id=? AND action='SETTLE'",
                Integer.class, Integer.toString(paymentId)));

        HttpResponse<String> replay = sendVnpayIpn(valid);
        assertEquals("02", objectMapper.readTree(replay.body()).path("RspCode").asText(), replay.body());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE renewal_intent_id=?",
                Integer.class, accepted.intent.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=? AND renewal_settlement_accepted_at IS NOT NULL",
                Integer.class, accepted.intent.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id=? AND action='SETTLE'",
                Integer.class, Integer.toString(paymentId)));

        Fixture badSignature = fixture(true);
        Payment badSignaturePayment = createOnlineAttempt(badSignature);
        Map<String, String> tampered = signedVnpayIpn(badSignaturePayment, QUOTE, "998877660002", null);
        tampered.put("vnp_Amount", "1");
        HttpResponse<String> invalidSignatureResponse = sendVnpayIpn(tampered);
        assertEquals("97", objectMapper.readTree(invalidSignatureResponse.body()).path("RspCode").asText());
        assertNoIpnEntitlementOrCollection(badSignature, badSignaturePayment);

        Fixture wrongAmount = fixture(true);
        Payment wrongAmountPayment = createOnlineAttempt(wrongAmount);
        HttpResponse<String> wrongAmountResponse = sendVnpayIpn(signedVnpayIpn(
                wrongAmountPayment, QUOTE.add(BigDecimal.ONE), "998877660003", null));
        assertEquals("00", objectMapper.readTree(wrongAmountResponse.body()).path("RspCode").asText());
        assertTrue(objectMapper.readTree(wrongAmountResponse.body()).path("Message").asText()
                .contains("reconciliation"));
        assertNoIpnEntitlementOrCollection(badSignature, badSignaturePayment);
        Payment reconciled = payments.findById(wrongAmountPayment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, reconciled.getStatus());
        assertEquals(0, QUOTE.add(BigDecimal.ONE).compareTo(reconciled.getRenewalVerifiedCollectionAmount()));
        assertNotNull(reconciled.getRenewalCollectionVerifiedAt());
        assertEquals(RenewalIntentStatus.OPEN,
                intents.findById(wrongAmount.intent.getId()).orElseThrow().getIntentStatus());
        assertTrue(memberships.findByRenewalIntent_Id(wrongAmount.intent.getId()).isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=? AND renewal_settlement_accepted_at IS NOT NULL",
                Integer.class, wrongAmount.intent.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id=? AND action='SETTLE'",
                Integer.class, Integer.toString(wrongAmountPayment.getId())));

        Fixture unknownReference = fixture(true);
        Payment unknownReferencePayment = createOnlineAttempt(unknownReference);
        Map<String, String> unknown = signedVnpayIpn(unknownReferencePayment, QUOTE, "998877660004",
                "TXNREN0000000000000000");
        HttpResponse<String> unknownResponse = sendVnpayIpn(unknown);
        assertEquals("01", objectMapper.readTree(unknownResponse.body()).path("RspCode").asText());
        assertNoIpnEntitlementOrCollection(unknownReference, unknownReferencePayment);
    }

    private Payment createOnlineAttempt(Fixture fixture) throws Exception {
        String token = login(fixture.user);
        HttpResponse<String> response = request("POST",
                "/api/memberships/renewal-intents/" + fixture.intent.getId() + "/attempts", token, null);
        assertEquals(200, response.statusCode());
        int id = objectMapper.readValue(response.body(), MemberRenewalAttemptActionResponse.class).attempt().paymentId();
        return payments.findById(id).orElseThrow();
    }

    private HttpResponse<String> sendVnpayIpn(Map<String, String> parameters) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                callbackUri("/api/payments/vnpay/ipn", parameters)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertNoIpnEntitlementOrCollection(Fixture fixture, Payment payment) {
        Payment current = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, current.getStatus());
        assertNull(current.getRenewalProviderTransactionReference());
        assertNull(current.getRenewalCollectionVerifiedAt());
        assertNull(current.getRenewalSettlementAcceptedAt());
        assertEquals(RenewalIntentStatus.OPEN, intents.findById(fixture.intent.getId()).orElseThrow().getIntentStatus());
        assertTrue(memberships.findByRenewalIntent_Id(fixture.intent.getId()).isEmpty());
    }

    private Map<String, String> signedVnpayIpn(Payment payment, BigDecimal amount,
            String providerReference, String transactionReferenceOverride) {
        LocalDateTime paidAt = validProviderPaidAt(payment);
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Amount", amount.movePointRight(2).toBigIntegerExact().toString());
        parameters.put("vnp_BankCode", "NCB");
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_OrderInfo", "Synthetic signed Renewal QA collection");
        parameters.put("vnp_PayDate", paidAt.format(PROVIDER_TIME));
        parameters.put("vnp_ResponseCode", "00");
        parameters.put("vnp_TmnCode", SYNTHETIC_VNPAY_TMN);
        parameters.put("vnp_TransactionNo", providerReference);
        parameters.put("vnp_TransactionStatus", "00");
        parameters.put("vnp_TxnRef", transactionReferenceOverride == null
                ? payment.getTransactionCode().replace("-", "") : transactionReferenceOverride);
        parameters.put("vnp_SecureHash", signVnpay(parameters));
        parameters.put("vnp_SecureHashType", "HmacSHA512");
        return parameters;
    }

    private String signVnpay(Map<String, String> parameters) {
        String canonical = new TreeMap<>(parameters).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SYNTHETIC_VNPAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create local synthetic VNPay signature", e);
        }
    }

    @Test
    void concurrentRenewalLifecycleEntryPointsCreateOnlyOneIntent() throws Exception {
        Fixture owner = fixture(false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lifecycle race start gate timed out");
                return renewalLifecycle.runDueLifecycle();
            });
            Future<?> second = workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lifecycle race start gate timed out");
                return renewalLifecycle.runDueLifecycle();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            List<MembershipRenewalIntent> created = intents.findBySourceMembershipId(owner.source.getId()).stream().toList();
            assertEquals(1, created.size(), "Concurrent lifecycle entry paths must converge on one source Membership Intent");
            assertEquals(RenewalIntentStatus.OPEN, created.get(0).getIntentStatus());
            assertTrue(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(created.get(0).getId()).isEmpty(),
                    "Intent lifecycle must not create a payment attempt or collect money");
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void receptionistCanCreateAndConfirmCounterRenewalOnceOverHttp() throws Exception {
        Fixture owner = fixture(true);
        User receptionist = user("receptionist", fixtureEmail(), "RECEPTIONIST");
        fixtures.add(new Fixture(receptionist));
        String staffToken = login(receptionist);

        HttpResponse<String> list = request("GET", "/api/receptionist/members/" + owner.user.getId()
                + "/renewal-intents", staffToken, null);
        assertEquals(200, list.statusCode());
        MemberRenewalIntentResponse[] intentsForStaff = objectMapper.readValue(
                list.body(), MemberRenewalIntentResponse[].class);
        assertEquals(1, intentsForStaff.length);
        assertEquals(owner.intent.getId(), intentsForStaff[0].intentId());
        assertEquals(QUOTE, intentsForStaff[0].quotedAmount());

        HttpResponse<String> created = request("POST", "/api/receptionist/members/" + owner.user.getId()
                + "/renewal-intents/" + owner.intent.getId() + "/counter-attempts", staffToken, null);
        assertEquals(200, created.statusCode());
        MemberRenewalAttemptActionResponse action = objectMapper.readValue(created.body(),
                MemberRenewalAttemptActionResponse.class);
        assertEquals("COUNTER", action.attempt().method());
        assertNull(action.checkoutUrl());
        Integer paymentId = action.attempt().paymentId();
        assertEquals(PaymentStatus.PENDING, payments.findById(paymentId).orElseThrow().getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isEmpty());

        LocalDateTime beforeConfirmation = LocalDateTime.now(VN).minusSeconds(1);
        String confirmation = "{\"success\":true,\"note\":\"test collection received\"}";
        AtomicReference<RenewalSettlementResult> counterSettlementResult = new AtomicReference<>();
        doAnswer(invocation -> {
            RenewalSettlementResult result = (RenewalSettlementResult) invocation.callRealMethod();
            counterSettlementResult.set(result);
            return result;
        }).when(settlementService).settle(any());
        HttpResponse<String> confirmed = request("PATCH", "/api/payments/" + paymentId + "/counter",
                staffToken, confirmation);
        assertEquals(200, confirmed.statusCode());
        assertNotNull(counterSettlementResult.get(), "COUNTER confirmation must reach the settlement engine");
        assertEquals("ACCEPTED", counterSettlementResult.get().outcome().name(), counterSettlementResult.get().detail());
        assertEquals("SUCCESS", objectMapper.readTree(confirmed.body()).path("status").asText());
        Payment paid = payments.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, paid.getStatus());
        assertEquals(receptionist.getId(), paid.getProcessedBy().getId());
        assertNotNull(paid.getPaidAt());
        assertFalse(paid.getPaidAt().isBefore(beforeConfirmation),
                "COUNTER paidAt must be server generated at confirmation");
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isPresent());

        HttpResponse<String> duplicate = request("PATCH", "/api/payments/" + paymentId + "/counter",
                staffToken, confirmation);
        assertEquals(200, duplicate.statusCode());
        assertEquals("SUCCESS", objectMapper.readTree(duplicate.body()).path("status").asText());
        assertTrue(memberships.findByRenewalIntent_Id(owner.intent.getId()).isPresent(),
                "Repeated staff confirmation must preserve the one successor");
    }

    @Test
    void concurrentVerifiedSettlementCreatesExactlyOneSuccessor() throws Exception {
        Fixture owner = fixture(true);
        String token = login(owner.user);
        HttpResponse<String> response = request("POST",
                "/api/memberships/renewal-intents/" + owner.intent.getId() + "/attempts", token, null);
        assertEquals(200, response.statusCode());
        MemberRenewalAttemptActionResponse action = objectMapper.readValue(response.body(),
                MemberRenewalAttemptActionResponse.class);
        Payment attempt = payments.findById(action.attempt().paymentId()).orElseThrow();
        String providerReference = "99" + Long.toUnsignedString(System.nanoTime());
        LocalDateTime paidAt = validProviderPaidAt(attempt);
        RenewalSettlementEvidence evidence = RenewalSettlementEvidence.verifiedOnlineCollection(
                owner.intent.getId(), attempt.getId(), QUOTE, "VND", providerReference, paidAt,
                "PHASE8CK_SYNTHETIC_VERIFICATION", "PHASE8CK-" + RUN_ID);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<RenewalSettlementResult> first = workers.submit(() -> settleAfter(ready, start, evidence));
            Future<RenewalSettlementResult> second = workers.submit(() -> settleAfter(ready, start, evidence));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS).outcome().name(),
                    second.get(20, TimeUnit.SECONDS).outcome().name());
            assertEquals(1, outcomes.stream().filter("ACCEPTED"::equals).count());
            assertEquals(1, outcomes.stream().filter("ALREADY_ACCEPTED"::equals).count());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(PaymentStatus.SUCCESS, payments.findById(attempt.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByRenewalIntent_Id(owner.intent.getId()).stream().count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=? AND renewal_settlement_accepted_at IS NOT NULL",
                Integer.class, owner.intent.getId()));
    }

    private RenewalSettlementResult settleAfter(CountDownLatch ready, CountDownLatch start,
            RenewalSettlementEvidence evidence) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Settlement race start gate timed out");
        return settlementService.settle(evidence);
    }

    @AfterAll
    void cleanupOnlyOwnedFixturesAndCompareHistoricalRows() throws Exception {
        if (fixtures.isEmpty()) return;
        Phase8CJUnitCloneGuard.verifyRuntime(dataSource, environment, context);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Fixture fixture : fixtures) {
                if (fixture.intent == null && fixture.source != null && fixture.source.getId() != null) {
                    fixture.intent = intents.findBySourceMembershipId(fixture.source.getId()).orElse(null);
                }
            }
            List<Integer> userIds = fixtures.stream().map(f -> f.user.getId()).distinct().toList();
            List<Integer> intentIds = fixtures.stream().map(f -> f.intent).filter(java.util.Objects::nonNull)
                    .map(MembershipRenewalIntent::getId).filter(java.util.Objects::nonNull).distinct().toList();
            List<Integer> membershipIds = ownedMembershipIds(userIds, intentIds);
            List<Integer> paymentIds = ownedPaymentIds(userIds, intentIds);
            deleteOwnedAuditEntries(userIds, intentIds, paymentIds, membershipIds);
            for (Fixture fixture : fixtures) {
                if (fixture.intent != null && fixture.intent.getId() != null) {
                    Integer owned = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM dbo.membership_renewal_intent ri
                            JOIN dbo.membership src ON src.id=ri.source_membership_id
                            JOIN dbo.users u ON u.id=src.member_id
                            WHERE ri.id=? AND u.email LIKE ? AND u.name LIKE ?
                            """, Integer.class, fixture.intent.getId(), "phase8cj." + RUN_ID + ".%@example.invalid", PREFIX + "%");
                    assertEquals(1, owned, "Refuse cleanup if Renewal fixture ownership changed");
                    jdbc.update("DELETE FROM dbo.membership WHERE renewal_intent_id=?", fixture.intent.getId());
                    jdbc.update("""
                            UPDATE dbo.membership_renewal_intent
                            SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL,
                                closed_at=?, closed_reason='AUTO_RENEW_DISABLED'
                            WHERE id=?
                            """, java.sql.Timestamp.valueOf(LocalDateTime.now(VN).withNano(0)),
                            fixture.intent.getId());
                    jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id=?", fixture.intent.getId());
                    jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE id=?", fixture.intent.getId());
                }
                if (fixture.source != null && fixture.source.getId() != null) {
                    jdbc.update("DELETE FROM dbo.membership WHERE id=?", fixture.source.getId());
                }
                if (fixture.plan != null && fixture.plan.getId() != null) {
                    jdbc.update("DELETE FROM dbo.membership_plan WHERE id=?", fixture.plan.getId());
                }
                if (fixture.user != null && fixture.user.getId() != null) {
                    jdbc.update("DELETE FROM dbo.notification WHERE user_id=?", fixture.user.getId());
                    jdbc.update("DELETE FROM dbo.users WHERE id=?", fixture.user.getId());
                }
            }
        });
        fixtures.clear();
        assertEquals(baseline, snapshotProtectedRows(), "Protected historical rows changed during HTTP verification");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE email LIKE ?",
                Integer.class, "phase8cj." + RUN_ID + ".%@example.invalid"));
    }

    private Fixture fixture(boolean withIntent) {
        LocalDate today = LocalDate.now(VN);
        LocalDateTime now = LocalDateTime.now(VN).withNano(0);
        User member = user("member", fixtureEmail());
        Fixture fixture = new Fixture(member);
        fixtures.add(fixture);
        MembershipPlan plan = new MembershipPlan();
        plan.setName(PREFIX + "-Plan-" + UUID.randomUUID().toString().substring(0, 6));
        plan.setDescription("Phase 5B Final HTTP fixture");
        plan.setPrice(QUOTE); plan.setDurationMonths(2);
        plan.setCourseDiscountPercent(new BigDecimal("10.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(true); plan.setAllowOneOnOneCoaching(true); plan.setStatus("ACTIVE");
        fixture.plan = plans.saveAndFlush(plan);

        LocalDate sourceEnd = today.plusDays(2);
        Membership source = new Membership(); source.setMember(member); source.setPlan(fixture.plan);
        source.setAutoRenew(true); source.setStatus("ACTIVE");
        source.setStartDate(today.minusDays(28)); source.setEndDate(sourceEnd);
        fixture.source = memberships.saveAndFlush(source);
        if (!withIntent) return fixture;

        MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setSourceMembership(fixture.source); intent.setTargetPlan(fixture.plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true); intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(now); intent.setValidFrom(sourceEnd.minusDays(3).atStartOfDay());
        intent.setValidUntil(sourceEnd.atStartOfDay()); intent.setQuoteCapturedAt(now);
        intent.setSourceEndDateSnapshot(sourceEnd); intent.setSuccessorStartDate(sourceEnd);
        intent.setSuccessorEndDate(sourceEnd.plusMonths(2));
        intent.setPlanNameSnapshot(fixture.plan.getName());
        intent.setPlanDescriptionSnapshot(fixture.plan.getDescription());
        intent.setPlanPriceSnapshot(QUOTE); intent.setQuotedAmount(QUOTE); intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(fixture.plan.getCourseDiscountPercent());
        intent.setPtDiscountPercentSnapshot(fixture.plan.getPersonalTrainingDiscountPercent());
        intent.setAllowPtPlanSnapshot(true); intent.setAllowOneOnOneSnapshot(true);
        fixture.intent = intents.saveAndFlush(intent);
        return fixture;
    }

    private User user(String label, String email) {
        return user(label, email, "MEMBER");
    }

    private String fixtureEmail() {
        return "phase8cj." + RUN_ID + "." + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
    }

    private User user(String label, String email, String role) {
        User user = new User(); user.setName(PREFIX + "-" + label); user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(FIXTURE_PASSWORD));
        user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/login", null,
                objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", FIXTURE_PASSWORD)));
        assertEquals(200, response.statusCode());
        JsonNode login = objectMapper.readTree(response.body());
        assertTrue(login.path("accessToken").asText().length() > 20);
        return login.path("accessToken").asText();
    }

    private LocalDateTime validProviderPaidAt(Payment attempt) {
        LocalDateTime now = LocalDateTime.now(VN).withNano(0);
        LocalDateTime firstRepresentableSecondAfterCreation = attempt.getCreatedAt().withNano(0).plusSeconds(1);
        return now.isBefore(attempt.getCreatedAt()) ? firstRepresentableSecondAfterCreation : now;
    }

    private Map<String, String> callbackParameters(String transactionCode, boolean success, String providerRef,
            String signature, LocalDateTime verifiedPaidAt) {
        String paidAt = verifiedPaidAt.format(PROVIDER_TIME);
        return Map.of("transactionCode", transactionCode, "success", Boolean.toString(success),
                "paymentTime", paidAt, "amount", QUOTE.toPlainString(), "currency", "VND",
                "providerTransactionReference", providerRef, "signature", signature);
    }

    private URI callbackUri(String path, Map<String, String> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base() + path);
        parameters.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> attemptAfter(CountDownLatch ready, CountDownLatch start, Fixture fixture, String token)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request start gate timed out");
        return request("POST", "/api/memberships/renewal-intents/" + fixture.intent.getId() + "/attempts", token, null);
    }

    private String base() { return "http://localhost:" + port; }

    private Map<String, Map<String, List<String>>> snapshotProtectedRows() throws Exception {
        Map<String, Map<String, List<String>>> snapshot = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            Phase8CJUnitCloneGuard.verifyIdentity(connection);
            for (String table : PROTECTED) {
                DatabaseMetaData metadata = connection.getMetaData();
                Map<Short, String> keys = new java.util.TreeMap<>();
                try (ResultSet result = metadata.getPrimaryKeys(null, "dbo", table)) {
                    while (result.next()) keys.put(result.getShort("KEY_SEQ"), result.getString("COLUMN_NAME"));
                }
                assertFalse(keys.isEmpty(), "Protected table has no primary key: " + table);
                String order = String.join(",", keys.values().stream().map(key -> "[" + key + "]").toList());
                Map<String, List<String>> rows = new java.util.TreeMap<>();
                try (Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT * FROM dbo.[" + table + "] ORDER BY " + order)) {
                    int count = result.getMetaData().getColumnCount();
                    while (result.next()) {
                        List<String> values = new ArrayList<>(count);
                        for (int i = 1; i <= count; i++) values.add(canonical(result.getObject(i)));
                        String key = String.join("|", keys.values().stream().map(name -> {
                            try {
                                for (int i = 1; i <= count; i++)
                                    if (name.equalsIgnoreCase(result.getMetaData().getColumnLabel(i))) return values.get(i - 1);
                                throw new IllegalStateException("Missing primary key field " + name);
                            } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
                        }).toList());
                        assertNull(rows.put(key, List.copyOf(values)), "Duplicate primary key in " + table);
                    }
                }
                snapshot.put(table, rows);
            }
        }
        return snapshot;
    }

    /** Remove only the uniquely marked HTTP fixtures left by an interrupted run of this test class. */
    private void cleanupPriorMarkedFixtures() throws Exception {
        assertFalse(RUN_ID.isBlank(), "Refuse orphan cleanup without an explicit run identifier");
        List<Integer> userIds = jdbc.queryForList("""
                SELECT id FROM dbo.users WHERE name LIKE ? AND email LIKE ? ORDER BY id
                """, Integer.class, RUN_ID + "-%", "phase8cj." + RUN_ID + ".%@example.invalid");
        List<Integer> planIds = jdbc.queryForList("""
                SELECT id FROM dbo.membership_plan WHERE name LIKE ? ORDER BY id
                """, Integer.class, RUN_ID + "-%");
        if (userIds.isEmpty() && planIds.isEmpty()) return;
        List<Integer> sourceIds = new ArrayList<>();
        if (!planIds.isEmpty()) {
            for (Integer userId : userIds) {
                for (Integer planId : planIds) {
                    sourceIds.addAll(jdbc.queryForList(
                            "SELECT id FROM dbo.membership WHERE member_id=? AND plan_id=? ORDER BY id",
                            Integer.class, userId, planId));
                }
            }
        }
        List<Integer> intentIds = new ArrayList<>();
        for (Integer sourceId : sourceIds) {
            intentIds.addAll(jdbc.queryForList(
                    "SELECT id FROM dbo.membership_renewal_intent WHERE source_membership_id=? ORDER BY id",
                    Integer.class, sourceId));
        }
        List<Integer> membershipIds = ownedMembershipIds(userIds, intentIds);
        List<Integer> paymentIds = ownedPaymentIds(userIds, intentIds);
        for (Integer userId : userIds) {
            Integer unrelatedMemberships = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.membership
                    WHERE member_id=? AND COALESCE(plan_id, -1) NOT IN ("""
                    + (planIds.isEmpty() ? "-1" : String.join(",", java.util.Collections.nCopies(planIds.size(), "?")))
                    + ") AND COALESCE(renewal_intent_id, -1) NOT IN ("
                    + (intentIds.isEmpty() ? "-1" : String.join(",", java.util.Collections.nCopies(intentIds.size(), "?")))
                    + ")", Integer.class,
                    java.util.stream.Stream.concat(java.util.stream.Stream.of(userId),
                            java.util.stream.Stream.concat(planIds.stream(), intentIds.stream())).toArray());
            assertEquals(0, unrelatedMemberships, "Refuse cleanup of unmarked Membership references");
            Integer unrelatedPayments = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.payment
                    WHERE (member_id=? OR processed_by=?) AND COALESCE(renewal_intent_id, -1) NOT IN ("""
                    + (intentIds.isEmpty() ? "-1" : String.join(",", java.util.Collections.nCopies(intentIds.size(), "?")))
                    + ")", Integer.class,
                    java.util.stream.Stream.concat(java.util.stream.Stream.of(userId, userId), intentIds.stream()).toArray());
            assertEquals(0, unrelatedPayments, "Refuse cleanup of unmarked Payment references");
        }
        try {
            Phase8CJUnitCloneGuard.verifyRuntime(dataSource, environment, context);
        } catch (Exception failure) {
            throw new IllegalStateException("Clone identity recheck failed before prior-fixture cleanup", failure);
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deleteOwnedAuditEntries(userIds, intentIds, paymentIds, membershipIds);
            for (Integer intentId : intentIds) {
                jdbc.update("DELETE FROM dbo.membership WHERE renewal_intent_id=?", intentId);
                jdbc.update("""
                        UPDATE dbo.membership_renewal_intent
                        SET intent_status='CANCELLED', settled_payment_id=NULL, settled_at=NULL,
                            closed_at=?, closed_reason='AUTO_RENEW_DISABLED'
                        WHERE id=?
                        """, java.sql.Timestamp.valueOf(LocalDateTime.now(VN).withNano(0)), intentId);
                jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id=?", intentId);
                jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE id=?", intentId);
            }
            for (Integer userId : userIds) {
                for (Integer planId : planIds) {
                    jdbc.update("DELETE FROM dbo.membership WHERE member_id=? AND plan_id=?", userId, planId);
                }
                jdbc.update("DELETE FROM dbo.notification WHERE user_id=?", userId);
            }
            for (Integer planId : planIds) jdbc.update("DELETE FROM dbo.membership_plan WHERE id=?", planId);
            for (Integer userId : userIds) jdbc.update("DELETE FROM dbo.users WHERE id=?", userId);
        });
    }

    private List<Integer> ownedMembershipIds(List<Integer> userIds, List<Integer> intentIds) {
        if (userIds.isEmpty() && intentIds.isEmpty()) return List.of();
        String userClause = userIds.isEmpty() ? "1=0" : "member_id IN (" + marks(userIds.size()) + ")";
        String intentClause = intentIds.isEmpty() ? "1=0" : "renewal_intent_id IN (" + marks(intentIds.size()) + ")";
        Object[] args = java.util.stream.Stream.concat(userIds.stream(), intentIds.stream()).toArray();
        return jdbc.queryForList("SELECT id FROM dbo.membership WHERE (" + userClause + ") OR (" + intentClause + ") ORDER BY id",
                Integer.class, args);
    }

    private List<Integer> ownedPaymentIds(List<Integer> userIds, List<Integer> intentIds) {
        if (userIds.isEmpty() && intentIds.isEmpty()) return List.of();
        String memberClause = userIds.isEmpty() ? "1=0" : "member_id IN (" + marks(userIds.size()) + ")";
        String intentClause = intentIds.isEmpty() ? "1=0" : "renewal_intent_id IN (" + marks(intentIds.size()) + ")";
        Object[] args = java.util.stream.Stream.concat(userIds.stream(), intentIds.stream()).toArray();
        return jdbc.queryForList("SELECT id FROM dbo.payment WHERE (" + memberClause + ") OR (" + intentClause + ") ORDER BY id",
                Integer.class, args);
    }

    private void deleteOwnedAuditEntries(List<Integer> userIds, List<Integer> intentIds,
            List<Integer> paymentIds, List<Integer> membershipIds) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (!userIds.isEmpty()) {
            conditions.add("actor_user_id IN (" + marks(userIds.size()) + ")");
            args.addAll(userIds);
        }
        addAuditEntityCondition(conditions, args, "RENEWAL_INTENT", intentIds);
        addAuditEntityCondition(conditions, args, "MEMBERSHIP_RENEWAL_INTENT", intentIds);
        addAuditEntityCondition(conditions, args, "RENEWAL_PAYMENT", paymentIds);
        addAuditEntityCondition(conditions, args, "PAYMENT", paymentIds);
        addAuditEntityCondition(conditions, args, "MEMBERSHIP", membershipIds);
        if (!conditions.isEmpty()) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE " + String.join(" OR ", conditions), args.toArray());
        }
    }

    private void addAuditEntityCondition(List<String> conditions, List<Object> args,
            String entityType, List<Integer> ids) {
        if (ids.isEmpty()) return;
        conditions.add("(entity_type=? AND entity_id IN (" + marks(ids.size()) + "))");
        args.add(entityType);
        ids.forEach(id -> args.add(Integer.toString(id)));
    }

    private static String marks(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private String canonical(Object value) {
        if (value == null) return "<NULL>";
        if (value instanceof byte[] bytes) return "B64:" + Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.math.BigDecimal decimal) return "DEC:" + decimal.toPlainString();
        if (value instanceof java.sql.Timestamp timestamp) return "TS:" + timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return "D:" + date.toLocalDate();
        if (value instanceof java.sql.Time time) return "T:" + time.toLocalTime();
        return value.getClass().getName() + ":" + value;
    }

    /** Independent target guard for the explicitly approved current QA clone. */
    public static final class Phase8CJUnitCloneGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final String URL = "jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo_PTOutcome_8CF1;"
                + "encrypt=true;trustServerCertificate=true";
        private static final String SERVER = "LAPTOP-416VBDLK";
        private static final String DATABASE = "SportCenterDemo_PTOutcome_8CF1";

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            if (!URL.equals(env.getProperty("spring.datasource.url"))
                    || !"validate".equalsIgnoreCase(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"false".equalsIgnoreCase(env.getProperty("sportcenter.scheduling.enabled"))
                    || !"false".equalsIgnoreCase(env.getProperty("sportcenter.renewal.scheduler.enabled"))
                    || !"false".equalsIgnoreCase(env.getProperty("sportcenter.pt.outcome-fallback.enabled"))
                    || !"mock".equals(env.getProperty("app.payment.provider"))
                    || !"true".equalsIgnoreCase(env.getProperty("app.payment.mock-gateway.enabled"))) {
                throw new IllegalStateException("Phase 8C-J refused: effective runtime configuration is not clone-safe");
            }
            String username = env.getProperty("spring.datasource.username");
            String password = env.getProperty("spring.datasource.password");
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException("Phase 8C-J refused: datasource credentials unavailable");
            }
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("encrypt", "true");
            props.setProperty("trustServerCertificate", "true");
            try (Connection connection = DriverManager.getConnection(URL, props)) {
                verifyIdentity(connection);
            } catch (Exception failure) {
                throw new IllegalStateException("Phase 8C-J clone identity check failed before context startup", failure);
            }
        }

        static void verifyRuntime(DataSource dataSource, Environment env, ApplicationContext context) throws Exception {
            if (!URL.equals(env.getProperty("spring.datasource.url"))
                    || !"validate".equalsIgnoreCase(env.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new IllegalStateException("Phase 8C-J effective target/schema mode changed");
            }
            if (!context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty()) {
                throw new IllegalStateException("Phase 8C-J scheduling infrastructure unexpectedly active");
            }
            try (Connection connection = dataSource.getConnection()) { verifyIdentity(connection); }
        }

        static void verifyIdentity(Connection connection) throws Exception {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                    "SELECT SERVERPROPERTY('ServerName'), SERVERPROPERTY('ProductVersion'), DB_NAME(), DB_ID(), "
                            + "CONVERT(nvarchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status'))")) {
                if (!result.next() || !SERVER.equals(result.getString(1)) || !"17.0.1155.3".equals(result.getString(2))
                        || !DATABASE.equals(result.getString(3)) || result.getInt(4) != 12
                        || !"ONLINE".equalsIgnoreCase(result.getString(5)) || result.next()) {
                    throw new IllegalStateException("SQL connection is not the explicitly approved QA clone");
                }
            }
        }
    }

    @TestConfiguration
    static class TestGatewayConfiguration {
        @Bean @Primary PaymentGateway simulatedRenewalGateway() { return new SimulatedRenewalGateway(); }
    }

    static final class SimulatedRenewalGateway implements PaymentGateway {
        private final VnpayPaymentGateway vnpayVerifier = new VnpayPaymentGateway(
                SYNTHETIC_VNPAY_TMN, SYNTHETIC_VNPAY_SECRET,
                "http://127.0.0.1/unused-test-pay-url", "http://127.0.0.1/unused-test-return-url", 15,
                Clock.systemUTC());

        @Override public String createPaymentUrl(String transactionCode, BigDecimal amount, String clientIp) {
            return "http://127.0.0.1/test-checkout?transactionCode=" + transactionCode;
        }
        @Override public Optional<GatewayCallback> verifyCallback(Map<String, String> params) {
            if (params.keySet().stream().anyMatch(key -> key.startsWith("vnp_"))) {
                return vnpayVerifier.verifyCallback(params);
            }
            if (!TEST_SIGNATURE.equals(params.get("signature"))) return Optional.empty();
            try {
                LocalDateTime paidAt = LocalDateTime.parse(params.get("paymentTime"), PROVIDER_TIME);
                return Optional.of(new GatewayCallback(params.get("transactionCode"),
                        Boolean.parseBoolean(params.get("success")), new BigDecimal(params.get("amount")), null,
                        params.get("paymentTime"), params.get("providerTransactionReference"),
                        params.get("currency"), true, paidAt, GatewayCallback.PaidTimeStatus.VALID));
            } catch (RuntimeException malformed) { return Optional.empty(); }
        }
    }

    private static final class Fixture {
        final User user;
        MembershipPlan plan;
        Membership source;
        MembershipRenewalIntent intent;
        Fixture(User user) { this.user = user; }
    }
}
