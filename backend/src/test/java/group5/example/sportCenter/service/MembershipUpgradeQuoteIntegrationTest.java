package group5.example.sportCenter.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipPlanUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(MembershipUpgradeQuoteIntegrationTest.TestClockConfiguration.class)
class MembershipUpgradeQuoteIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 2, 15);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary
        Clock upgradeQuoteTestClock() {
            return Clock.fixed(Instant.parse("2024-02-15T05:00:00Z"), BUSINESS_ZONE);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtService jwtService;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipPlanUpgradeRepository upgradePaths;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void memberGetsConfiguredTargetAndDayProratedCreditWithoutMutation() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan targetPlan = plan("500.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 3, 1), 1, new BigDecimal("310.00"), false);
        configurePath(sourcePlan, targetPlan);
        long membershipCount = memberships.count();
        long paymentCount = payments.count();

        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.businessTimeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.sourceMembershipId").value(source.getId()))
                .andExpect(jsonPath("$.sourceAutoRenew").value(false))
                .andExpect(jsonPath("$.originalDurationDays").value(29))
                .andExpect(jsonPath("$.remainingDays").value(15))
                .andExpect(jsonPath("$.remainingCredit").value(160.34))
                .andExpect(jsonPath("$.upgradeOptions[0].targetPlanId").value(targetPlan.getId()))
                .andExpect(jsonPath("$.upgradeOptions[0].eligible").value(true))
                .andExpect(jsonPath("$.upgradeOptions[0].upgradeAmount").value(339.66));

        assertEquals(membershipCount, memberships.count());
        assertEquals(paymentCount, payments.count());
        assertEquals(PaymentStatus.SUCCESS, source.getSourcePayment().getStatus());
    }

    @Test
    void usesHalfOpenDateBoundariesAndSupportsLeapYearLegacyMultiMonthPeriods() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan targetPlan = plan("1000.00", "ACTIVE");
        Membership source = paidMembership(member, sourcePlan, LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 4, 1), null, new BigDecimal("900.00"), true);
        configurePath(sourcePlan, targetPlan);

        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalDurationDays").value(91))
                .andExpect(jsonPath("$.remainingDays").value(46))
                .andExpect(jsonPath("$.remainingCredit").value(454.95))
                .andExpect(jsonPath("$.sourceAutoRenew").value(true));

        User startsToday = user("MEMBER");
        paidMembership(startsToday, sourcePlan, TODAY, TODAY.plusMonths(1), 1,
                new BigDecimal("100.00"), false);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(startsToday)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingDays").value(29))
                .andExpect(jsonPath("$.remainingCredit").value(100.00));
    }

    @Test
    void endDateIsExclusiveAndNoCurrentMembershipIsReported() throws Exception {
        User member = user("MEMBER");
        MembershipPlan plan = plan("100.00", "ACTIVE");
        paidMembership(member, plan, TODAY.minusMonths(1), TODAY, 1, new BigDecimal("100.00"), false);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEffectiveMembershipCount").value(0))
                .andExpect(jsonPath("$.sourceMembershipId").doesNotExist())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("No ACTIVE Membership")));
    }

    @Test
    void overlapFutureMembershipAndUnresolvedPaymentBlockQuotes() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan target = plan("500.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY.minusDays(10), TODAY.plusDays(10), 1,
                new BigDecimal("100.00"), false);
        paidMembership(member, sourcePlan, TODAY.minusDays(5), TODAY.plusDays(15), 1,
                new BigDecimal("100.00"), false);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentEffectiveMembershipCount").value(2))
                .andExpect(jsonPath("$.eligible").value(false));

        User futureMember = user("MEMBER");
        Membership current = paidMembership(futureMember, sourcePlan, TODAY.minusDays(14), TODAY.minusDays(14).plusMonths(1), 1,
                new BigDecimal("100.00"), false);
        paidMembership(futureMember, sourcePlan, current.getEndDate(), current.getEndDate().plusMonths(1), 1,
                new BigDecimal("100.00"), false);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(futureMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceMembershipId").value(current.getId()))
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("future ACTIVE Membership")));

        User pendingMember = user("MEMBER");
        paidMembership(pendingMember, sourcePlan, TODAY.minusDays(14), TODAY.minusDays(14).plusMonths(1), 1,
                new BigDecimal("100.00"), false);
        Payment pending = new Payment();
        pending.setMember(pendingMember);
        pending.setAmount(new BigDecimal("100.00"));
        pending.setMethod(PaymentMethod.COUNTER);
        pending.setPurpose(PaymentPurpose.MEMBERSHIP);
        pending.setReferenceId(target.getId());
        pending.setStatus(PaymentStatus.PENDING);
        pending.setTransactionCode("PEND-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payments.saveAndFlush(pending);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(pendingMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("pending Membership payment")));
    }

    @Test
    void missingPaidEvidenceNoConfiguredPathAndNonPositiveAmountAreIneligible() throws Exception {
        User noEvidence = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        MembershipPlan targetPlan = plan("500.00", "ACTIVE");
        Membership manual = new Membership();
        manual.setMember(noEvidence); manual.setPlan(sourcePlan); manual.setStatus("ACTIVE");
        manual.setAutoRenew(false); manual.setStartDate(TODAY.minusDays(1)); manual.setEndDate(TODAY.plusDays(29));
        memberships.saveAndFlush(manual);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(noEvidence)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.remainingCredit").doesNotExist());

        User noPath = user("MEMBER");
        paidMembership(noPath, sourcePlan, TODAY.minusDays(14), TODAY.minusDays(14).plusMonths(1), 1,
                new BigDecimal("100.00"), false);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(noPath)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upgradeOptions").isEmpty())
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("No ADMIN-configured")));

        User zeroAmount = user("MEMBER");
        Membership zeroSource = paidMembership(zeroAmount, sourcePlan, TODAY, TODAY.plusMonths(1), 1,
                new BigDecimal("500.00"), false);
        configurePath(sourcePlan, targetPlan);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(zeroAmount)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upgradeOptions[0].eligible").value(false))
                .andExpect(jsonPath("$.upgradeOptions[0].upgradeAmount").doesNotExist())
                .andExpect(jsonPath("$.upgradeOptions[0].blocker").value(org.hamcrest.Matchers.containsString("business review")));
        assertTrue(memberships.existsById(zeroSource.getId()));
    }

    @Test
    void staleConfiguredInactiveEqualAndCheaperTargetsAreReturnedAsIneligible() throws Exception {
        User member = user("MEMBER");
        MembershipPlan sourcePlan = plan("100.00", "ACTIVE");
        paidMembership(member, sourcePlan, TODAY, TODAY.plusMonths(1), 1, new BigDecimal("100.00"), false);
        MembershipPlan inactive = plan("300.00", "INACTIVE");
        MembershipPlan equal = plan("100.00", "ACTIVE");
        MembershipPlan cheaper = plan("50.00", "ACTIVE");
        configurePath(sourcePlan, inactive);
        configurePath(sourcePlan, equal);
        configurePath(sourcePlan, cheaper);

        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.upgradeOptions.length()").value(3))
                .andExpect(jsonPath("$.upgradeOptions[0].eligible").value(false))
                .andExpect(jsonPath("$.upgradeOptions[1].eligible").value(false))
                .andExpect(jsonPath("$.upgradeOptions[2].eligible").value(false));
    }

    @Test
    void inconsistentPaidSnapshotAndPendingRenewalBlockTheQuote() throws Exception {
        User mismatchMember = user("MEMBER");
        MembershipPlan plan = plan("100.00", "ACTIVE");
        Membership mismatch = paidMembership(mismatchMember, plan, TODAY.minusDays(14),
                TODAY.minusDays(14).plusMonths(1), 1, new BigDecimal("100.00"), false);
        mismatch.getSourcePayment().setAmount(new BigDecimal("90.00"));
        payments.saveAndFlush(mismatch.getSourcePayment());
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(mismatchMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.remainingCredit").doesNotExist())
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("purchase evidence")));

        User renewalMember = user("MEMBER");
        Membership source = paidMembership(renewalMember, plan, TODAY.minusDays(14),
                TODAY.minusDays(14).plusMonths(1), 1, new BigDecimal("100.00"), false);
        Payment pendingRenewal = new Payment();
        pendingRenewal.setMember(renewalMember); pendingRenewal.setAmount(new BigDecimal("100.00"));
        pendingRenewal.setMethod(PaymentMethod.ONLINE); pendingRenewal.setPurpose(PaymentPurpose.MEMBERSHIP);
        pendingRenewal.setReferenceId(plan.getId()); pendingRenewal.setRenewalMembership(source);
        pendingRenewal.setPurchasedMonths(1); pendingRenewal.setMonthlyPriceSnapshot(new BigDecimal("100.00"));
        pendingRenewal.setStatus(PaymentStatus.PENDING);
        pendingRenewal.setTransactionCode("RNEW-" + UUID.randomUUID().toString().replace("-", "").substring(0, 15));
        payments.saveAndFlush(pendingRenewal);
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(renewalMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("pending Membership payment")));
    }

    @Test
    void onlyMemberCanReadAndJwtAlwaysScopesTheResultToTheCaller() throws Exception {
        User member = user("MEMBER");
        User other = user("MEMBER");
        User admin = user("ADMIN");
        User receptionist = user("RECEPTIONIST");
        MembershipPlan plan = plan("100.00", "ACTIVE");
        paidMembership(other, plan, TODAY.minusDays(1), TODAY.plusDays(29), 1, new BigDecimal("100.00"), false);

        mvc.perform(get("/api/memberships/me/upgrade-options"))
                .andExpect(status().isUnauthorized());
        for (User staff : new User[] {admin, receptionist}) {
            mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(staff)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/memberships/me/upgrade-options").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceMembershipId").doesNotExist());
        assertEquals(1, memberships.findByMemberId(other.getId()).size());
    }

    private Membership paidMembership(User member, MembershipPlan plan, LocalDate start, LocalDate end,
            Integer months, BigDecimal amount, boolean autoRenew) {
        Payment payment = new Payment();
        payment.setMember(member); payment.setAmount(amount); payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP); payment.setReferenceId(plan.getId());
        payment.setStatus(PaymentStatus.SUCCESS); payment.setPaidAt(Instant.now().atZone(BUSINESS_ZONE).toLocalDateTime());
        payment.setPurchasedMonths(months); payment.setMonthlyPriceSnapshot(plan.getMonthlyPrice());
        payment.setTransactionCode("PAID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment = payments.saveAndFlush(payment);

        Membership membership = new Membership();
        membership.setMember(member); membership.setPlan(plan); membership.setStartDate(start); membership.setEndDate(end);
        membership.setStatus("ACTIVE"); membership.setAutoRenew(autoRenew); membership.setPurchasedMonths(months);
        membership.setPaidAmount(amount); membership.setSourcePayment(payment);
        return memberships.saveAndFlush(membership);
    }

    private void configurePath(MembershipPlan source, MembershipPlan target) {
        if (upgradePaths.existsByFromPlan_IdAndToPlan_Id(source.getId(), target.getId())) return;
        MembershipPlanUpgrade path = new MembershipPlanUpgrade();
        path.setFromPlan(source); path.setToPlan(target);
        upgradePaths.saveAndFlush(path);
    }

    private MembershipPlan plan(String monthlyPrice, String status) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName("Quote plan " + UUID.randomUUID()); plan.setPrice(new BigDecimal(monthlyPrice));
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice)); plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(BigDecimal.ZERO); plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false); plan.setAllowOneOnOneCoaching(false); plan.setStatus(status);
        return plans.saveAndFlush(plan);
    }

    private User user(String role) {
        User user = new User();
        user.setName("Upgrade quote user"); user.setEmail(UUID.randomUUID() + "@upgrade-quote.example.test");
        user.setPasswordHash("test-hash"); user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
