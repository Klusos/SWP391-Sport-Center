package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipPlanUpgrade;
import group5.example.sportCenter.entity.MembershipUpgradePaymentEvidence;
import group5.example.sportCenter.entity.MembershipUpgradeCheckoutAttempt;
import group5.example.sportCenter.entity.MembershipUpgradeCounterReceipt;
import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.CheckoutProvider;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.dto.CreateMembershipUpgradePaymentRequest;
import group5.example.sportCenter.dto.MembershipUpgradePaymentResponse;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.payment.VnpayPaymentGateway;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCaptureEvidenceRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCheckoutAttemptRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCounterReceiptRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(MembershipUpgradePaymentIntegrationTest.TestClockConfiguration.class)
class MembershipUpgradePaymentIntegrationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 15);

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2024-02-15T05:00:00Z"));
        @Override public ZoneId getZone() { return VN; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
        void set(Instant value) { instant.set(value); }
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary MutableClock upgradePaymentTestClock() { return new MutableClock(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipPlanUpgradeRepository paths;
    @Autowired PaymentRepository payments;
    @Autowired MembershipUpgradePaymentEvidenceRepository evidence;
    @Autowired MembershipUpgradeCaptureEvidenceRepository captureEvidence;
    @Autowired MembershipUpgradeCheckoutAttemptRepository checkoutAttempts;
    @Autowired MembershipUpgradeCounterReceiptRepository counterReceipts;
    @Autowired MutableClock clock;
    @Autowired MembershipUpgradePaymentService upgradePayments;
    @Autowired MembershipUpgradeCheckoutService upgradeCheckout;
    @Autowired PaymentService paymentService;
    @Autowired MembershipUpgradeSettlementService upgradeSettlement;
    @Autowired MembershipRenewalService renewalService;
    @MockitoSpyBean PaymentGateway gateway;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2024-02-15T05:00:00Z"));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void verifiedUpgradeCaptureReplacesMembershipImmediatelyAndIsIdempotent() {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        var draft = upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));

        String transactionCode = payments.findById(draft.paymentId()).orElseThrow().getTransactionCode();
        var capture = verifiedCapture(transactionCode, draft.amount(), "20240215120000");
        var applied = upgradeSettlement.settleVerifiedOnlineCapture(capture);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.APPLIED, applied.outcome());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(draft.paymentId()).orElseThrow().getStatus());
        Membership replaced = memberships.findById(source.getId()).orElseThrow();
        assertEquals("REPLACED", replaced.getStatus());
        assertEquals(TODAY, replaced.getReplacedOn());
        assertEquals(LocalDate.of(2024, 2, 29), replaced.getEndDate(), "Original paid period end date remains intact");
        Membership created = memberships.findById(applied.membershipId()).orElseThrow();
        assertEquals(target.getId(), created.getPlan().getId());
        assertEquals(TODAY, created.getStartDate());
        assertEquals(TODAY.plusMonths(1), created.getEndDate());
        assertTrue(created.getAutoRenew());
        assertEquals(draft.paymentId(), created.getSourcePayment().getId());
        assertEquals(created.getId(), evidence.findByPayment_Id(draft.paymentId()).orElseThrow().getAppliedMembership().getId());
        assertTrue(memberships.findActiveAndValidForPt(member.getId(),TODAY).stream()
                .noneMatch(m -> m.getId().equals(replaced.getId())),"REPLACED history must not grant date-based membership benefits");
        assertTrue(memberships.findRenewalCandidateIds(TODAY,TODAY.plusDays(3)).stream()
                .noneMatch(id -> id.equals(replaced.getId())),"REPLACED history must not enter Auto Renew scheduling");
        assertEquals(MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED,
                upgradeSettlement.settleVerifiedOnlineCapture(capture).outcome());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void failedCallbackAfterUpgradeSuccessCannotDowngradePaymentOrReplacement() {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        var draft = upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));
        Payment pending = payments.findById(draft.paymentId()).orElseThrow();
        var success = verifiedCapture(pending.getTransactionCode(), draft.amount(), "20240215120000");
        assertEquals(MembershipUpgradeSettlementResult.Outcome.APPLIED,
                upgradeSettlement.settleVerifiedOnlineCapture(success).outcome());

        var failedEvidence=verifiedFailure(success.transactionCode(),pending.getAmount());
        var failed = new GatewayCallback(success.transactionCode(), false, pending.getAmount(), "declined",
                "provider-failed-later", "24", "02", success.transactionCode(),null,failedEvidence);
        org.mockito.Mockito.doReturn(Optional.of(failed)).when(gateway).verifyCallback(any());
        var callbackResult = context.getBean(PaymentService.class).processVnpayIpn(Map.of("vnp_TxnRef",success.transactionCode()));

        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, callbackResult.status());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals(2, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void concurrentDuplicateCapturesCreateOnlyOneReplacement() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        var draft = upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));
        var capture = verifiedCapture(payments.findById(draft.paymentId()).orElseThrow().getTransactionCode(),
                draft.amount(), "20240215120000");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MembershipUpgradeSettlementResult> one = pool.submit(() -> settleAfterBarrier(capture, ready, start));
            Future<MembershipUpgradeSettlementResult> two = pool.submit(() -> settleAfterBarrier(capture, ready, start));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var first = one.get();
            var second = two.get();
            assertTrue(java.util.Set.of(MembershipUpgradeSettlementResult.Outcome.APPLIED,
                    MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED).contains(first.outcome()), first + " / " + second);
            assertTrue(java.util.Set.of(MembershipUpgradeSettlementResult.Outcome.APPLIED,
                    MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED).contains(second.outcome()));
            assertEquals(2, memberships.findByMemberId(member.getId()).size());
            assertEquals(PaymentStatus.SUCCESS, payments.findById(draft.paymentId()).orElseThrow().getStatus());
        } finally { pool.shutdownNow(); }
    }

    private MembershipUpgradeSettlementResult settleAfterBarrier(
            group5.example.sportCenter.payment.VerifiedUpgradeCapture capture,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { start.await(); return upgradeSettlement.settleVerifiedOnlineCapture(capture); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    @Test
    void captureAtExpiryIsDurablyReconciledWithoutCreatingMembership() {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        var draft = upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));
        String transactionCode = payments.findById(draft.paymentId()).orElseThrow().getTransactionCode();
        var capture = verifiedCapture(transactionCode, draft.amount(), "20240215121500");

        var result = upgradeSettlement.settleVerifiedOnlineCapture(capture);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals("RECONCILIATION_REQUIRED", captureEvidence.findByUpgradeEvidence_Payment_Id(draft.paymentId())
                .get(0).getOutcome());
    }

    @Test
    void preMidnightCaptureReportedAfterSourceExpiryIsNotBackdated() {
        clock.set(Instant.parse("2024-02-15T16:50:00Z")); // 23:50 in Vietnam
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 16), LocalDate.of(2024, 2, 16), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        var draft = upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));
        String transactionCode = payments.findById(draft.paymentId()).orElseThrow().getTransactionCode();
        var capture = verifiedCapture(transactionCode, draft.amount(), "20240215235900");
        clock.set(Instant.parse("2024-02-15T17:01:00Z")); // callback received at 00:01 the next Vietnam date

        var result = upgradeSettlement.settleVerifiedOnlineCapture(capture);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(LocalDate.of(2024, 2, 15), captureEvidence.findByUpgradeEvidence_Payment_Id(draft.paymentId())
                .get(0).getCapturedAtUtc().atZone(VN).toLocalDate());
    }

    @Test
    void missingProviderCaptureTimeAndAmountMismatchRequireReconciliation() {
        User noTimeMember = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(noTimeMember, sourcePlan, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt noTimeJwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(noTimeMember.getId().toString()).build();
        var noTimeDraft = upgradePayments.create(noTimeJwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.ONLINE));
        String noTimeCode = payments.findById(noTimeDraft.paymentId()).orElseThrow().getTransactionCode();
        var noTimeCapture = verifiedCapture(noTimeCode, noTimeDraft.amount(), null);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                upgradeSettlement.settleVerifiedOnlineCapture(noTimeCapture).outcome());
        assertEquals(1, memberships.findByMemberId(noTimeMember.getId()).size());

        User mismatchMember = user("MEMBER");
        MembershipPlan secondSource = plan("100.00", "ACTIVE");
        MembershipPlan secondTarget = plan("500.00", "ACTIVE");
        paidMembership(mismatchMember, secondSource, LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), new BigDecimal("100.00"));
        path(secondSource, secondTarget);
        Jwt mismatchJwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(mismatchMember.getId().toString()).build();
        var mismatchDraft = upgradePayments.create(mismatchJwt, new CreateMembershipUpgradePaymentRequest(secondTarget.getId(), PaymentMethod.ONLINE));
        String mismatchCode = payments.findById(mismatchDraft.paymentId()).orElseThrow().getTransactionCode();
        var mismatchCapture = verifiedCapture(mismatchCode, mismatchDraft.amount().add(new BigDecimal("1.00")), "20240215120000");
        assertEquals(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                upgradeSettlement.settleVerifiedOnlineCapture(mismatchCapture).outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payments.findById(mismatchDraft.paymentId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(mismatchMember.getId()).size());
    }

    @Test
    void noControllerEndpointCanForceUpgradeSettlement() throws Exception {
        User member = user("MEMBER");
        mvc.perform(post("/api/memberships/me/upgrade-settlement")
                        .header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"success\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlineCheckoutIsOwnerScopedAndReusesTheSameLivePersistedAttempt() throws Exception {
        User member=user("MEMBER"), other=user("MEMBER");
        MembershipPlan sourcePlan=plan("100.00","ACTIVE"), target=plan("500.00","ACTIVE");
        paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00"));
        path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        var draft=upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.ONLINE));
        doReturn(true).when(gateway).supportsUpgradeCheckout();
        doReturn("https://sandbox.example.test/pay?ref=UGP").when(gateway)
                .createUpgradePaymentUrl(anyString(),any(BigDecimal.class),anyString(),any(),any());
        mvc.perform(post("/api/payments/{id}/upgrade-checkout",draft.paymentId())
                        .header("Authorization",bearer(other)))
                .andExpect(status().isForbidden());
        var first=mvc.perform(post("/api/payments/{id}/upgrade-checkout",draft.paymentId())
                        .header("Authorization",bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(draft.paymentId()))
                .andExpect(jsonPath("$.attemptReference").value(org.hamcrest.Matchers.startsWith("UGP")))
                .andReturn();
        String firstBody=first.getResponse().getContentAsString();
        var second=mvc.perform(post("/api/payments/{id}/upgrade-checkout",draft.paymentId())
                        .header("Authorization",bearer(member)))
                .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);
        assertEquals(1,checkoutAttempts.findByPayment_IdOrderByCreatedAtUtcDescIdDesc(draft.paymentId()).size());
    }

    @Test
    void signedUpgradeIpnUsesPersistedUgpAttemptAndBrowserReturnNeverSettles() throws Exception {
        User member=user("MEMBER");
        MembershipPlan sourcePlan=plan("100.00","ACTIVE"), target=plan("500.00","ACTIVE");
        Membership source=paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00"));
        path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        var draft=upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.ONLINE));
        mvc.perform(get("/api/payments/vnpay/return").param("vnp_TxnRef","UGP"+"A".repeat(32))
                        .param("vnp_ResponseCode","00"))
                .andExpect(status().isFound());
        assertEquals(PaymentStatus.PENDING,payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals("ACTIVE",memberships.findById(source.getId()).orElseThrow().getStatus());

        var capture=verifiedCapture(payments.findById(draft.paymentId()).orElseThrow().getTransactionCode(),draft.amount(),"20240215120000");
        String ref=capture.transactionCode();
        Map<String,String> params=Map.of("vnp_TxnRef",ref);
        GatewayCallback callback=new GatewayCallback(ref,true,draft.amount(),null,"provider-upg-unique","00","00",ref,capture,null);
        doReturn(Optional.of(callback)).when(gateway).verifyCallback(params);
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.APPLIED,
                paymentService.processVnpayIpn(params).status());
        assertEquals(PaymentStatus.SUCCESS,payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals("REPLACED",memberships.findById(source.getId()).orElseThrow().getStatus());
    }

    @Test
    void receptionistCounterConfirmationRecordsStaffReceiptAndAppliesUpgrade() throws Exception {
        User member=user("MEMBER"), receptionist=user("RECEPTIONIST");
        MembershipPlan sourcePlan=plan("100.00","ACTIVE"), target=plan("500.00","ACTIVE");
        Membership source=paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00"));
        path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        var draft=upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.COUNTER));
        mvc.perform(patch("/api/payments/{id}/counter",draft.paymentId()).header("Authorization",bearer(member))
                        .contentType("application/json").content("{\"success\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/payments/{id}/counter",draft.paymentId()).header("Authorization",bearer(receptionist))
                        .contentType("application/json").content("{\"success\":true,\"note\":\"cash receipt verified\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals("REPLACED",memberships.findById(source.getId()).orElseThrow().getStatus());
        MembershipUpgradeCounterReceipt receipt=counterReceipts.findByPayment_Id(draft.paymentId()).orElseThrow();
        assertEquals(receptionist.getId(),receipt.getStaff().getId());
        assertEquals(draft.amount(),receipt.getAmount());
    }

    @Test
    void verifiedFailedUgpIpnClosesOnlyTheAttemptAndLeavesPaymentPending() {
        User member=user("MEMBER"); MembershipPlan sourcePlan=plan("100.00","ACTIVE"),target=plan("500.00","ACTIVE");
        paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00")); path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        var draft=upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.ONLINE));
        String code=payments.findById(draft.paymentId()).orElseThrow().getTransactionCode();
        var successfulShape=verifiedCapture(code,draft.amount(),"20240215120000");
        String reference=successfulShape.transactionCode();
        var failure=verifiedFailure(reference,draft.amount());
        Map<String,String> params=Map.of("vnp_TxnRef",reference);
        doReturn(Optional.of(new GatewayCallback(reference,false,draft.amount(),"declined","provider-declined",
                "24","02",reference,null,failure))).when(gateway).verifyCallback(params);
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.APPLIED,
                paymentService.processVnpayIpn(params).status());
        assertEquals(PaymentStatus.PENDING,payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals(CheckoutAttemptStatus.FAILED,checkoutAttempts.findByTransactionReference(reference).orElseThrow().getStatus());
        assertEquals("FAILED",captureEvidence.findByUpgradeEvidence_Payment_Id(draft.paymentId()).get(0).getOutcome());
    }

    @Test
    void invalidSignatureUnknownAttemptAndAmountMismatchFailClosed() {
        Map<String,String> invalid=Map.of("vnp_TxnRef","UGP"+"A".repeat(32));
        doReturn(Optional.empty()).when(gateway).verifyCallback(invalid);
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.INVALID_SIGNATURE,
                paymentService.processVnpayIpn(invalid).status());

        String unknownRef="UGP"+UUID.randomUUID().toString().replace("-","").toUpperCase();
        GatewayCallback unknown=signedUgpCallback(unknownRef,new BigDecimal("10.00"),"00","00","20240215120000","unknown-provider");
        Map<String,String> unknownParams=Map.of("vnp_TxnRef",unknownRef);
        doReturn(Optional.of(unknown)).when(gateway).verifyCallback(unknownParams);
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.NOT_FOUND,
                paymentService.processVnpayIpn(unknownParams).status());

        User member=user("MEMBER"); MembershipPlan sourcePlan=plan("100.00","ACTIVE"),target=plan("500.00","ACTIVE");
        paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00")); path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        var draft=upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.ONLINE));
        var shape=verifiedCapture(payments.findById(draft.paymentId()).orElseThrow().getTransactionCode(),draft.amount(),"20240215120000");
        String ref=shape.transactionCode();
        GatewayCallback mismatch=signedUgpCallback(ref,draft.amount().add(new BigDecimal("1.00")),"00","00","20240215120000","mismatched-provider");
        Map<String,String> mismatchParams=Map.of("vnp_TxnRef",ref);
        doReturn(Optional.of(mismatch)).when(gateway).verifyCallback(mismatchParams);
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.AMOUNT_MISMATCH,
                paymentService.processVnpayIpn(mismatchParams).status());
        assertEquals(PaymentStatus.REFUND_REQUIRED,payments.findById(draft.paymentId()).orElseThrow().getStatus());
        assertEquals("RECONCILIATION_REQUIRED",captureEvidence.findByUpgradeEvidence_Payment_Id(draft.paymentId()).get(0).getOutcome());
    }

    @Test
    void providerTransactionIdCannotBeAppliedToTwoUpgradePayments() {
        var first=onlineDraftWithSource();
        String firstCode=payments.findById(first.paymentId()).orElseThrow().getTransactionCode();
        var firstAttemptShape=verifiedCapture(firstCode,first.amount(),"20240215120000");
        String providerId="provider-shared-upgrade-id";
        GatewayCallback firstCallback=signedUgpCallback(firstAttemptShape.transactionCode(),first.amount(),"00","00","20240215120000",providerId);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.APPLIED,
                upgradeSettlement.settleVerifiedOnlineCapture(firstCallback.verifiedUpgradeCapture()).outcome());

        var second=onlineDraftWithSource();
        String secondCode=payments.findById(second.paymentId()).orElseThrow().getTransactionCode();
        var secondAttemptShape=verifiedCapture(secondCode,second.amount(),"20240215120000");
        GatewayCallback reused=signedUgpCallback(secondAttemptShape.transactionCode(),second.amount(),"00","00","20240215120000",providerId);
        assertEquals(MembershipUpgradeSettlementResult.Outcome.RECONCILIATION_REQUIRED,
                upgradeSettlement.settleVerifiedOnlineCapture(reused.verifiedUpgradeCapture()).outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED,payments.findById(second.paymentId()).orElseThrow().getStatus());
        assertEquals(1,memberships.findByMemberId(payments.findById(second.paymentId()).orElseThrow().getMember().getId()).size());
    }

    private MembershipUpgradePaymentResponse onlineDraftWithSource() {
        User member=user("MEMBER"); MembershipPlan sourcePlan=plan("100.00","ACTIVE"),target=plan("500.00","ACTIVE");
        paidMembership(member,sourcePlan,LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),new BigDecimal("100.00")); path(sourcePlan,target);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none").subject(member.getId().toString()).build();
        return upgradePayments.create(jwt,new CreateMembershipUpgradePaymentRequest(target.getId(),PaymentMethod.ONLINE));
    }

    private GatewayCallback signedUgpCallback(String reference, BigDecimal amount, String responseCode,
            String transactionStatus, String payDate, String providerId) {
        Map<String,String> params=new TreeMap<>(); params.put("vnp_Version","2.1.0"); params.put("vnp_Command","pay");
        params.put("vnp_TmnCode","test-tmn"); params.put("vnp_TxnRef",reference);
        params.put("vnp_Amount",amount.movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode",responseCode); params.put("vnp_TransactionStatus",transactionStatus);
        params.put("vnp_TransactionNo",providerId); params.put("vnp_CurrCode","VND"); params.put("vnp_OrderInfo","UGP fixture");
        if(payDate!=null) params.put("vnp_PayDate",payDate); params.put("vnp_SecureHash",signVnpay(params));
        return new VnpayPaymentGateway("test-tmn","test-secret","https://unused.invalid","http://localhost/return",15,clock)
                .verifyCallback(params).orElseThrow();
    }

    private group5.example.sportCenter.payment.VerifiedUpgradeFailure verifiedFailure(String reference, BigDecimal amount) {
        Map<String,String> params=new TreeMap<>(); params.put("vnp_Version","2.1.0"); params.put("vnp_Command","pay");
        params.put("vnp_TmnCode","test-tmn"); params.put("vnp_TxnRef",reference);
        params.put("vnp_Amount",amount.movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode","24"); params.put("vnp_TransactionStatus","02");
        params.put("vnp_TransactionNo","provider-declined"); params.put("vnp_CurrCode","VND");
        params.put("vnp_OrderInfo","declined fixture"); params.put("vnp_SecureHash",signVnpay(params));
        return new VnpayPaymentGateway("test-tmn","test-secret","https://unused.invalid","http://localhost/return",15,clock)
                .verifyCallback(params).orElseThrow().verifiedUpgradeFailure();
    }

    private group5.example.sportCenter.payment.VerifiedUpgradeCapture verifiedCapture(
            String transactionCode, BigDecimal amount, String payDate) {
        Payment payment=payments.findByTransactionCode(transactionCode).orElseThrow();
        MembershipUpgradeCheckoutAttempt attempt=checkoutAttempts.findByPayment_IdOrderByCreatedAtUtcDescIdDesc(payment.getId())
                .stream().findFirst().orElse(null);
        if(attempt==null) {
            MembershipUpgradePaymentEvidence snapshot=evidence.findByPayment_Id(payment.getId()).orElseThrow();
            attempt=new MembershipUpgradeCheckoutAttempt(); attempt.setPayment(payment); attempt.setProvider(CheckoutProvider.VNPAY);
            attempt.setTransactionReference("UGP"+UUID.randomUUID().toString().replace("-", "").toUpperCase());
            attempt.setAmount(payment.getAmount()); attempt.setCreatedAtUtc(snapshot.getCreatedAtUtc());
            attempt.setExpiresAtUtc(snapshot.getExpiresAtUtc()); attempt.setCheckoutUrl("https://example.test/checkout");
            attempt.setStatus(CheckoutAttemptStatus.URL_ISSUED); attempt=checkoutAttempts.saveAndFlush(attempt);
        }
        String rawReference = attempt.getTransactionReference();
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay"); params.put("vnp_TmnCode", "test-tmn");
        params.put("vnp_TxnRef", rawReference); params.put("vnp_Amount", amount.movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "provider-" + UUID.randomUUID());
        if (payDate != null) params.put("vnp_PayDate", payDate);
        params.put("vnp_CurrCode", "VND"); params.put("vnp_OrderInfo", "test");
        params.put("vnp_SecureHash", signVnpay(params));
        return new VnpayPaymentGateway("test-tmn", "test-secret", "https://unused.invalid", "http://localhost/return", 15, clock)
                .verifyCallback(params).orElseThrow().verifiedUpgradeCapture();
    }

    private static String signVnpay(Map<String, String> params) {
        try {
            var sorted = new TreeMap<>(params);
            String query = sorted.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.US_ASCII))
                    .collect(java.util.stream.Collectors.joining("&"));
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(query.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    @Test
    void createsMemberOwnedPendingDraftWithImmutableQuoteEvidenceAndNoCheckout() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan targetPlan = plan("500.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, TODAY.minusDays(14), TODAY.minusDays(14).plusMonths(1),
                new BigDecimal("310.00"));
        path(sourcePlan, targetPlan);
        long membershipCount = memberships.count();

        mvc.perform(post("/api/memberships/me/upgrade-payments")
                        .header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"targetPlanId\":" + targetPlan.getId() + ",\"paymentMethod\":\"ONLINE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purpose").value("UPGRADE"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.sourceMembershipId").value(source.getId()))
                .andExpect(jsonPath("$.targetPlanId").value(targetPlan.getId()))
                .andExpect(jsonPath("$.amount").value(339.66))
                .andExpect(jsonPath("$.sourcePaidAmountSnapshot").value(310.00))
                .andExpect(jsonPath("$.remainingCreditSnapshot").value(160.34))
                .andExpect(jsonPath("$.expiresAt").value("2024-02-15T05:15:00Z"))
                .andExpect(jsonPath("$.checkoutIssued").value(false))
                .andExpect(jsonPath("$.payable").value(false));

        Payment draft = payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).get(0);
        MembershipUpgradePaymentEvidence snapshot = evidence.findByPayment_Id(draft.getId()).orElseThrow();
        assertEquals(targetPlan.getId(), draft.getReferenceId());
        assertEquals(PaymentMethod.ONLINE, draft.getMethod());
        assertEquals(new BigDecimal("339.66"), draft.getAmount());
        assertEquals(TODAY, snapshot.getIssuanceBusinessDate());
        assertEquals(29, snapshot.getOriginalDurationDays());
        assertEquals(15, snapshot.getRemainingDays());
        assertEquals(Instant.parse("2024-02-15T05:00:00Z"), snapshot.getCreatedAtUtc());
        assertEquals(Instant.parse("2024-02-15T05:15:00Z"), snapshot.getExpiresAtUtc());
        assertEquals(membershipCount, memberships.count());
        Membership unchanged = memberships.findById(source.getId()).orElseThrow();
        assertEquals(sourcePlan.getId(), unchanged.getPlan().getId());
        assertEquals(TODAY.minusDays(14), unchanged.getStartDate());
        verify(gateway, never()).createPaymentUrl(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void rejectsNonMembersUnconfiguredOrInactiveTargetsAndConflictingMembershipState() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isConflict());

        User receptionist = user("RECEPTIONIST");
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(receptionist))
                        .contentType("application/json").content(body(target, "ONLINE")))
                .andExpect(status().isForbidden());

        User noPathMember = user("MEMBER");
        paidMembership(noPathMember, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(noPathMember))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isConflict());

        User futureMember = user("MEMBER");
        paidMembership(futureMember, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        Membership future = new Membership();
        future.setMember(futureMember); future.setPlan(sourcePlan); future.setStatus("ACTIVE"); future.setAutoRenew(false);
        future.setStartDate(TODAY.plusDays(28)); future.setEndDate(TODAY.plusDays(58));
        memberships.saveAndFlush(future);
        path(sourcePlan, target);
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(futureMember))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isConflict());
    }

    @Test
    void capsExpiryAtVietnamMidnightAndRejectsTooShortWindow() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        path(sourcePlan, target);
        clock.set(Instant.parse("2024-02-15T16:45:00Z")); // 23:45 in Vietnam; midnight is 15 minutes away.
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").value("2024-02-15T17:00:00Z"));

        User tooLate = user("MEMBER");
        paidMembership(tooLate, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        clock.set(Instant.parse("2024-02-15T16:59:00Z"));
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(tooLate))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isConflict());
        assertTrue(payments.findByMember_IdAndPurposeAndStatusIn(tooLate.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).isEmpty());
    }

    @Test
    void exactExpiryBoundaryClosesOldDraftAndAllowsFreshDraftWithoutDeletingEvidence() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        path(sourcePlan, target);
        String token = bearer(member);
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", token)
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isCreated());
        Payment first = payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).get(0);
        MembershipUpgradePaymentEvidence firstEvidence = evidence.findByPayment_Id(first.getId()).orElseThrow();
        clock.set(firstEvidence.getExpiresAtUtc());

        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", token)
                        .contentType("application/json").content(body(target, "ONLINE")))
                .andExpect(status().isCreated());
        assertEquals(PaymentStatus.CANCELLED, payments.findById(first.getId()).orElseThrow().getStatus());
        assertTrue(evidence.findById(firstEvidence.getId()).isPresent());
        assertEquals(1, payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).size());
    }

    @Test
    void counterUpgradeUsesDedicatedReceiptWhileGenericOnlineCallbackFailsClosed() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        path(sourcePlan, target);
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body(target, "COUNTER")))
                .andExpect(status().isCreated());
        Payment draft = payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).get(0);
        String receptionistToken = bearer(user("RECEPTIONIST"));
        mvc.perform(patch("/api/payments/{id}/counter", draft.getId()).header("Authorization", receptionistToken)
                        .contentType("application/json").content("{\"success\":true,\"note\":\"test\"}"))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.SUCCESS,payments.findById(draft.getId()).orElseThrow().getStatus());

        User onlineMember = user("MEMBER");
        Membership onlineSource = paidMembership(onlineMember, sourcePlan, TODAY.minusDays(2),
                TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(onlineMember))
                        .contentType("application/json").content(body(target, "ONLINE")))
                .andExpect(status().isCreated());
        Payment onlineDraft = payments.findByMember_IdAndPurposeAndStatusIn(onlineMember.getId(), PaymentPurpose.UPGRADE,
                java.util.List.of(PaymentStatus.PENDING)).get(0);

        var callback = new GatewayCallback(onlineDraft.getTransactionCode(), true, onlineDraft.getAmount(), null,
                "provider-test-1", "00", "00", onlineDraft.getTransactionCode());
        org.mockito.Mockito.doReturn(Optional.of(callback)).when(gateway).verifyCallback(any());
        PaymentService paymentService = context.getBean(PaymentService.class);
        var result = paymentService.processOnlineCallback(java.util.Map.of("synthetic", "signed-fixture"));
        assertEquals(group5.example.sportCenter.payment.OnlineCallbackResult.Status.NOT_FOUND, result.status());
        assertEquals(PaymentStatus.PENDING, payments.findById(onlineDraft.getId()).orElseThrow().getStatus());
        assertTrue(memberships.findByMemberId(onlineMember.getId()).stream().anyMatch(m -> m.getId().equals(onlineSource.getId())));
        assertEquals(1, memberships.findByMemberId(onlineMember.getId()).size());
        assertEquals("ACTIVE",memberships.findById(onlineSource.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentRequestsForOneMemberCreateAtMostOnePendingUpgradeDraft() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY.minusDays(2), TODAY.minusDays(2).plusMonths(1), new BigDecimal("100.00"));
        path(sourcePlan, target);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(member.getId().toString()).build();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> issueAfterBarrier(jwt, target, ready, start));
            var second = pool.submit(() -> issueAfterBarrier(jwt, target, ready, start));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            int successCount = (first.get() == 201 ? 1 : 0) + (second.get() == 201 ? 1 : 0);
            assertEquals(1, successCount);
            assertEquals(1, payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.UPGRADE,
                    java.util.List.of(PaymentStatus.PENDING)).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pendingUpgradeDraftBlocksSchedulerRenewalUntilExpiry() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, TODAY.plusDays(2).minusMonths(1), TODAY.plusDays(2),
                new BigDecimal("100.00"));
        source.setAutoRenew(true);
        memberships.saveAndFlush(source);
        path(sourcePlan, target);

        mvc.perform(post("/api/memberships/me/upgrade-payments").header("Authorization", bearer(member))
                        .contentType("application/json").content(body(target, "ONLINE")))
                .andExpect(status().isCreated());
        assertEquals(0, renewalService.createRenewalRequestIfDue(source.getId()));
        assertTrue(payments.findByMember_IdAndPurposeAndStatusIn(member.getId(), PaymentPurpose.MEMBERSHIP,
                java.util.List.of(PaymentStatus.PENDING)).isEmpty());
    }

    private int issueAfterBarrier(Jwt jwt, MembershipPlan target, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            upgradePayments.create(jwt, new CreateMembershipUpgradePaymentRequest(target.getId(), PaymentMethod.COUNTER));
            return 201;
        } catch (org.springframework.web.server.ResponseStatusException expectedConflict) {
            if (expectedConflict.getStatusCode().value() == 409) return 409;
            throw expectedConflict;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private String body(MembershipPlan target, String method) {
        return "{\"targetPlanId\":" + target.getId() + ",\"paymentMethod\":\"" + method + "\"}";
    }

    private User user(String role) {
        User user = new User(); user.setName("Upgrade payment test");
        user.setEmail(UUID.randomUUID() + "@upgrade-payment.example.test"); user.setPasswordHash("test-hash");
        user.setRole(role); user.setStatus("ACTIVE"); return users.saveAndFlush(user);
    }

    private String bearer(User user) { return "Bearer " + jwtService.generateToken(user); }

    private MembershipPlan plan(String price, String status) {
        MembershipPlan plan = new MembershipPlan(); plan.setName("Upgrade target " + UUID.randomUUID());
        plan.setPrice(new BigDecimal(price)); plan.setMonthlyPrice(new BigDecimal(price)); plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(BigDecimal.ZERO); plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false); plan.setAllowOneOnOneCoaching(false); plan.setStatus(status);
        return plans.saveAndFlush(plan);
    }

    private Membership paidMembership(User member, MembershipPlan plan, LocalDate start, LocalDate end, BigDecimal amount) {
        Payment paid = new Payment(); paid.setMember(member); paid.setAmount(amount); paid.setMethod(PaymentMethod.COUNTER);
        paid.setPurpose(PaymentPurpose.MEMBERSHIP); paid.setReferenceId(plan.getId()); paid.setStatus(PaymentStatus.SUCCESS);
        paid.setPaidAt(LocalDate.of(2024, 2, 1).atStartOfDay()); paid.setPurchasedMonths(1);
        paid.setMonthlyPriceSnapshot(plan.getMonthlyPrice());
        paid.setTransactionCode("UPGSRC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        paid = payments.saveAndFlush(paid);
        Membership membership = new Membership(); membership.setMember(member); membership.setPlan(plan);
        membership.setStartDate(start); membership.setEndDate(end); membership.setStatus("ACTIVE");
        membership.setAutoRenew(false); membership.setPurchasedMonths(1); membership.setPaidAmount(amount);
        membership.setSourcePayment(paid); return memberships.saveAndFlush(membership);
    }

    private void path(MembershipPlan source, MembershipPlan target) {
        MembershipPlanUpgrade path = new MembershipPlanUpgrade(); path.setFromPlan(source); path.setToPlan(target);
        paths.saveAndFlush(path);
    }
}
