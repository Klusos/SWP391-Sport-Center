package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipRenewalIntentServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Integer MEMBER_ID = 71;
    private static final Integer SOURCE_ID = 81;
    private static final Integer PLAN_ID = 91;

    @Mock MembershipRepository memberships;
    @Mock MembershipRenewalIntentRepository intents;
    @Mock MembershipPlanRepository plans;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock AuditLogService auditLogs;

    private User member;
    private Membership source;
    private MembershipPlan sourcePlan;
    private MembershipPlan currentPlan;
    private MembershipRenewalIntentService service;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(MEMBER_ID);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");

        sourcePlan = plan("Historical source terms", "80000.00", 1, "0.00", false);
        sourcePlan.setId(PLAN_ID);
        currentPlan = plan("Current plan quote", "125000.00", 2, "15.00", true);
        currentPlan.setId(PLAN_ID);

        source = new Membership();
        source.setId(SOURCE_ID);
        source.setMember(member);
        source.setPlan(sourcePlan);
        source.setAutoRenew(true);
        source.setStatus("ACTIVE");
        source.setStartDate(LocalDate.of(2026, 10, 1));
        source.setEndDate(LocalDate.of(2026, 11, 23));
        service = serviceAt("2026-11-20T05:00:00Z"); // 12:00 on the window's first VN business date.
    }

    @Test
    void createsSchedulerIntentWithCurrentPlanSnapshotAndExclusiveSuccessorPeriod() {
        stubEligibleCreation();
        when(intents.saveAndFlush(any(MembershipRenewalIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipRenewalIntentService.CreationResult result = service.createForScheduler(SOURCE_ID);

        assertEquals(MembershipRenewalIntentService.Outcome.CREATED, result.outcome());
        MembershipRenewalIntent intent = result.intent();
        verify(auditLogs).record(eq("CREATE"), eq("RENEWAL_INTENT"), eq(intent.getId()),
                org.mockito.ArgumentMatchers.contains("status=NONE->OPEN"));
        assertSame(source, intent.getSourceMembership());
        assertSame(currentPlan, intent.getTargetPlan());
        assertEquals(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER, intent.getCreationSource());
        assertEquals(RenewalIntentStatus.OPEN, intent.getIntentStatus());
        assertEquals(true, intent.getAutoRenewOptInSnapshot());
        assertEquals("Current plan quote", intent.getPlanNameSnapshot());
        assertEquals(new BigDecimal("125000.00"), intent.getPlanPriceSnapshot());
        assertEquals(new BigDecimal("125000.00"), intent.getQuotedAmount());
        assertEquals(2, intent.getDurationMonthsSnapshot());
        assertEquals(new BigDecimal("15.00"), intent.getCourseDiscountPercentSnapshot());
        assertEquals(new BigDecimal("5.00"), intent.getPtDiscountPercentSnapshot());
        assertEquals(true, intent.getAllowPtPlanSnapshot());
        assertEquals(false, intent.getAllowOneOnOneSnapshot());
        assertEquals(LocalDate.of(2026, 11, 20).atStartOfDay(), intent.getValidFrom());
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(), intent.getValidUntil());
        assertEquals(LocalDate.of(2026, 11, 23), intent.getSuccessorStartDate());
        assertEquals(LocalDate.of(2027, 1, 23), intent.getSuccessorEndDate());
        assertNull(intent.getSettledPayment());

        verify(payments, never()).save(any());
        verify(memberships, never()).save(any());
    }

    @Test
    void rejectsWhenAutoRenewIsOff() {
        source.setAutoRenew(false);
        when(memberships.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(SOURCE_ID)).thenReturn(Optional.empty());
        when(payments.existsByMember_IdAndPurposeInAndStatusIn(any(), any(), any())).thenReturn(false);
        when(memberships.findByIdForRenewalUpdate(SOURCE_ID)).thenReturn(Optional.of(source));

        assertEquals(MembershipRenewalIntentService.Outcome.INELIGIBLE_SOURCE,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonActiveSourceMembership() {
        source.setStatus("CANCELLED");
        stubEligibleCreation();

        assertEquals(MembershipRenewalIntentService.Outcome.INELIGIBLE_SOURCE,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInactiveCurrentPlan() {
        currentPlan.setStatus("INACTIVE");
        stubEligibleCreation();

        assertEquals(MembershipRenewalIntentService.Outcome.INACTIVE_PLAN,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
        verify(notifications).createForUserOnce(eq(MEMBER_ID), eq("Renewal unavailable"), any());
    }

    @Test
    void acceptsExactStartOfThreeDayWindowInVietnamTime() {
        service = serviceAt("2026-11-19T17:00:00Z"); // 00:00 VN on 2026-11-20.
        stubEligibleCreation();
        when(intents.saveAndFlush(any(MembershipRenewalIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(MembershipRenewalIntentService.Outcome.CREATED,
                service.createForScheduler(SOURCE_ID).outcome());
    }

    @Test
    void acceptsTMinusTwoAndTMinusOneWithinTheThreeDayWindow() {
        for (String instant : List.of("2026-11-21T05:00:00Z", "2026-11-22T05:00:00Z")) {
            service = serviceAt(instant);
            stubEligibleCreation();
            when(intents.saveAndFlush(any(MembershipRenewalIntent.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            assertEquals(MembershipRenewalIntentService.Outcome.CREATED,
                    service.createForScheduler(SOURCE_ID).outcome(), "expected eligible at " + instant);
        }
    }

    @Test
    void enablingRenewalAfterEnteringTheWindowAllowsIntentCreation() {
        service = serviceAt("2026-11-21T05:00:00Z");
        source.setAutoRenew(false);
        stubEligibleCreation();
        assertEquals(MembershipRenewalIntentService.Outcome.INELIGIBLE_SOURCE,
                service.createForScheduler(SOURCE_ID).outcome());

        source.setAutoRenew(true);
        when(intents.saveAndFlush(any(MembershipRenewalIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(MembershipRenewalIntentService.Outcome.CREATED,
                service.createForScheduler(SOURCE_ID).outcome());
    }

    @Test
    void rejectsAtExclusiveEndDateMidnight() {
        service = serviceAt("2026-11-22T17:00:00Z"); // 00:00 VN at source endDate.
        stubEligibleCreation();

        assertEquals(MembershipRenewalIntentService.Outcome.OUTSIDE_WINDOW,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOutsideThreeDayWindow() {
        service = serviceAt("2026-11-19T16:59:59Z");
        stubEligibleCreation();

        assertEquals(MembershipRenewalIntentService.Outcome.OUTSIDE_WINDOW,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPendingPurchaseOrUpgradePayment() {
        when(memberships.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(SOURCE_ID)).thenReturn(Optional.empty());
        when(payments.existsByMember_IdAndPurposeInAndStatusIn(
                MEMBER_ID, List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE),
                List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED))).thenReturn(true);

        assertEquals(MembershipRenewalIntentService.Outcome.CONFLICTING_PAYMENT,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(memberships, never()).findByIdForRenewalUpdate(any());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void existingOpenOrTerminalIntentIsNotReopenedOrReplaced() {
        MembershipRenewalIntent prior = new MembershipRenewalIntent();
        prior.setIntentStatus(RenewalIntentStatus.EXPIRED);
        when(memberships.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(SOURCE_ID)).thenReturn(Optional.of(prior));

        MembershipRenewalIntentService.CreationResult result = service.createForScheduler(SOURCE_ID);

        assertEquals(MembershipRenewalIntentService.Outcome.ALREADY_EXISTS, result.outcome());
        assertSame(prior, result.intent());
        assertEquals(RenewalIntentStatus.EXPIRED, result.intent().getIntentStatus());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExistingPaidSuccessorOrLegacyFutureMembership() {
        stubEligibleCreation();
        when(memberships.existsByMemberIdAndStatusAndStartDateGreaterThan(
                MEMBER_ID, "ACTIVE", LocalDate.of(2026, 11, 20))).thenReturn(true);

        assertEquals(MembershipRenewalIntentService.Outcome.CONFLICTING_MEMBERSHIP,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWhenSourceIsNotTheOnlyEffectiveMembership() {
        stubEligibleCreation();
        Membership otherEffective = new Membership();
        otherEffective.setId(999);
        when(memberships.findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                MEMBER_ID, "ACTIVE", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20)))
                .thenReturn(List.of(source, otherEffective));

        assertEquals(MembershipRenewalIntentService.Outcome.CONFLICTING_MEMBERSHIP,
                service.createForScheduler(SOURCE_ID).outcome());
        verify(intents, never()).saveAndFlush(any());
    }

    @Test
    void repeatedSchedulerCallReturnsExistingIntentWithoutChangingItsSnapshot() {
        stubEligibleCreation();
        AtomicReference<MembershipRenewalIntent> stored = new AtomicReference<>();
        when(intents.findBySourceMembershipIdForUpdate(SOURCE_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(intents.saveAndFlush(any(MembershipRenewalIntent.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });

        MembershipRenewalIntentService.CreationResult created = service.createForScheduler(SOURCE_ID);
        currentPlan.setPrice(new BigDecimal("999999.00"));
        currentPlan.setName("Edited after capture");
        MembershipRenewalIntentService.CreationResult repeated = service.createForScheduler(SOURCE_ID);

        assertEquals(MembershipRenewalIntentService.Outcome.CREATED, created.outcome());
        assertEquals(MembershipRenewalIntentService.Outcome.ALREADY_EXISTS, repeated.outcome());
        assertSame(created.intent(), repeated.intent());
        assertEquals(new BigDecimal("125000.00"), repeated.intent().getQuotedAmount());
        assertEquals("Current plan quote", repeated.intent().getPlanNameSnapshot());
        verify(intents, times(1)).saveAndFlush(any(MembershipRenewalIntent.class));
        verify(auditLogs, times(1)).record(eq("CREATE"), eq("RENEWAL_INTENT"), eq(created.intent().getId()),
                org.mockito.ArgumentMatchers.contains("status=NONE->OPEN"));
        verify(users, times(2)).findByIdForBookingUpdate(MEMBER_ID);
    }

    @Test
    void memberRowLockIsAcquiredBeforeIntentAndPaymentConflictChecks() {
        stubEligibleCreation();
        when(intents.saveAndFlush(any(MembershipRenewalIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createForScheduler(SOURCE_ID);

        ArgumentCaptor<Integer> memberId = ArgumentCaptor.forClass(Integer.class);
        var lockOrder = inOrder(users, intents, payments, memberships);
        lockOrder.verify(users).findByIdForBookingUpdate(memberId.capture());
        assertEquals(MEMBER_ID, memberId.getValue());
        lockOrder.verify(intents).findBySourceMembershipIdForUpdate(SOURCE_ID);
        lockOrder.verify(payments).existsByMember_IdAndPurposeInAndStatusIn(any(), any(), any());
        lockOrder.verify(memberships).findByIdForRenewalUpdate(SOURCE_ID);
    }

    private void stubEligibleCreation() {
        when(memberships.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(SOURCE_ID)).thenReturn(Optional.empty());
        when(payments.existsByMember_IdAndPurposeInAndStatusIn(
                MEMBER_ID, List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE),
                List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED))).thenReturn(false);
        when(memberships.findByIdForRenewalUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
        when(memberships.findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                eq(MEMBER_ID), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(source));
        when(memberships.existsByMemberIdAndStatusAndStartDateGreaterThan(
                eq(MEMBER_ID), eq("ACTIVE"), any(LocalDate.class))).thenReturn(false);
        when(plans.findByIdForUpgradeQuote(PLAN_ID)).thenReturn(Optional.of(currentPlan));
    }

    private MembershipRenewalIntentService serviceAt(String instant) {
        Clock fixedClock = Clock.fixed(Instant.parse(instant), BUSINESS_ZONE);
        return new MembershipRenewalIntentService(memberships, intents, plans, payments, users, fixedClock,
                notifications, auditLogs);
    }

    private MembershipPlan plan(String name, String price, int duration, String courseDiscount, boolean ptPlan) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName(name);
        plan.setDescription(name + " description");
        plan.setPrice(new BigDecimal(price));
        plan.setDurationMonths(duration);
        plan.setCourseDiscountPercent(new BigDecimal(courseDiscount));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(ptPlan);
        plan.setAllowOneOnOneCoaching(false);
        plan.setStatus("ACTIVE");
        return plan;
    }
}
