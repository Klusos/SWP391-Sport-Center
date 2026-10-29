package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

import com.jayway.jsonpath.JsonPath;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipCancellationRequest;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipCancellationRequestRepository;
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
    private static final String REQUESTS_URL = "/api/memberships/cancellation-requests";

    @Autowired PaymentService payments;
    @Autowired MembershipAutoRenewalService autoRenewal;
    @Autowired MembershipCancellationRequestService cancellationRequests;
    @Autowired MembershipCancellationRequestRepository requestRows;
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
    void memberRequestsCancellationAndStaffApprovalMarksThePaymentForFullRefund() throws Exception {
        Payment purchase = buyRenewal();
        Membership upcoming = latest();
        assertEquals(purchase.getId(), upcoming.getPurchasePayment().getId());

        // Upcoming renewals block mid-term upgrades until they are cancelled.
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("QUEUED_MEMBERSHIP"));

        // Members can no longer cancel directly.
        mvc.perform(patch("/api/memberships/" + upcoming.getId() + "/cancel").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        Integer requestId = requestCancellation(member, upcoming, "Chuyển công tác");
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"membershipId\":" + upcoming.getId() + "}"))
                .andExpect(status().isConflict());
        // Staff were notified of the new request.
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, receptionist.getId()));
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, admin.getId()));

        mvc.perform(get(REQUESTS_URL).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get(REQUESTS_URL).header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].reason").value("Chuyển công tác"))
                .andExpect(jsonPath("$[0].purchasePaymentId").value(purchase.getId()));

        mvc.perform(patch(REQUESTS_URL + "/" + requestId + "/approve").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedByName").value(receptionist.getName()));
        mvc.perform(patch(REQUESTS_URL + "/" + requestId + "/approve").header("Authorization", bearer(receptionist)))
                .andExpect(status().isConflict());

        Membership cancelled = memberships.findById(upcoming.getId()).orElseThrow();
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("Yêu cầu huỷ #" + requestId + ": Chuyển công tác", cancelled.getCancelReason());
        Payment refunded = paymentRows.findById(purchase.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, refunded.getStatus());
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, member.getId()));
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                .andExpect(jsonPath("$.options[0].planName").value("PREMIUM"));
        mvc.perform(get(REQUESTS_URL + "/me").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void staffConfirmsTheRefundAndTheMemberSeesItInPaymentHistory() throws Exception {
        Payment purchase = buyRenewal();
        Membership upcoming = latest();
        mvc.perform(patch(REQUESTS_URL + "/" + requestCancellation(member, upcoming, null) + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/payments/refund-required").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/refund-required").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(purchase.getId()))
                .andExpect(jsonPath("$.content[0].status").value("REFUND_REQUIRED"));

        String refundUrl = "/api/payments/" + purchase.getId() + "/refund";
        mvc.perform(patch(refundUrl).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(refundUrl).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"note\":\"Tiền mặt tại quầy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedByName").value(receptionist.getName()))
                .andExpect(jsonPath("$.refundedAt").exists());
        mvc.perform(patch(refundUrl).header("Authorization", bearer(receptionist)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/payments/refund-required").header("Authorization", bearer(receptionist)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/payments/me").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$[0].id").value(purchase.getId()))
                .andExpect(jsonPath("$[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$[0].note").value(org.hamcrest.Matchers.endsWith("Refund: Tiền mặt tại quầy")));
        // Cancellation notice + refund notice.
        assertEquals(2L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, member.getId()));
    }

    @Test
    void requestsCloseSeventyTwoHoursBeforeTheStartAndMustTargetAnUpcomingMembership() throws Exception {
        User lateMember = user("MEMBER", "late-cancel");
        Membership tooSoon = memberships.save(copy(lateMember, standard, today.plusDays(3), today.plusMonths(1).plusDays(3)));
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(lateMember)).contentType("application/json")
                        .content("{\"membershipId\":" + tooSoon.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("72 hours")));

        User earlyMember = user("MEMBER", "early-cancel");
        Membership inTime = memberships.save(copy(earlyMember, standard, today.plusDays(4), today.plusMonths(1).plusDays(4)));
        requestCancellation(earlyMember, inTime, null);

        // Running memberships, other members' memberships and staff callers are refused.
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"membershipId\":" + current.getId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"membershipId\":" + inTime.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"membershipId\":" + inTime.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffRejectWithANoteAndMembersCanWithdrawPendingRequests() throws Exception {
        buyRenewal();
        Membership upcoming = latest();
        Integer first = requestCancellation(member, upcoming, null);

        String rejectUrl = REQUESTS_URL + "/" + first + "/reject";
        mvc.perform(patch(rejectUrl).header("Authorization", bearer(receptionist)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(rejectUrl).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"note\":\"Gói khuyến mãi không hoàn tiền\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Gói khuyến mãi không hoàn tiền"));
        assertEquals("ACTIVE", memberships.findById(upcoming.getId()).orElseThrow().getStatus());
        assertEquals(1L, jdbc.queryForObject("select count(*) from notification where user_id = ?", Long.class, member.getId()));

        // A new request is allowed after a rejection, and can be withdrawn by its owner only.
        Integer second = requestCancellation(member, upcoming, null);
        User stranger = user("MEMBER", "stranger-cancel");
        mvc.perform(patch(REQUESTS_URL + "/" + second + "/withdraw").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(REQUESTS_URL + "/" + second + "/withdraw").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(patch(REQUESTS_URL + "/" + second + "/approve").header("Authorization", bearer(receptionist)))
                .andExpect(status().isConflict());
        assertEquals("ACTIVE", memberships.findById(upcoming.getId()).orElseThrow().getStatus());
    }

    @Test
    void pendingRequestsExpireOnceTheMembershipStartsAndDirectStaffCancellationAnswersThem() throws Exception {
        buyRenewal();
        Membership upcoming = latest();
        Integer requestId = requestCancellation(member, upcoming, null);

        // Approval is refused once the membership has started; the daily job then closes the request.
        clock.setInstant(upcoming.getStartDate().atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
        mvc.perform(patch(REQUESTS_URL + "/" + requestId + "/approve").header("Authorization", bearer(receptionist)))
                .andExpect(status().isConflict());
        assertEquals(1, cancellationRequests.expireUnapprovable());
        assertEquals(MembershipCancellationRequestStatus.EXPIRED, requestRows.findById(requestId).orElseThrow().getStatus());
        assertEquals("ACTIVE", memberships.findById(upcoming.getId()).orElseThrow().getStatus());

        User other = user("MEMBER", "direct-cancel");
        Membership otherUpcoming = memberships.save(copy(other, standard, upcoming.getStartDate().plusDays(10),
                upcoming.getStartDate().plusDays(40)));
        Integer otherRequest = requestCancellation(other, otherUpcoming, null);
        mvc.perform(patch("/api/memberships/" + otherUpcoming.getId() + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        MembershipCancellationRequest answered = requestRows.findById(otherRequest).orElseThrow();
        assertEquals(MembershipCancellationRequestStatus.APPROVED, answered.getStatus());
        assertEquals(0, cancellationRequests.expireUnapprovable());
    }

    @Test
    void onlyTheLastMembershipCanBeCancelledAndMembersCannotCancelARunningOne() throws Exception {
        buyRenewal();
        Membership firstUpcoming = latest();
        membership(standard, firstUpcoming.getEndDate(), firstUpcoming.getEndDate().plusMonths(1));

        mvc.perform(patch("/api/memberships/" + firstUpcoming.getId() + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(member)).contentType("application/json")
                        .content("{\"membershipId\":" + firstUpcoming.getId() + "}"))
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

        // Receptionists only review member requests; direct cancellation is ADMIN-only.
        mvc.perform(patch("/api/memberships/" + upcomingSoon.getId() + "/cancel").header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        assertEquals("ACTIVE", memberships.findById(upcomingSoon.getId()).orElseThrow().getStatus());
        mvc.perform(patch("/api/memberships/" + upcomingSoon.getId() + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Cancelled by ADMIN before it started"));
        assertEquals(PaymentStatus.CANCELLED, paymentRows.findById(renewal.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- helpers

    private Integer requestCancellation(User owner, Membership target, String reason) throws Exception {
        String body = reason == null ? "{\"membershipId\":" + target.getId() + "}"
                : "{\"membershipId\":" + target.getId() + ",\"reason\":\"" + reason + "\"}";
        String json = mvc.perform(post(REQUESTS_URL).header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

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
        requestRows.deleteAllInBatch();
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
