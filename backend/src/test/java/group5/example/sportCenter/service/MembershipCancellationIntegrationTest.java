package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
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
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
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
@Import(MembershipCancellationIntegrationTest.TestClockConfiguration.class)
class MembershipCancellationIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL_DOMAIN = "@cancel.example.test";

    @Autowired PaymentService payments;
    @Autowired MembershipAutoRenewalService autoRenewal;
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
    private MembershipPlan standard;
    private MembershipPlan premium;
    private Membership current;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(BUSINESS_ZONE);
        clock.setInstant(today.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        cleanDatabase();
        member = user("MEMBER", "member-cancel");
        receptionist = user("RECEPTIONIST", "reception-cancel");
        admin = user("ADMIN", "admin-cancel");
        standard = plan("STANDARD", "500000", "10");
        premium = plan("PREMIUM", "1500000", "15");
        current = membership(standard, today.minusDays(10), today.plusDays(20));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void memberCancelsAnUpcomingRenewalAndItsPaymentIsMarkedForFullRefund() throws Exception {
        Payment purchase = buyRenewal();
        Membership upcoming = latest();
        assertEquals(purchase.getId(), upcoming.getPurchasePayment().getId());

        // Upcoming renewals block mid-term upgrades until they are cancelled.
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("QUEUED_MEMBERSHIP"));

        mvc.perform(patch("/api/memberships/" + upcoming.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cancelled by MEMBER before it started"));

        Payment refunded = paymentRows.findById(purchase.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, refunded.getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, member.getId()));
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                .andExpect(jsonPath("$.options[0].planName").value("PREMIUM"));
    }

    @Test
    void onlyTheLastMembershipCanBeCancelledAndMembersCannotCancelARunningOne() throws Exception {
        buyRenewal();
        Membership firstUpcoming = latest();
        membership(standard, firstUpcoming.getEndDate(), firstUpcoming.getEndDate().plusMonths(1));

        mvc.perform(patch("/api/memberships/" + firstUpcoming.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isConflict());

        User loneMember = user("MEMBER", "lone-cancel");
        Membership running = memberships.save(copy(loneMember, standard, today.minusDays(3), today.plusDays(27)));
        mvc.perform(patch("/api/memberships/" + running.getId() + "/cancel").header("Authorization", bearer(loneMember)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/memberships/" + running.getId() + "/cancel").header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        assertEquals("ACTIVE", memberships.findById(running.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminCancelsARunningMembershipOnlyWithAReasonAndWithoutTouchingThePayment() throws Exception {
        String url = "/api/memberships/" + current.getId() + "/cancel";
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Medical certificate, refund handled at the desk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Medical certificate, refund handled at the desk"));

        Membership cancelled = memberships.findById(current.getId()).orElseThrow();
        assertEquals(today.plusDays(20), cancelled.getEndDate());
        assertNull(cancelled.getPurchasePayment());
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void cancellingAMembershipAlsoCancelsItsPendingAutoRenewal() throws Exception {
        User renewing = user("MEMBER", "renewing-cancel");
        Membership upcomingSoon = memberships.save(copy(renewing, standard, today.plusDays(1), today.plusDays(2)));
        upcomingSoon.setAutoRenew(true);
        upcomingSoon = memberships.save(upcomingSoon);
        Payment renewal = autoRenewal.createIfDue(upcomingSoon).orElseThrow();

        mvc.perform(patch("/api/memberships/" + upcomingSoon.getId() + "/cancel").header("Authorization", bearer(renewing)))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(renewal.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- helpers

    private Payment buyRenewal() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPurpose(PaymentPurpose.MEMBERSHIP);
        request.setReferenceId(standard.getId());
        request.setMethod(PaymentMethod.COUNTER);
        PaymentResponse created = payments.createPayment(request, jwt(member));
        CounterPaymentRequest counter = new CounterPaymentRequest();
        counter.setSuccess(true);
        payments.processAtCounter(created.getId(), counter, jwt(receptionist));
        return paymentRows.findById(created.getId()).orElseThrow();
    }

    private Membership latest() {
        List<Membership> rows = memberships.findByMemberId(member.getId()).stream()
                .sorted(Comparator.comparing(Membership::getStartDate)).toList();
        return rows.get(rows.size() - 1);
    }

    private String optionsUrl() {
        return "/api/memberships/member/" + member.getId() + "/upgrade-options";
    }

    private MembershipPlan plan(String name, String price, String courseDiscount) {
        MembershipPlan created = new MembershipPlan();
        created.setName(name);
        created.setPrice(new BigDecimal(price));
        created.setDurationMonths(1);
        created.setCourseDiscountPercent(new BigDecimal(courseDiscount));
        created.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        created.setAllowPersonalTrainingPlan(false);
        created.setAllowOneOnOneCoaching(false);
        created.setStatus("ACTIVE");
        return plans.save(created);
    }

    private Membership membership(MembershipPlan forPlan, LocalDate start, LocalDate end) {
        return memberships.save(copy(member, forPlan, start, end));
    }

    private Membership copy(User owner, MembershipPlan forPlan, LocalDate start, LocalDate end) {
        Membership membership = new Membership();
        membership.setMember(owner);
        membership.setPlan(forPlan);
        membership.setAutoRenew(false);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
        return membership;
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
        TestClock membershipCancellationTestClock() {
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
