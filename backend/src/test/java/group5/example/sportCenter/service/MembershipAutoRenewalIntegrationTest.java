package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest(properties = {
        "app.payment.provider=mock",
        "sportcenter.scheduling.enabled=false"
})
@ActiveProfiles("phase1")
class MembershipAutoRenewalIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@renewal-transition.example.test";

    @Autowired MembershipAutoRenewalService autoRenewal;
    @Autowired MembershipRenewalIntentService renewalIntents;
    @Autowired MembershipRenewalLifecycleService renewalLifecycle;
    @Autowired MembershipRenewalPaymentAttemptService renewalAttempts;
    @Autowired MembershipService membershipService;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository payments;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipRenewalIntentRepository intentRows;
    @Autowired MembershipPlanRepository plans;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired TestClock clock;
    @Autowired JwtService tokens;

    private LocalDate today;
    private User member;
    private User receptionist;
    private MembershipPlan plan;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        cleanDatabase();
        member = user("MEMBER", "member");
        receptionist = user("RECEPTIONIST", "receptionist");
        plan = plan();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void optedInMembershipDoesNotCreateNewLegacyNoteBasedPayment() {
        Membership current = membership(today.minusDays(28), today.plusDays(2), true);

        assertTrue(autoRenewal.createIfDue(current).isEmpty());
        assertEquals(0, autoRenewal.runDaily().created());
        assertTrue(payments.findByStatusAndPurposeAndNoteStartingWith(PaymentStatus.PENDING,
                PaymentPurpose.MEMBERSHIP, MembershipAutoRenewalService.NOTE_PREFIX).isEmpty());
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
        assertEquals(0, notifications.findAll().stream().filter(n -> n.getUser().getId().equals(member.getId())).count());
    }

    @Test
    void pausingLegacyRenewalCreationDoesNotDisableMembershipExpiration() {
        Membership expiredToday = membership(today.minusMonths(1), today, true);

        assertTrue(autoRenewal.createIfDue(expiredToday).isEmpty());
        assertEquals(1, membershipService.expireMemberships());
        assertEquals("EXPIRED", memberships.findById(expiredToday.getId()).orElseThrow().getStatus());
    }

    @Test
    void preExistingLegacyRenewalPaymentIsPreservedForReconciliationInsteadOfActivatingFromPlan() {
        Membership source = membership(today.minusDays(28), today.plusDays(2), true);
        Payment legacy = new Payment();
        legacy.setMember(member);
        legacy.setAmount(plan.getPrice());
        legacy.setMethod(PaymentMethod.COUNTER);
        legacy.setPurpose(PaymentPurpose.MEMBERSHIP);
        legacy.setReferenceId(plan.getId());
        legacy.setStatus(PaymentStatus.PENDING);
        legacy.setTransactionCode("LEGACY-" + System.nanoTime());
        legacy.setNote(MembershipAutoRenewalService.NOTE_PREFIX + source.getId());
        legacy = payments.saveAndFlush(legacy);

        CounterPaymentRequest receipt = new CounterPaymentRequest();
        receipt.setSuccess(true);
        var result = paymentService.processAtCounter(legacy.getId(), receipt, jwt(receptionist));

        assertEquals("REFUND_REQUIRED", result.getStatus());
        Payment reconciled = payments.findById(legacy.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, reconciled.getStatus());
        assertTrue(reconciled.getNote().contains(MembershipAutoRenewalService.NOTE_PREFIX + source.getId()));
        assertEquals(1, memberships.findByMemberId(member.getId()).size());
    }

    @Test
    void legacyRenewalPaymentReplacementCannotStartAnotherNoteBasedAttempt() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.MEMBERSHIP);
        request.setReferenceId(plan.getId());
        request.setMethod(PaymentMethod.ONLINE);
        request.setAutoRenew(true);
        request.setRenewalSourcePaymentId(123456);

        org.springframework.web.server.ResponseStatusException rejected = org.junit.jupiter.api.Assertions
                .assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> paymentService.createPayment(request, jwt(member)));
        assertEquals(410, rejected.getStatusCode().value());
        assertTrue(payments.findByStatusAndPurposeAndNoteStartingWith(PaymentStatus.PENDING,
                PaymentPurpose.MEMBERSHIP, MembershipAutoRenewalService.NOTE_PREFIX).isEmpty());
    }

    @Test
    void schedulerCatchUpCreatesOneFrozenIntentAndReenableDoesNotReopenIt() {
        Membership source = membership(today.minusDays(28), today.plusDays(2), true);

        var first = renewalLifecycle.runDueLifecycle();
        var intent = intentRows.findBySourceMembershipId(source.getId()).orElseThrow();
        var second = renewalLifecycle.runDueLifecycle();

        assertEquals(1, first.intentsCreated());
        assertEquals(0, second.intentsCreated());
        assertEquals(1, intentRows.count());
        assertEquals(today.plusDays(2).atStartOfDay(), intent.getValidUntil());
        assertEquals(plan.getPrice(), intent.getQuotedAmount());
        assertEquals(1, notifications.findByUserId(member.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().stream().filter(n -> n.getTitle().equals("Renewal available")).count());

        membershipService.cancelAutoRenew(source.getId(), jwt(member));
        assertEquals("CANCELLED", intentRows.findById(intent.getId()).orElseThrow().getIntentStatus().name());
        membershipService.enableAutoRenew(source.getId(), jwt(member));
        renewalLifecycle.runDueLifecycle();

        assertEquals(1, intentRows.count(), "A terminal source-period Intent is not recreated after re-enable");
        assertTrue(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(intent.getId()).isEmpty());
    }

    @Test
    void optOutCancelsUncollectedAttemptButPreservesCollectionForReconciliation() {
        Membership source = membership(today.minusDays(28), today.plusDays(2), true);
        renewalLifecycle.runDueLifecycle();
        var intent = intentRows.findBySourceMembershipId(source.getId()).orElseThrow();
        Payment attempt = renewalAttempts.createAttempt(intent.getId(), PaymentMethod.ONLINE);

        membershipService.cancelAutoRenew(source.getId(), jwt(member));

        Payment cancelled = payments.findById(attempt.getId()).orElseThrow();
        assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(0, memberships.findByMemberId(member.getId()).stream()
                .filter(m -> m.getRenewalIntent() != null).count());
        source.setStatus("CANCELLED");
        memberships.saveAndFlush(source);

        Membership secondSource = membership(today.minusDays(28), today.plusDays(2), true);
        renewalLifecycle.runDueLifecycle();
        var secondIntent = intentRows.findBySourceMembershipId(secondSource.getId()).orElseThrow();
        Payment collectedAttempt = renewalAttempts.createAttempt(secondIntent.getId(), PaymentMethod.ONLINE);
        collectedAttempt.setRenewalCollectionVerifiedAt(today.atTime(10, 0));
        collectedAttempt.setRenewalProviderTransactionReference("test-provider-" + System.nanoTime());
        collectedAttempt.setRenewalVerifiedCollectionAmount(collectedAttempt.getAmount());
        collectedAttempt.setRenewalVerifiedCollectionCurrency("VND");
        collectedAttempt.setPaidAt(today.atTime(10, 0));
        payments.saveAndFlush(collectedAttempt);

        membershipService.cancelAutoRenew(secondSource.getId(), jwt(member));

        Payment reconciliation = payments.findById(collectedAttempt.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, reconciliation.getStatus());
        assertTrue(reconciliation.hasRecordedRenewalCollection());
        assertTrue(notifications.findByUserId(member.getId(), org.springframework.data.domain.PageRequest.of(0, 30))
                .getContent().stream().anyMatch(n -> n.getTitle().equals("Renewal payment needs manual reconciliation")
                        && n.getMessage().contains("does not mean a refund has been completed")));
    }

    @Test
    void onlyMembershipOwnerCanChangeAutoRenewPreference() {
        Membership source = membership(today.minusDays(28), today.plusDays(2), true);

        var denied = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> membershipService.cancelAutoRenew(source.getId(), jwt(receptionist)));

        assertEquals(403, denied.getStatusCode().value());
        assertTrue(memberships.findById(source.getId()).orElseThrow().getAutoRenew());
        assertTrue(intentRows.findBySourceMembershipId(source.getId()).isEmpty());
    }

    private Membership membership(LocalDate start, LocalDate end, boolean autoRenew) {
        Membership row = new Membership();
        row.setMember(member);
        row.setPlan(plan);
        row.setAutoRenew(autoRenew);
        row.setStartDate(start);
        row.setEndDate(end);
        row.setStatus("ACTIVE");
        return memberships.saveAndFlush(row);
    }

    private MembershipPlan plan() {
        MembershipPlan row = new MembershipPlan();
        row.setName("Renew transition plan");
        row.setPrice(new BigDecimal("400000.00"));
        row.setDurationMonths(1);
        row.setCourseDiscountPercent(BigDecimal.ZERO);
        row.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        row.setAllowPersonalTrainingPlan(false);
        row.setAllowOneOnOneCoaching(false);
        row.setStatus("ACTIVE");
        return plans.saveAndFlush(row);
    }

    private User user(String role, String suffix) {
        User row = new User();
        row.setName("Renewal transition " + suffix);
        row.setEmail(suffix + EMAIL_DOMAIN);
        row.setPasswordHash("unused");
        row.setRole(role);
        row.setStatus("ACTIVE");
        return users.saveAndFlush(row);
    }

    private Jwt jwt(User user) {
        return Jwt.withTokenValue("phase3b-test")
                .header("alg", "none")
                .subject(String.valueOf(user.getId()))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("role", user.getRole())
                .build();
    }

    private void cleanDatabase() {
        notifications.deleteAllInBatch();
        payments.deleteAllInBatch();
        intentRows.deleteAllInBatch();
        memberships.deleteAllInBatch();
        plans.deleteAllInBatch();
        List<User> ownUsers = users.findAll().stream().filter(u -> u.getEmail().endsWith(EMAIL_DOMAIN)).toList();
        users.deleteAll(ownUsers);
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        TestClock membershipRenewalPhase3bClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(Instant.now(), zone); }
        @Override public Instant instant() { return Instant.now(); }
    }
}
