package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
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
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.AuditLog;
import group5.example.sportCenter.entity.MembershipCancellationRequest;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipCancellationRequestRepository;
import group5.example.sportCenter.repository.AuditLogRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
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
    @MockitoSpyBean AuditLogRepository auditLogs;
    @Autowired PaymentRepository paymentRows;
    @Autowired MembershipUpgradeRepository upgrades;
    @Autowired MembershipRenewalIntentRepository renewalIntents;
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
                .andExpect(jsonPath("$.blockedReason").value("UNKNOWN_PAID_VALUE"));
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
        attachSuccessfulPayment(otherUpcoming, other, standard.getPrice(), PaymentPurpose.MEMBERSHIP);
        Integer otherRequest = requestCancellation(other, otherUpcoming, null);
        mvc.perform(patch("/api/memberships/" + otherUpcoming.getId() + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Duplicate package issued\"}"))
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
    void adminCancelsARunningMembershipWithFullActualPaidAmountRefundAndAudit() throws Exception {
        Payment paid = attachSuccessfulPayment(current, member, new BigDecimal("500000.00"), PaymentPurpose.MEMBERSHIP);
        String url = "/api/memberships/" + current.getId() + "/cancel";
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, auditLogs.findAll().stream()
                .filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                        && String.valueOf(current.getId()).equals(entry.getEntityId())
                        && "CANCEL".equals(entry.getAction()))
                .count());
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"" + "x".repeat(251) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Medical certificate, refund review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Medical certificate, refund review"));

        Membership cancelled = memberships.findById(current.getId()).orElseThrow();
        assertEquals(today.plusDays(20), cancelled.getEndDate());
        assertEquals(paid.getId(), cancelled.getPurchasePayment().getId());
        Payment refundRequired = paymentRows.findById(paid.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_REQUIRED, refundRequired.getStatus());
        assertEquals(new BigDecimal("500000.00"), refundRequired.getAmount());
        assertNull(refundRequired.getRefundedAt());
        mvc.perform(get("/api/payments/refund-required").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + paid.getId() + ")]" ).isNotEmpty());
        var cancellationAudit = auditLogs.findAll().stream()
                .filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                        && String.valueOf(current.getId()).equals(entry.getEntityId())
                        && "CANCEL".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertEquals(admin.getId(), cancellationAudit.getActorUserId());
        assertEquals("ADMIN", cancellationAudit.getActorRole());
        org.junit.jupiter.api.Assertions.assertTrue(cancellationAudit.getDescription().contains("status=ACTIVE->CANCELLED"));
        org.junit.jupiter.api.Assertions.assertTrue(cancellationAudit.getDescription().contains("refundAmountDue=500000"));
        org.junit.jupiter.api.Assertions.assertTrue(cancellationAudit.getDescription().contains("reason=Medical certificate, refund review"));
        assertEquals(1, auditLogs.findAll().stream()
                .filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                        && String.valueOf(current.getId()).equals(entry.getEntityId())
                        && "CANCEL".equals(entry.getAction()))
                .count());
        mvc.perform(get(optionsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.blockedReason").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void cancellingAMembershipDoesNotCreateLegacyNoteBasedAutoRenewalPayments() throws Exception {
        User renewing = user("MEMBER", "renewing-cancel");
        Membership upcomingSoon = memberships.save(copy(renewing, standard, today.plusDays(1), today.plusDays(2)));
        upcomingSoon.setAutoRenew(true);
        upcomingSoon = memberships.save(upcomingSoon);
        assertTrue(autoRenewal.createIfDue(upcomingSoon).isEmpty(),
                "the retired note-based payment flow must remain inactive; renewal intents are the current workflow");
        assertTrue(paymentRows.findByMemberIdOrderByCreatedAtDesc(renewing.getId()).isEmpty());

        // Receptionists only review member requests; direct cancellation is ADMIN-only.
        mvc.perform(patch("/api/memberships/" + upcomingSoon.getId() + "/cancel").header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        assertEquals("ACTIVE", memberships.findById(upcomingSoon.getId()).orElseThrow().getStatus());
        attachSuccessfulPayment(upcomingSoon, renewing, standard.getPrice(), PaymentPurpose.MEMBERSHIP);
        mvc.perform(patch("/api/memberships/" + upcomingSoon.getId() + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Renewal test cancellation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Renewal test cancellation"));
        assertEquals(PaymentStatus.REFUND_REQUIRED,
                paymentRows.findByMemberIdOrderByCreatedAtDesc(renewing.getId()).get(0).getStatus());
    }

    @Test
    void exceptionalCancellationFailsClosedWhenUpgradeLineageIsMissing() throws Exception {
        Payment upgradePayment = attachSuccessfulPayment(current, member, new BigDecimal("300000.00"), PaymentPurpose.UPGRADE);
        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Legacy upgrade review\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("chu kỳ Membership hiện tại")));
        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCESS, paymentRows.findById(upgradePayment.getId()).orElseThrow().getStatus());
        assertEquals(0, auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(current.getId()).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction())).count());
    }

    @Test
    void exceptionalCancellationRefundsDirectAndUpgradePaymentsInTheCurrentCycle() throws Exception {
        Payment direct = attachSuccessfulPayment(current, member, new BigDecimal("1000000.00"),
                PaymentPurpose.MEMBERSHIP);
        current.setStatus("UPGRADED");
        memberships.saveAndFlush(current);

        Payment upgradePayment = new Payment();
        upgradePayment.setMember(member);
        upgradePayment.setAmount(new BigDecimal("300000.00"));
        upgradePayment.setMethod(PaymentMethod.COUNTER);
        upgradePayment.setPurpose(PaymentPurpose.UPGRADE);
        upgradePayment.setReferenceId(premium.getId());
        upgradePayment.setStatus(PaymentStatus.SUCCESS);
        upgradePayment.setTransactionCode("EC-UP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        upgradePayment.setCreatedAt(today.atTime(9, 0));
        upgradePayment.setPaidAt(today.atTime(9, 1));
        upgradePayment.setProcessedBy(receptionist);
        upgradePayment = paymentRows.saveAndFlush(upgradePayment);
        Integer upgradePaymentId = upgradePayment.getId();

        Membership replacement = copy(member, premium, today.minusDays(1), today.plusDays(29));
        replacement.setPurchasePayment(upgradePayment);
        replacement = memberships.saveAndFlush(replacement);
        Integer replacementId = replacement.getId();

        MembershipUpgrade upgrade = completeUpgrade(current, replacement, upgradePayment, premium,
                new BigDecimal("200000.00"));
        upgrades.saveAndFlush(upgrade);

        mvc.perform(patch("/api/memberships/" + replacementId + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Full current cycle review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(PaymentStatus.REFUND_REQUIRED, paymentRows.findById(direct.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED,
                paymentRows.findById(upgradePayment.getId()).orElseThrow().getStatus());
        var summary = auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(replacementId).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertTrue(summary.getDescription().contains("eligiblePaidAmount=1300000"));
        assertTrue(summary.getDescription().contains("eligiblePaymentCount=2"));
        assertTrue(summary.getDescription().length() <= 500);
        assertEquals(2, auditLogs.findAll().stream().filter(entry -> "PAYMENT".equals(entry.getEntityType())
                && "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())).count());
        assertTrue(auditLogs.findAll().stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction()))
                .anyMatch(entry -> direct.getId().toString().equals(entry.getEntityId())
                        && entry.getDescription().contains("actualPaidAmount=1000000")));
        assertTrue(auditLogs.findAll().stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction()))
                .anyMatch(entry -> upgradePaymentId.toString().equals(entry.getEntityId())
                        && entry.getDescription().contains("actualPaidAmount=300000")));
        assertEquals(new BigDecimal("200000.00"), upgrades.findByPaymentId(upgradePayment.getId())
                .orElseThrow().getAppliedCreditAmount());
        mvc.perform(get("/api/payments/refund-required").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + direct.getId() + ")]" ).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == " + upgradePayment.getId() + ")]" ).isNotEmpty());
    }

    @Test
    void multipleUpgradeChainAndMaximumReasonKeepCompleteAuditEvidenceWithinColumnLimit() throws Exception {
        Payment direct = attachSuccessfulPayment(current, member, new BigDecimal("1000000.00"),
                PaymentPurpose.MEMBERSHIP);
        current.setStatus("UPGRADED");
        memberships.saveAndFlush(current);

        Membership source = current;
        List<Payment> lineagePayments = new java.util.ArrayList<>();
        lineagePayments.add(direct);
        for (int index = 0; index < 3; index++) {
            Payment upgradePayment = attachPayment(member, premium, new BigDecimal("300000.00"));
            Membership result = copy(member, premium, today.minusDays(1), today.plusDays(29));
            result.setPurchasePayment(upgradePayment);
            result = memberships.saveAndFlush(result);
            upgrades.saveAndFlush(completeUpgrade(source, result, upgradePayment, premium,
                    new BigDecimal("200000.00")));
            lineagePayments.add(upgradePayment);
            source = result;
            if (index < 2) {
                source.setStatus("UPGRADED");
                memberships.saveAndFlush(source);
            }
        }
        Integer targetId = source.getId();
        String reason = "R".repeat(250);

        mvc.perform(patch("/api/memberships/" + targetId + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals("CANCELLED", memberships.findById(targetId).orElseThrow().getStatus());
        assertEquals(new BigDecimal("1900000.00"), lineagePayments.stream()
                .map(payment -> paymentRows.findById(payment.getId()).orElseThrow())
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(lineagePayments.stream().allMatch(payment ->
                paymentRows.findById(payment.getId()).orElseThrow().getStatus() == PaymentStatus.REFUND_REQUIRED));

        var entries = auditLogs.findAll();
        var summary = entries.stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(targetId).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertEquals(admin.getId(), summary.getActorUserId());
        assertTrue(summary.getDescription().contains("actorUserId=" + admin.getId() + ";actorRole=ADMIN"));
        assertTrue(summary.getDescription().contains("cycleRootMembershipId=" + current.getId()));
        assertTrue(summary.getDescription().contains("eligiblePaidAmount=1900000"));
        assertTrue(summary.getDescription().contains("refundAmountDue=1900000"));
        assertTrue(summary.getDescription().contains("reason=" + reason));
        assertTrue(summary.getDescription().length() <= 500);
        for (Payment payment : lineagePayments) {
            var evidence = entries.stream().filter(entry -> "PAYMENT".equals(entry.getEntityType())
                    && payment.getId().toString().equals(entry.getEntityId())
                    && "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())).findFirst().orElseThrow();
            assertTrue(evidence.getDescription().contains("membershipId=" + targetId));
            assertTrue(evidence.getDescription().contains("status=SUCCESS->REFUND_REQUIRED"));
            assertTrue(evidence.getDescription().contains("disposition=NEW_REFUND_OBLIGATION_PENDING_DISBURSEMENT"));
            assertTrue(evidence.getDescription().contains("actualPaidAmount="
                    + payment.getAmount().stripTrailingZeros().toPlainString()));
            assertTrue(evidence.getDescription().length() <= 500);
        }
        long auditCount = entries.stream().filter(entry -> "CANCEL".equals(entry.getAction())
                && String.valueOf(targetId).equals(entry.getEntityId())).count()
                + entries.stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())).count();
        long refundRequiredCount = paymentRows.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.REFUND_REQUIRED).count();
        mvc.perform(patch("/api/memberships/" + targetId + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Repeated request\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals(auditCount, auditLogs.findAll().stream().filter(entry -> "CANCEL".equals(entry.getAction())
                && String.valueOf(targetId).equals(entry.getEntityId())).count()
                + auditLogs.findAll().stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())).count());
        assertEquals(refundRequiredCount, paymentRows.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.REFUND_REQUIRED).count());
    }

    @Test
    void existingRefundRequiredAndRefundedPaymentsAreAuditedAsPreservedNotNewTransitions() throws Exception {
        Payment alreadyRequired = attachSuccessfulPayment(current, member, new BigDecimal("500000.00"),
                PaymentPurpose.MEMBERSHIP);
        alreadyRequired.setStatus(PaymentStatus.REFUND_REQUIRED);
        paymentRows.saveAndFlush(alreadyRequired);
        current.setStatus("UPGRADED");
        memberships.saveAndFlush(current);

        Payment alreadyRefunded = attachPayment(member, premium, new BigDecimal("300000.00"));
        alreadyRefunded.setStatus(PaymentStatus.REFUNDED);
        alreadyRefunded.setRefundedAt(today.atTime(9, 30));
        paymentRows.saveAndFlush(alreadyRefunded);
        Membership replacement = copy(member, premium, today.minusDays(1), today.plusDays(29));
        replacement.setPurchasePayment(alreadyRefunded);
        replacement = memberships.saveAndFlush(replacement);
        upgrades.saveAndFlush(completeUpgrade(current, replacement, alreadyRefunded, premium,
                new BigDecimal("200000.00")));
        Integer replacementId = replacement.getId();

        mvc.perform(patch("/api/memberships/" + replacementId + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Preserve existing refund history\"}"))
                .andExpect(status().isOk());

        assertEquals("CANCELLED", memberships.findById(replacement.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED,
                paymentRows.findById(alreadyRequired.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRows.findById(alreadyRefunded.getId()).orElseThrow().getStatus());
        assertEquals(today.atTime(9, 30), paymentRows.findById(alreadyRefunded.getId()).orElseThrow().getRefundedAt());
        var entries = auditLogs.findAll();
        var summary = entries.stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(replacementId).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertTrue(summary.getDescription().contains("eligiblePaidAmount=800000"));
        assertTrue(summary.getDescription().contains("refundAmountDue=500000"));
        assertEquals("ADMIN", summary.getActorRole());
        assertEquals(1, entries.stream().filter(entry -> "EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE".equals(entry.getAction())
                && alreadyRequired.getId().toString().equals(entry.getEntityId())
                && entry.getDescription().contains("status=REFUND_REQUIRED->REFUND_REQUIRED")
                && entry.getDescription().contains("EXISTING_REFUND_OBLIGATION_PRESERVED")).count());
        assertEquals(1, entries.stream().filter(entry -> "EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE".equals(entry.getAction())
                && alreadyRefunded.getId().toString().equals(entry.getEntityId())
                && entry.getDescription().contains("status=REFUNDED->REFUNDED")
                && entry.getDescription().contains("ALREADY_REFUNDED_NO_DUPLICATE_OBLIGATION")).count());
        assertEquals(0, entries.stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())
                && alreadyRequired.getId().toString().equals(entry.getEntityId())).count());
        assertEquals(0, entries.stream().filter(entry -> "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())
                && alreadyRefunded.getId().toString().equals(entry.getEntityId())).count());
    }

    @Test
    void refundedPaymentWithoutRefundTimestampIsRejectedWithoutCancellationOrAudit() throws Exception {
        Payment inconsistent = attachSuccessfulPayment(current, member, new BigDecimal("500000.00"),
                PaymentPurpose.MEMBERSHIP);
        inconsistent.setStatus(PaymentStatus.REFUNDED);
        inconsistent.setRefundedAt(null);
        paymentRows.saveAndFlush(inconsistent);

        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Reject inconsistent financial state\"}"))
                .andExpect(status().isConflict());

        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRows.findById(inconsistent.getId()).orElseThrow().getStatus());
        assertEquals(0, auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && current.getId().toString().equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction())).count());
    }

    private Payment attachPayment(User owner, MembershipPlan target, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setMember(owner);
        payment.setAmount(amount);
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.UPGRADE);
        payment.setReferenceId(target.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode("EC-MU-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setCreatedAt(today.atTime(9, 0));
        payment.setPaidAt(today.atTime(9, 1));
        payment.setProcessedBy(receptionist);
        return paymentRows.saveAndFlush(payment);
    }

    @Test
    void exceptionalCancellationAtRenewalRootExcludesThePreviousMembershipCycle() throws Exception {
        Payment previousCyclePayment = attachSuccessfulPayment(current, member,
                new BigDecimal("500000.00"), PaymentPurpose.MEMBERSHIP);
        current.setAutoRenew(true);
        memberships.saveAndFlush(current);

        var now = LocalDate.now(BUSINESS_ZONE).atTime(10, 0);
        MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setSourceMembership(current);
        intent.setTargetPlan(standard);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true);
        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(now.minusDays(2));
        intent.setValidFrom(current.getEndDate().minusDays(3).atStartOfDay());
        intent.setValidUntil(current.getEndDate().atStartOfDay());
        intent.setQuoteCapturedAt(now.minusDays(2));
        intent.setSourceEndDateSnapshot(current.getEndDate());
        intent.setSuccessorStartDate(current.getEndDate());
        intent.setSuccessorEndDate(current.getEndDate().plusMonths(1));
        intent.setPlanNameSnapshot(standard.getName());
        intent.setPlanDescriptionSnapshot(standard.getDescription());
        intent.setPlanPriceSnapshot(standard.getPrice());
        intent.setQuotedAmount(new BigDecimal("725000.00"));
        intent.setDurationMonthsSnapshot(1);
        intent.setCourseDiscountPercentSnapshot(standard.getCourseDiscountPercent());
        intent.setPtDiscountPercentSnapshot(standard.getPersonalTrainingDiscountPercent());
        intent.setAllowPtPlanSnapshot(standard.getAllowPersonalTrainingPlan());
        intent.setAllowOneOnOneSnapshot(standard.getAllowOneOnOneCoaching());
        intent = renewalIntents.saveAndFlush(intent);

        Payment renewalPayment = new Payment();
        renewalPayment.setMember(member);
        renewalPayment.setAmount(new BigDecimal("725000.00"));
        renewalPayment.setMethod(PaymentMethod.ONLINE);
        renewalPayment.setPurpose(PaymentPurpose.MEMBERSHIP);
        renewalPayment.setReferenceId(standard.getId());
        renewalPayment.setStatus(PaymentStatus.SUCCESS);
        renewalPayment.setTransactionCode("EC-REN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        renewalPayment.setCreatedAt(now.minusDays(1));
        renewalPayment.setPaidAt(now.minusHours(1));
        renewalPayment.setRenewalIntent(intent);
        renewalPayment.setRenewalSettlementAcceptedAt(now.minusMinutes(30));
        renewalPayment = paymentRows.saveAndFlush(renewalPayment);
        Integer renewalPaymentId = renewalPayment.getId();

        intent.setIntentStatus(RenewalIntentStatus.SETTLED);
        intent.setSettledPayment(renewalPayment);
        intent.setSettledAt(now.minusMinutes(30));
        renewalIntents.saveAndFlush(intent);

        Membership renewed = copy(member, standard, current.getEndDate(), current.getEndDate().plusMonths(1));
        renewed.setPurchasePayment(renewalPayment);
        renewed.setRenewalIntent(intent);
        renewed = memberships.saveAndFlush(renewed);
        Integer renewedId = renewed.getId();

        mvc.perform(patch("/api/memberships/" + renewedId + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Renewal cycle review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(PaymentStatus.SUCCESS, paymentRows.findById(previousCyclePayment.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUND_REQUIRED, paymentRows.findById(renewalPayment.getId()).orElseThrow().getStatus());
        var summary = auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(renewedId).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction()))
                .findFirst().orElseThrow();
        assertTrue(summary.getDescription().contains("eligiblePaidAmount=725000"));
        assertTrue(summary.getDescription().contains("cycleRootMembershipId=" + renewedId));
        assertTrue(summary.getDescription().contains("eligiblePaymentCount=1"));
        assertTrue(auditLogs.findAll().stream().anyMatch(entry -> "PAYMENT".equals(entry.getEntityType())
                && renewalPaymentId.toString().equals(entry.getEntityId())
                && "EXCEPTIONAL_CANCEL_REFUND".equals(entry.getAction())));
        assertTrue(auditLogs.findAll().stream().noneMatch(entry -> "PAYMENT".equals(entry.getEntityType())
                && previousCyclePayment.getId().toString().equals(entry.getEntityId())
                && entry.getAction().startsWith("EXCEPTIONAL_CANCEL_")));
    }

    @Test
    void failedCancellationAuditRollsBackMembershipAndRefundObligation() throws Exception {
        Payment paid = attachSuccessfulPayment(current, member, new BigDecimal("500000.00"), PaymentPurpose.MEMBERSHIP);
        doThrow(new IllegalStateException("audit persistence unavailable"))
                .when(auditLogs).insert(argThat((AuditLog entry) -> "MEMBERSHIP".equals(entry.getEntityType())
                        && String.valueOf(current.getId()).equals(entry.getEntityId())
                        && "CANCEL".equals(entry.getAction())));

        mvc.perform(patch("/api/memberships/" + current.getId() + "/cancel")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"Atomic audit failure test\"}"))
                .andExpect(status().is5xxServerError());

        assertEquals("ACTIVE", memberships.findById(current.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCESS, paymentRows.findById(paid.getId()).orElseThrow().getStatus());
        assertEquals(0, auditLogs.findAll().stream().filter(entry -> "MEMBERSHIP".equals(entry.getEntityType())
                && String.valueOf(current.getId()).equals(entry.getEntityId()) && "CANCEL".equals(entry.getAction())).count());
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
        // This suite exercises cancellation request and refund transitions, not ordinary
        // Purchase eligibility. Build the upcoming paid state directly so the fixture can
        // coexist with the intentionally effective current Membership under current policy.
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(standard.getPrice());
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(standard.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode("c-" + UUID.randomUUID());
        payment.setCreatedAt(today.atTime(10, 0));
        payment.setPaidAt(today.atTime(10, 0));
        payment.setProcessedBy(receptionist);
        Payment savedPayment = paymentRows.saveAndFlush(payment);

        Membership upcoming = copy(member, standard, current.getEndDate().plusDays(1),
                current.getEndDate().plusMonths(1));
        upcoming.setPurchasePayment(savedPayment);
        memberships.saveAndFlush(upcoming);
        return savedPayment;
    }

    private Payment attachSuccessfulPayment(Membership membership, User owner, BigDecimal amount, PaymentPurpose purpose) {
        Payment payment = new Payment();
        payment.setMember(owner);
        payment.setAmount(amount);
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(purpose);
        payment.setReferenceId(standard.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode("EC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payment.setCreatedAt(today.atTime(9, 0));
        payment.setPaidAt(today.atTime(9, 1));
        payment.setProcessedBy(receptionist);
        Payment saved = paymentRows.saveAndFlush(payment);
        membership.setPurchasePayment(saved);
        memberships.saveAndFlush(membership);
        return saved;
    }

    private MembershipUpgrade completeUpgrade(Membership source, Membership result, Payment payment,
            MembershipPlan targetPlan, BigDecimal appliedCredit) {
        MembershipUpgrade upgrade = new MembershipUpgrade();
        upgrade.setPayment(payment);
        upgrade.setSourceMembership(source);
        upgrade.setResultMembership(result);
        upgrade.setTargetPlan(targetPlan);
        int totalDays = (int) java.time.temporal.ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate());
        upgrade.setSourcePeriodStartDate(source.getStartDate());
        upgrade.setSourcePeriodEndDate(source.getEndDate());
        upgrade.setCreditValuationDate(source.getEndDate().minusDays(3));
        upgrade.setSourceTotalDays(totalDays);
        upgrade.setSourceUnusedDays(3);
        upgrade.setRemainingDays(3);
        upgrade.setSourcePaidValueBasisAmount(new BigDecimal("1000000.00"));
        upgrade.setTargetPlanNameSnapshot(targetPlan.getName());
        upgrade.setTargetPlanDescriptionSnapshot(targetPlan.getDescription());
        upgrade.setTargetPlanPriceSnapshot(targetPlan.getPrice());
        upgrade.setTargetDurationMonthsSnapshot(targetPlan.getDurationMonths());
        upgrade.setTargetCourseDiscountPercentSnapshot(targetPlan.getCourseDiscountPercent());
        upgrade.setTargetPtDiscountPercentSnapshot(targetPlan.getPersonalTrainingDiscountPercent());
        upgrade.setTargetAllowPtPlanSnapshot(targetPlan.getAllowPersonalTrainingPlan());
        upgrade.setTargetAllowOneOnOneSnapshot(targetPlan.getAllowOneOnOneCoaching());
        upgrade.setAppliedCreditAmount(appliedCredit);
        upgrade.setCreatedAt(today.atTime(9, 0));
        upgrade.setExpiresAt(today.plusDays(1).atTime(9, 0));
        return upgrade;
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
        jdbc.update("UPDATE membership SET renewal_intent_id=NULL");
        jdbc.update("UPDATE membership_renewal_intent SET settled_payment_id=NULL, settled_at=NULL");
        jdbc.update("UPDATE payment SET renewal_intent_id=NULL");
        renewalIntents.deleteAllInBatch();
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
