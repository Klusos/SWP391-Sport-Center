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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

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
    @Autowired MembershipRepository memberships;
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
        standard = plan("STANDARD", "500000", "10", "0", false, false);
        deluxe = plan("DELUXE", "550000", "5", "0", true, false);
        premium = plan("PREMIUM", "1500000", "15", "5", true, true);
        current = membership(member, standard, today.minusDays(10), today.plusDays(20));
    }

    @AfterEach
    void tearDown() {
        // membership_upgrade references payments, memberships and plans that other test classes delete.
        cleanDatabase();
    }

    @Test
    void optionsListOnlyPlansThatKeepEveryBenefitWithTheProratedPrice() throws Exception {
        String url = "/api/memberships/member/" + member.getId() + "/upgrade-options";
        mvc.perform(get(url).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlanName").value("STANDARD"))
                .andExpect(jsonPath("$.remainingDays").value(20))
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                // DELUXE costs more but lowers the course discount, so it is not an upgrade.
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.options[0].planName").value("PREMIUM"))
                // (1,500,000 - 500,000) / 30 * 20 = 666,666.67 -> rounded up to 667,000.
                .andExpect(jsonPath("$.options[0].amount").value(667000.00));
        mvc.perform(get(url).header("Authorization", bearer(receptionist))).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", bearer(otherMember))).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void counterUpgradeReplacesTheMembershipAndKeepsTheEndDate() {
        PaymentResponse payment = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        assertEquals("PENDING", payment.getStatus());
        assertEquals(new BigDecimal("667000.00"), payment.getAmount());
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
        assertEquals(today.plusDays(20), upgraded.getEndDate());

        MembershipUpgrade record = upgrades.findByPaymentId(payment.getId()).orElseThrow();
        assertEquals(20, record.getRemainingDays());
        assertNotNull(record.getResultMembership());
        assertEquals(upgraded.getId(), record.getResultMembership().getId());
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
    void counterConfirmationIsRejectedOnceTheQuoteExpiresAtMidnight() {
        PaymentResponse payment = payments.createPayment(upgrade(premium, PaymentMethod.COUNTER), jwt(member));
        clock.advance(Duration.ofHours(15));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payments.processAtCounter(payment.getId(), counter(true), jwt(receptionist)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("PENDING", paymentRows.findById(payment.getId()).orElseThrow().getStatus().name());
        assertEquals("CANCELLED", payments.processAtCounter(payment.getId(), counter(false), jwt(receptionist)).getStatus());
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

        // An unresolved ordinary purchase also blocks creating an upgrade quote/payment.
        PaymentResponse pendingPurchase = payments.createPayment(purchase(standard), jwt(member));
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
    void proratedAmountUsesThirtyDayMonthsAndRoundsUpToOneThousand() {
        MembershipPlan quarterly = plan("QUARTERLY", "2700000", "20", "10", true, true);
        quarterly.setDurationMonths(3);
        // 2,700,000 / 3 months = 900,000 per month; (900,000 - 500,000) / 30 * 10 = 133,333.33 -> 134,000.
        assertEquals(new BigDecimal("134000.00"), MembershipUpgradeService.proratedAmount(standard, quarterly, 10));
        assertTrue(MembershipUpgradeService.isUpgrade(standard, quarterly));
        assertTrue(!MembershipUpgradeService.isUpgrade(premium, standard));
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

    private Membership membership(User owner, MembershipPlan plan, LocalDate start, LocalDate end) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(plan);
        membership.setAutoRenew(false);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
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
