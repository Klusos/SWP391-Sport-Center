package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.CreateMembershipUpgradePathRequest;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.MembershipUpgradePathRepository;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import group5.example.sportCenter.service.MembershipUpgradePathService;

@SpringBootTest(properties = "app.payment.provider=mock")
@ActiveProfiles("phase1")
@Import(MembershipUpgradeIntegrationTest.TestClockConfiguration.class)
class MembershipUpgradeIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@upgrade.example.test";

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRows;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipUpgradePathRepository paths;
    @Autowired AuditLogRepository auditLogs;
    @Autowired MembershipUpgradePathService pathService;
    @Autowired MembershipRepository memberships;
    @MockitoSpyBean MembershipRenewalIntentRepository renewalIntents;
    @Autowired MembershipPlanRepository plans;
    @Autowired UserRepository users;
    @Autowired TestClock clock;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private MockMvc mvc;
    private LocalDate today;
    private User member;
    private User otherMember;
    private User receptionist;
    private User admin;
    private MembershipPlan standard;
    private MembershipPlan deluxe;
    private MembershipPlan premium;
    private Membership current;

    @BeforeEach
    void setUp() {
        // 10:00 in Vietnam keeps every quote well away from the midnight cut-off.
        today = LocalDate.now(BUSINESS_ZONE);
        clock.setInstant(today.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        cleanDatabase();

        member = user("MEMBER", "member-up");
        otherMember = user("MEMBER", "other-up");
        receptionist = user("RECEPTIONIST", "reception-up");
        admin = user("ADMIN", "admin-up");
        standard = plan("STANDARD", "500000", "10", "0", false, false);
        deluxe = plan("DELUXE", "550000", "5", "0", true, false);
        premium = plan("PREMIUM", "1500000", "15", "5", true, true);
        createPath(standard, premium, admin);
        current = membership(member, standard, today.minusDays(10), today.plusDays(20));
    }

    @AfterEach
    void tearDown() {
        // membership_upgrade references payments, memberships and plans that other test classes delete.
        cleanDatabase();
    }

    @Test
    void optionsListOnlyAdminConfiguredPathsWithVerifiedCreditQuote() throws Exception {
        String url = "/api/memberships/member/" + member.getId() + "/upgrade-options";
        mvc.perform(get(url).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlanName").value("STANDARD"))
                .andExpect(jsonPath("$.remainingDays").value(20))
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                // DELUXE has no configured directed path.
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.options[0].planName").value("PREMIUM"))
                // floor(500,000 * 20 / 30) = 333,333; payable = 1,166,667.
                .andExpect(jsonPath("$.options[0].appliedCredit").value(333333.00))
                .andExpect(jsonPath("$.options[0].amount").value(1166667.00));
        mvc.perform(get(url).header("Authorization", bearer(receptionist))).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", bearer(otherMember))).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void counterUpgradeUsesFrozenWholeVndCreditAndFullTargetDuration() throws Exception {
        PaymentResponse payment = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        assertEquals("PENDING", payment.getStatus());
        assertEquals(new BigDecimal("1166667.00"), payment.getAmount());
        assertNotNull(payment.getUpgradeQuote());
        assertEquals(new BigDecimal("1500000.00"), payment.getUpgradeQuote().targetPrice());
        assertEquals(new BigDecimal("333333.00"), payment.getUpgradeQuote().appliedCredit());
        assertEquals(new BigDecimal("1166667.00"), payment.getUpgradeQuote().payable());
        assertEquals(1, payment.getUpgradeQuote().targetDurationMonths());
        assertTrue(payments.getPendingMembershipCounterPayments(0, 20, jwt(receptionist)).getContent().stream()
                .anyMatch(row -> row.getId().equals(payment.getId())));

        assertEquals("SUCCESS", payments.processAtCounter(payment.getId(), counter(true), jwt(receptionist)).getStatus());

        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getId)).toList();
        assertEquals(2, rows.size());
        assertEquals(MembershipUpgradeService.UPGRADED, rows.get(0).getStatus());
        assertEquals(today.minusDays(10), rows.get(0).getStartDate());
        Membership upgraded = rows.get(1);
        assertEquals("ACTIVE", upgraded.getStatus());
        assertEquals(premium.getId(), upgraded.getPlan().getId());
        assertEquals(today, upgraded.getStartDate());
        assertEquals(today.plusMonths(1), upgraded.getEndDate());
        assertEquals("COMPLETE", upgraded.getBenefitSnapshotStatus());
        assertEquals("UPGRADE", upgraded.getBenefitSnapshotSource());
        assertEquals("PREMIUM", upgraded.getPlanNameSnapshot());
        assertEquals(new BigDecimal("1500000.00"), upgraded.getPaidValueBasisAmount());
        assertEquals("UPGRADE_CHAIN", upgraded.getPaidValueBasisSource());
        String membershipResponse = mvc.perform(get("/api/memberships/member/" + member.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(membershipResponse.contains("\"id\":" + upgraded.getId() + ",\"memberId\":"));
        assertTrue(membershipResponse.contains("\"purchasePaymentId\":" + payment.getId()));

        MembershipUpgrade record = upgrades.findByPaymentId(payment.getId()).orElseThrow();
        assertEquals(20, record.getRemainingDays());
        assertNotNull(record.getResultMembership());
        assertEquals(upgraded.getId(), record.getResultMembership().getId());
        var upgradeAudit = auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP_UPGRADE".equals(entry.getEntityType())
                && String.valueOf(record.getId()).equals(entry.getEntityId()) && "SETTLE".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertTrue(upgradeAudit.getDescription().contains("sourceMembership=" + current.getId()));
        assertTrue(upgradeAudit.getDescription().contains("replacementMembership=" + upgraded.getId()));
        assertTrue(upgradeAudit.getDescription().contains("chargedAmount=" + payment.getAmount().stripTrailingZeros().toPlainString()));
    }

    @Test
    void directPurchaseResponseLinksOnlyTheMembershipCreatedByThatPayment() throws Exception {
        current.setStatus("EXPIRED");
        current.setEndDate(today.minusDays(1));
        memberships.saveAndFlush(current);

        PaymentResponse pending = payments.createPayment(purchase(standard), jwt(member));
        assertEquals("PENDING", pending.getStatus());
        assertEquals("SUCCESS", payments.processAtCounter(pending.getId(), counter(true), jwt(receptionist)).getStatus());
        Membership purchased = memberships.findByMemberId(member.getId()).stream()
                .filter(row -> row.getPurchasePayment() != null && pending.getId().equals(row.getPurchasePayment().getId()))
                .findFirst().orElseThrow();

        String membershipResponse = mvc.perform(get("/api/memberships/member/" + member.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(membershipResponse.contains("\"id\":" + purchased.getId() + ",\"memberId\":"));
        assertTrue(membershipResponse.contains("\"purchasePaymentId\":" + pending.getId()));
    }

    @Test
    void upgradeQuoteIsBlockedByOpenRenewalIntentWithoutAnyPaymentAttempt() {
        org.mockito.Mockito.doReturn(true).when(renewalIntents).existsOpenByMemberId(member.getId());

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member)));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertTrue(conflict.getReason().contains("open Membership Renewal Intent"));
        assertTrue(paymentRows.findByMemberIdOrderByCreatedAtDesc(member.getId()).isEmpty());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
    }

    @Test
    void inactiveUpgradeTargetRejectsInitiationButDoesNotInvalidateAnAlreadyCreatedUpgrade() {
        PaymentResponse pending = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        premium.setStatus("INACTIVE");
        plans.saveAndFlush(premium);

        assertEquals("SUCCESS", payments.processAtCounter(pending.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(premium.getId(), activePlanId(member));

        User secondMember = user("MEMBER", "inactive-target-upgrade");
        Membership secondCurrent = membership(secondMember, standard, today.minusDays(10), today.plusDays(20));
        assertThrows(ResponseStatusException.class,
                () -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(secondMember)));
        assertEquals(1, memberships.findByMemberId(secondMember.getId()).size());
        assertEquals("ACTIVE", memberships.findById(secondCurrent.getId()).orElseThrow().getStatus());
    }

    @Test
    void upgradeSettlementRechecksAndReconcilesWhenAQueueAppearsAfterPaymentCreation() {
        PaymentResponse payment = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        Membership queued = membership(member, standard, current.getEndDate(), current.getEndDate().plusMonths(1));

        PaymentResponse result = payments.processAtCounter(payment.getId(), counter(true), jwt(receptionist));

        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(today.minusDays(10), memberships.findById(current.getId()).orElseThrow().getStartDate());
        assertEquals(today.plusDays(20), memberships.findById(current.getId()).orElseThrow().getEndDate());
        assertEquals("ACTIVE", memberships.findById(queued.getId()).orElseThrow().getStatus());
        assertTrue(upgrades.findByPaymentId(payment.getId()).orElseThrow().getResultMembership() == null);
    }

    @Test
    void onlineUpgradeAppliesWithinTheQuoteAndRefundsAfterItExpires() throws Exception {
        PaymentResponse onTime = payments.createPayment(upgrade(premium, PaymentMethod.ONLINE), jwt(member));
        clock.advance(Duration.ofMinutes(14));
        var successfulSettlementTime = java.time.LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        mvc.perform(get(callbackPath(onTime.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(successfulSettlementTime, paymentRows.findById(onTime.getId()).orElseThrow().getPaidAt(),
                "Upgrade success keeps callback processing time as paidAt");
        assertEquals(premium.getId(), activePlanId(member));
        clock.advance(Duration.ofSeconds(10));
        mvc.perform(get(callbackPath(onTime.getPaymentUrl())))
                .andExpect(status().isOk());
        assertEquals(successfulSettlementTime, paymentRows.findById(onTime.getId()).orElseThrow().getPaidAt(),
                "Duplicate callback cannot overwrite paidAt");

        Membership otherCurrent = membership(otherMember, standard, today.minusDays(5), today.plusDays(25));
        PaymentResponse late = payments.createPayment(upgrade(premium, PaymentMethod.ONLINE), jwt(otherMember));
        clock.advance(Duration.ofMinutes(16));
        mvc.perform(get(callbackPath(late.getPaymentUrl())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
        assertEquals("ACTIVE", memberships.findById(otherCurrent.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(otherMember.getId()).size());
    }

    @Test
    void successfulCounterPaymentAfterQuoteExpiryIsPreservedForReconciliation() {
        PaymentResponse payment = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        clock.advance(Duration.ofHours(15));

        PaymentResponse result = payments.processAtCounter(payment.getId(), counter(true), jwt(receptionist));
        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void upgradesAreBlockedWithoutACurrentPlanOrWithPendingQueuedOrNearlyExpiredPlans() throws Exception {
        String url = "/api/memberships/member/" + otherMember.getId() + "/upgrade-options";
        mvc.perform(get(url).header("Authorization", bearer(otherMember)))
                .andExpect(jsonPath("$.blockedReason").value("NO_ACTIVE_MEMBERSHIP"))
                .andExpect(jsonPath("$.options.length()").value(0));
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(otherMember)));

        // A downgrade or a plan that drops a benefit is refused.
        assertConflict(() -> payments.createPayment(upgrade(deluxe, PaymentMethod.COUNTER), jwt(member)));

        // An effective membership blocks a new ordinary purchase. A pre-existing pending
        // membership attempt must also block an Upgrade quote.
        assertConflict(() -> payments.createPayment(purchase(standard), jwt(member)));
        Payment pendingPurchase = pendingMembershipPayment(member, standard);
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member)));
        payments.cancelPayment(pendingPurchase.getId(), jwt(member));

        // One pending upgrade at a time, and no plan purchase while it is pending.
        payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.ONLINE), jwt(member)));
        assertConflict(() -> payments.createPayment(purchase(standard), jwt(member)));
        mvc.perform(get("/api/memberships/member/" + member.getId() + "/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("PENDING_PAYMENT"));

        // A plan already bought to start after the current one blocks mid-term upgrades.
        Membership queuedOwner = membership(otherMember, standard, today.minusDays(5), today.plusDays(25));
        membership(otherMember, standard, queuedOwner.getEndDate(), queuedOwner.getEndDate().plusMonths(1));
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(otherMember)));
        mvc.perform(get(url).header("Authorization", bearer(otherMember)))
                .andExpect(jsonPath("$.blockedReason").value("QUEUED_MEMBERSHIP"));

        User lastDays = user("MEMBER", "last-days-up");
        membership(lastDays, standard, today.minusDays(28), today.plusDays(2));
        mvc.perform(get("/api/memberships/member/" + lastDays.getId() + "/upgrade-options").header("Authorization", bearer(lastDays)))
                .andExpect(jsonPath("$.blockedReason").value("TOO_FEW_DAYS_LEFT"))
                .andExpect(jsonPath("$.remainingDays").value(2));
    }

    @Test
    void onlyAdminCanConfigureDirectedPathsAndDuplicateOrSelfLinksAreRejected() throws Exception {
        String endpoint = "/api/membership-upgrade-paths";
        mvc.perform(get(endpoint).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get(endpoint).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
        Integer pathId = paths.findByFromPlanIdAndToPlanId(standard.getId(), premium.getId()).orElseThrow().getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(endpoint + "/" + pathId + "/status")
                .header("Authorization", bearer(member)).contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(endpoint + "/" + pathId + "/status")
                .header("Authorization", bearer(admin)).contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(endpoint + "/" + pathId + "/status")
                .header("Authorization", bearer(admin)).contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));

        var selfLink = new CreateMembershipUpgradePathRequest(standard.getId(), standard.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                .header("Authorization", bearer(member)).contentType("application/json")
                .content("{\"fromPlanId\":" + standard.getId() + ",\"toPlanId\":" + premium.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                .header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"fromPlanId\":" + selfLink.fromPlanId() + ",\"toPlanId\":" + selfLink.toPlanId() + "}"))
                .andExpect(status().isBadRequest());
        var duplicate = new CreateMembershipUpgradePathRequest(standard.getId(), premium.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                .header("Authorization", bearer(admin)).contentType("application/json")
                .content("{\"fromPlanId\":" + duplicate.fromPlanId() + ",\"toPlanId\":" + duplicate.toPlanId() + "}"))
                .andExpect(status().isConflict());
        MembershipPlan cheaper = plan("CHEAPER-PATH", "450000", "15", "5", true, true);
        assertConflict(() -> pathService.create(new CreateMembershipUpgradePathRequest(standard.getId(), cheaper.getId()), jwt(admin)));

        MembershipPlan inactive = plan("INACTIVE-PATH", "1800000", "15", "5", true, true);
        inactive.setStatus("INACTIVE");
        plans.saveAndFlush(inactive);
        assertConflict(() -> pathService.create(new CreateMembershipUpgradePathRequest(standard.getId(), inactive.getId()), jwt(admin)));
    }

    @Test
    void unknownSourcePaidValueAndUnconfiguredTargetBlockQuote() throws Exception {
        jdbc.update("update membership set paid_value_basis_status='UNKNOWN', paid_value_basis_amount=null, "
                + "paid_value_basis_source='LEGACY_UNKNOWN' where id=?", current.getId());
        mvc.perform(get("/api/memberships/member/" + member.getId() + "/upgrade-options")
                .header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("UNKNOWN_PAID_VALUE"));
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member)));

        jdbc.update("update membership set paid_value_basis_status='VERIFIED', paid_value_basis_amount=500000, "
                + "paid_value_basis_source='PURCHASE_PAYMENT' where id=?", current.getId());
        assertConflict(() -> payments.createPayment(upgrade(deluxe, PaymentMethod.COUNTER), jwt(member)));
    }

    @Test
    void disablingPathAfterQuoteDoesNotInvalidateFrozenSettlement() {
        PaymentResponse pending = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        var path = paths.findByFromPlanIdAndToPlanId(standard.getId(), premium.getId()).orElseThrow();
        path.setActive(false);
        paths.saveAndFlush(path);
        User another = user("MEMBER", "disabled-path");
        membership(another, standard, today.minusDays(10), today.plusDays(20));
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(another)));
        assertEquals("SUCCESS", payments.processAtCounter(pending.getId(), counter(true), jwt(receptionist)).getStatus());
        assertEquals(premium.getId(), activePlanId(member));
    }

    @Test
    void capturedTargetTermsSurvivePlanEditsAndChainedBasisUsesAppliedCreditPlusPayment() {
        PaymentResponse pending = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        premium.setName("Renamed after quote");
        premium.setPrice(new BigDecimal("2000000.00"));
        premium.setDurationMonths(4);
        premium.setAllowOneOnOneCoaching(false);
        plans.saveAndFlush(premium);
        assertEquals("SUCCESS", payments.processAtCounter(pending.getId(), counter(true), jwt(receptionist)).getStatus());

        Membership resulting = memberships.findByMemberId(member.getId()).stream()
                .filter(row -> row.getStatus().equals("ACTIVE")).findFirst().orElseThrow();
        assertEquals("PREMIUM", resulting.getPlanNameSnapshot());
        assertEquals(new BigDecimal("1500000.00"), resulting.getPlanPriceSnapshot());
        assertEquals(1, resulting.getPlanDurationMonthsSnapshot());
        assertEquals(today.plusMonths(1), resulting.getEndDate());
        assertEquals(new BigDecimal("1500000.00"), resulting.getPaidValueBasisAmount());
        assertEquals("UPGRADE_CHAIN", resulting.getPaidValueBasisSource());

        MembershipPlan elite = plan("ELITE", "2500000", "25", "12", true, true);
        createPath(premium, elite, admin);
        PaymentResponse chained = payments.createPayment(upgrade(elite, PaymentMethod.COUNTER), jwt(member));
        assertEquals(new BigDecimal("1000000.00"), chained.getAmount());
        assertEquals("SUCCESS", payments.processAtCounter(chained.getId(), counter(true), jwt(receptionist)).getStatus());
        Membership finalMembership = memberships.findByMemberId(member.getId()).stream()
                .filter(row -> row.getStatus().equals("ACTIVE")).findFirst().orElseThrow();
        assertEquals(elite.getId(), finalMembership.getPlan().getId());
        assertEquals(new BigDecimal("2500000.00"), finalMembership.getPaidValueBasisAmount());
        assertEquals("UPGRADE_CHAIN", finalMembership.getPaidValueBasisSource());
    }

    @Test
    void failedOrCancelledUpgradeLeavesSourceMembershipActive() {
        PaymentResponse failed = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        assertEquals("CANCELLED", payments.processAtCounter(failed.getId(), counter(false), jwt(receptionist)).getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());

        PaymentResponse cancelled = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        assertEquals("CANCELLED", payments.cancelPayment(cancelled.getId(), jwt(member)).getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void legacyPendingUpgradeWithoutFrozenQuoteIsReconciledWithoutReplacingSource() {
        Payment legacy = new Payment();
        legacy.setMember(member);
        legacy.setAmount(new BigDecimal("1000000.00"));
        legacy.setMethod(PaymentMethod.COUNTER);
        legacy.setPurpose(PaymentPurpose.UPGRADE);
        legacy.setReferenceId(premium.getId());
        legacy.setStatus(PaymentStatus.PENDING);
        legacy.setTransactionCode("TXN-LEGACY-UPGRADE-" + member.getId());
        legacy.setCreatedAt(java.time.LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        legacy = paymentRows.saveAndFlush(legacy);

        MembershipUpgrade legacyAttempt = new MembershipUpgrade();
        legacyAttempt.setPayment(legacy);
        legacyAttempt.setSourceMembership(current);
        legacyAttempt.setTargetPlan(premium);
        legacyAttempt.setRemainingDays(20);
        legacyAttempt.setExpiresAt(today.plusDays(1).atStartOfDay());
        legacyAttempt.setCreatedAt(java.time.LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        upgrades.saveAndFlush(legacyAttempt);

        PaymentResponse result = payments.processAtCounter(legacy.getId(), counter(true), jwt(receptionist));
        assertEquals("REFUND_REQUIRED", result.getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertTrue(upgrades.findByPaymentId(legacy.getId()).orElseThrow().getResultMembership() == null);
    }

    @Test
    void exactThreeUnusedDaysAreEligibleAndZeroPayableQuoteIsRejected() {
        User boundaryMember = user("MEMBER", "three-day-upgrade");
        membership(boundaryMember, standard, today.minusDays(7), today.plusDays(3));
        PaymentResponse boundary = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(boundaryMember));
        assertEquals(new BigDecimal("1350000.00"), boundary.getAmount());
        payments.cancelPayment(boundary.getId(), jwt(boundaryMember));

        User zeroPayableMember = user("MEMBER", "zero-payable-upgrade");
        Membership highBasis = membership(zeroPayableMember, standard, today.minusDays(10), today.plusDays(20));
        jdbc.update("update membership set paid_value_basis_amount=20000000 where id=?", highBasis.getId());
        assertConflict(() -> payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(zeroPayableMember)));
        assertEquals(1, memberships.findByMemberId(zeroPayableMember.getId()).size());
    }

    @Test
    void concurrentUpgradeRequestsSerializeOnTheMemberRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> createUpgradeConcurrently(ready, start));
            Future<Boolean> second = executor.submit(() -> createUpgradeConcurrently(ready, start));
            ready.await();
            start.countDown();
            boolean firstCreated = first.get();
            boolean secondCreated = second.get();
            assertTrue(firstCreated ^ secondCreated);
            assertEquals(1, jdbc.queryForObject("select count(*) from payment where member_id=? and purpose='UPGRADE' and status='PENDING'",
                    Integer.class, member.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean createUpgradeConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    private void assertConflict(org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private Integer activePlanId(User owner) {
        // Plan ids are readable from the lazy proxy without an open session.
        return memberships.findByMemberId(owner.getId()).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus())).map(m -> m.getPlan().getId())
                .findFirst().orElse(null);
    }

    private CreatePaymentRequest upgrade(MembershipPlan target, PaymentMethod method) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.UPGRADE);
        request.setReferenceId(target.getId());
        request.setMethod(method);
        return request;
    }

    private CreatePaymentRequest purchase(MembershipPlan plan) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.MEMBERSHIP);
        request.setReferenceId(plan.getId());
        request.setMethod(PaymentMethod.COUNTER);
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

    private MembershipPlan plan(String name, String price, String courseDiscount, String ptDiscount,
                                boolean ptPlan, boolean oneOnOne) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName(name);
        plan.setPrice(new BigDecimal(price));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(new BigDecimal(courseDiscount));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal(ptDiscount));
        plan.setAllowPersonalTrainingPlan(ptPlan);
        plan.setAllowOneOnOneCoaching(oneOnOne);
        plan.setStatus("ACTIVE");
        return plans.save(plan);
    }

    private Payment pendingMembershipPayment(User owner, MembershipPlan plan) {
        Payment payment = new Payment();
        payment.setMember(owner);
        payment.setAmount(plan.getPrice());
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-LEGACY-PENDING-" + owner.getId());
        payment.setCreatedAt(java.time.LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        return paymentRows.save(payment);
    }

    private void createPath(MembershipPlan from, MembershipPlan to, User actor) {
        pathService.create(new CreateMembershipUpgradePathRequest(from.getId(), to.getId()), jwt(actor));
    }

    private Membership membership(User owner, MembershipPlan plan, LocalDate start, LocalDate end) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(plan);
        membership.setAutoRenew(false);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
        membership.setPaidValueBasisStatus("VERIFIED");
        membership.setPaidValueBasisAmount(plan.getPrice());
        membership.setPaidValueBasisSource("PURCHASE_PAYMENT");
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
        paths.deleteAllInBatch();
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
        TestClock membershipUpgradeTestClock() {
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
