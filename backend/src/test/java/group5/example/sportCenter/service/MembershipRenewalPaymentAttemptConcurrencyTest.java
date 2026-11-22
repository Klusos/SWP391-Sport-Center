package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/** H2 transaction test: competing service calls use independent thread-bound transactions. */
@SpringBootTest(properties = {
        "app.payment.provider=mock",
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF"
})
@ActiveProfiles("phase1")
@Import(MembershipRenewalPaymentAttemptConcurrencyTest.FixedClockConfiguration.class)
class MembershipRenewalPaymentAttemptConcurrencyTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMAIL = "renewal-attempt-race@phase5b3.example.test";

    @Autowired MembershipRenewalPaymentAttemptService service;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipRenewalIntentRepository intents;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private User member;
    private Membership source;
    private MembershipRenewalIntent intent;

    @BeforeEach
    void setUp() {
        assertEquals(1, jdbc.queryForObject("select 1", Integer.class));
        cleanFixture();
        member = new User();
        member.setName("Renewal attempt race");
        member.setEmail(EMAIL);
        member.setPasswordHash("test-only");
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        member = users.saveAndFlush(member);

        MembershipPlan plan = new MembershipPlan();
        plan.setName("Frozen Renewal Plan");
        plan.setPrice(new BigDecimal("125000.00"));
        plan.setDurationMonths(2);
        plan.setCourseDiscountPercent(BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("INACTIVE"); // Intent must remain usable after a later Plan deactivation.
        plan = plans.saveAndFlush(plan);

        source = new Membership();
        source.setMember(member);
        source.setPlan(plan);
        source.setAutoRenew(true);
        source.setStartDate(LocalDate.of(2026, 10, 1));
        source.setEndDate(LocalDate.of(2026, 11, 23));
        source.setStatus("ACTIVE");
        source = memberships.saveAndFlush(source);

        intent = new MembershipRenewalIntent();
        intent.setSourceMembership(source);
        intent.setTargetPlan(plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true);
        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(LocalDateTime.of(2026, 11, 20, 12, 0));
        intent.setValidFrom(LocalDate.of(2026, 11, 20).atStartOfDay());
        intent.setValidUntil(LocalDate.of(2026, 11, 23).atStartOfDay());
        intent.setQuoteCapturedAt(LocalDateTime.of(2026, 11, 20, 12, 0));
        intent.setSourceEndDateSnapshot(source.getEndDate());
        intent.setSuccessorStartDate(source.getEndDate());
        intent.setSuccessorEndDate(source.getEndDate().plusMonths(2));
        intent.setPlanNameSnapshot("Plan captured before deactivation");
        intent.setPlanDescriptionSnapshot("Frozen terms");
        intent.setPlanPriceSnapshot(new BigDecimal("125000.00"));
        intent.setQuotedAmount(new BigDecimal("125000.00"));
        intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(BigDecimal.ZERO);
        intent.setPtDiscountPercentSnapshot(BigDecimal.ZERO);
        intent.setAllowPtPlanSnapshot(false);
        intent.setAllowOneOnOneSnapshot(true);
        intent = intents.saveAndFlush(intent);
    }

    @AfterEach
    void tearDown() {
        cleanFixture();
    }

    @Test
    void concurrentAttemptRequestsCreateExactlyOneLivePendingAttempt() throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = workers.submit(() -> attemptAfterBarrier(startTogether));
            Future<String> second = workers.submit(() -> attemptAfterBarrier(startTogether));

            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
            List<Payment> attempts = payments.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                    .filter(payment -> payment.getRenewalIntent() != null
                            && intent.getId().equals(payment.getRenewalIntent().getId()))
                    .toList();
            assertEquals(1, attempts.size());
            assertEquals(1, memberships.findByMemberId(member.getId()).size(),
                    "Creating payment attempts must not create or extend a Membership");
        } finally {
            workers.shutdownNow();
        }
    }

    private String attemptAfterBarrier(CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            service.createAttempt(intent.getId(), PaymentMethod.ONLINE);
            return "CREATED";
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode() != HttpStatus.CONFLICT) throw conflict;
            return "CONFLICT";
        }
    }

    private void cleanFixture() {
        jdbc.update("delete from Payment where renewal_intent_id in "
                + "(select id from membership_renewal_intent where source_membership_id in "
                + "(select id from Membership where member_id in (select id from users where email = ?)))", EMAIL);
        jdbc.update("delete from membership_renewal_intent where source_membership_id in "
                + "(select id from Membership where member_id in (select id from users where email = ?))", EMAIL);
        jdbc.update("delete from Membership where member_id in (select id from users where email = ?)", EMAIL);
        jdbc.update("delete from membership_plan where name = 'Frozen Renewal Plan'");
        jdbc.update("delete from users where email = ?", EMAIL);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock phase5b3Clock() {
            return Clock.fixed(LocalDate.of(2026, 11, 20).atTime(12, 0).atZone(VN).toInstant(), VN);
        }
    }
}
