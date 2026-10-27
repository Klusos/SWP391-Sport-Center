package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(MembershipAutoRenewalIntegrationTest.TestClockConfiguration.class)
class MembershipAutoRenewalIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@renewal.example.test";

    @Autowired MembershipAutoRenewalService autoRenewal;
    @Autowired MembershipService membershipService;
    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRows;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository plans;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestClock clock;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;

    private MockMvc mvc;
    private LocalDate today;
    private User member;
    private User receptionist;
    private User admin;
    private MembershipPlan plan;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(BUSINESS_ZONE);
        clock.setInstant(today.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        cleanDatabase();
        member = user("MEMBER", "member-renew");
        receptionist = user("RECEPTIONIST", "reception-renew");
        admin = user("ADMIN", "admin-renew");
        plan = plan("Renew plan", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void enablingInsideTheNoticeWindowCreatesARenewalRequestAndNotifiesTheMember() throws Exception {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), false);

        mvc.perform(patch("/api/memberships/" + current.getId() + "/enable-auto-renew").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoRenew").value(true));

        List<Payment> pending = pendingRenewals();
        assertEquals(1, pending.size());
        Payment request = pending.get(0);
        assertEquals(PaymentMethod.COUNTER, request.getMethod());
        assertEquals(PaymentPurpose.MEMBERSHIP, request.getPurpose());
        assertEquals(plan.getId(), request.getReferenceId());
        assertEquals(0, new BigDecimal("400000.00").compareTo(request.getAmount()));
        assertEquals(true, request.getAutoRenew());
        assertEquals(current.getId(), MembershipAutoRenewalService.sourceMembershipId(request));
        assertEquals(1, notificationsFor(member));

        // Turning it off again cancels the unpaid request.
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel-renewal").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(request.getId()).orElseThrow().getStatus());
        assertTrue(pendingRenewals().isEmpty());
    }

    @Test
    void theDailyJobOnlyRenewsTheLastDueMembershipWithoutPendingPayments() throws Exception {
        membership(member, plan, today.minusDays(25), today.plusDays(5), true);
        User due = user("MEMBER", "due-renew");
        membership(due, plan, today.minusDays(27), today.plusDays(3), true);
        User optedOut = user("MEMBER", "off-renew");
        membership(optedOut, plan, today.minusDays(27), today.plusDays(3), false);
        User queued = user("MEMBER", "queued-renew");
        Membership queuedCurrent = membership(queued, plan, today.minusDays(29), today.plusDays(1), true);
        membership(queued, plan, queuedCurrent.getEndDate(), queuedCurrent.getEndDate().plusMonths(1), false);

        mvc.perform(post("/api/memberships/auto-renew/run").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.cancelled").value(0));
        List<Payment> pending = pendingRenewals();
        assertEquals(1, pending.size());
        assertEquals(due.getId(), pending.get(0).getMember().getId());

        // A second run does not duplicate the pending request.
        assertEquals(0, autoRenewal.runDaily().created());
        mvc.perform(post("/api/memberships/auto-renew/run").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payingTheRenewalQueuesTheNextPeriodWithAutoRenewStillOn() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();

        assertEquals("SUCCESS", payments.processAtCounter(request.getId(), counter(), jwt(receptionist)).getStatus());

        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        assertEquals(2, rows.size());
        assertEquals(current.getEndDate(), rows.get(1).getStartDate());
        assertEquals(current.getEndDate().plusMonths(1), rows.get(1).getEndDate());
        assertEquals(true, rows.get(1).getAutoRenew());
    }

    @Test
    void anUnpaidRenewalIsCancelledOnceTheMembershipEnds() {
        Membership current = membership(member, plan, today.minusDays(28), today.plusDays(2), true);
        Payment request = autoRenewal.createIfDue(current).orElseThrow();

        clock.advance(Duration.ofDays(2));
        membershipService.expireMemberships();
        MembershipAutoRenewalService.RunResult result = autoRenewal.runDaily();

        assertEquals(1, result.cancelled());
        assertEquals(0, result.created());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(request.getId()).orElseThrow().getStatus());
        assertEquals("EXPIRED", memberships.findById(current.getId()).orElseThrow().getStatus());
    }

    @Test
    void aPlanNoLongerSoldIsNotRenewedButTheMemberIsTold() {
        MembershipPlan retired = plan("Retired plan", "INACTIVE");
        Membership current = membership(member, retired, today.minusDays(28), today.plusDays(2), true);

        assertTrue(autoRenewal.createIfDue(current).isEmpty());
        assertTrue(pendingRenewals().isEmpty());
        assertEquals(1, notificationsFor(member));
    }

    // ---------------------------------------------------------------- helpers

    private List<Payment> pendingRenewals() {
        return paymentRows.findByStatusAndPurposeAndNoteStartingWith(
                PaymentStatus.PENDING, PaymentPurpose.MEMBERSHIP, MembershipAutoRenewalService.NOTE_PREFIX);
    }

    private long notificationsFor(User owner) {
        return jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, owner.getId());
    }

    private CounterPaymentRequest counter() {
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(true);
        return request;
    }

    private MembershipPlan plan(String name, String status) {
        MembershipPlan created = new MembershipPlan();
        created.setName(name + " " + System.nanoTime());
        created.setPrice(new BigDecimal("400000"));
        created.setDurationMonths(1);
        created.setCourseDiscountPercent(BigDecimal.ZERO);
        created.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        created.setAllowPersonalTrainingPlan(false);
        created.setAllowOneOnOneCoaching(false);
        created.setStatus(status);
        return plans.save(created);
    }

    private Membership membership(User owner, MembershipPlan forPlan, LocalDate start, LocalDate end, boolean autoRenew) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(forPlan);
        membership.setAutoRenew(autoRenew);
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
        paymentRows.deleteAllInBatch();
        memberships.deleteAllInBatch();
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
        TestClock membershipAutoRenewalTestClock() {
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
