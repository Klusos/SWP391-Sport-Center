package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipRenewalPaymentAttemptServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Integer MEMBER_ID = 71;
    private static final Integer INTENT_ID = 81;
    private static final Integer SOURCE_ID = 91;
    private static final Integer PLAN_ID = 101;
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");

    @Mock MembershipRenewalIntentRepository intents;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;

    private User member;
    private Membership source;
    private MembershipPlan plan;
    private MembershipRenewalIntent intent;
    private MembershipRenewalPaymentAttemptService service;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(MEMBER_ID);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        source = new Membership();
        source.setId(SOURCE_ID);
        source.setMember(member);
        source.setStatus("ACTIVE");
        source.setAutoRenew(true);
        source.setStartDate(LocalDate.of(2026, 10, 1));
        source.setEndDate(LocalDate.of(2026, 11, 23));
        plan = new MembershipPlan();
        plan.setId(PLAN_ID);
        plan.setPrice(new BigDecimal("125000.00"));
        plan.setDurationMonths(2);
        intent = new MembershipRenewalIntent();
        intent.setId(INTENT_ID);
        intent.setSourceMembership(source);
        intent.setTargetPlan(plan);
        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setAutoRenewOptInSnapshot(true);
        intent.setValidFrom(LocalDate.of(2026, 11, 20).atStartOfDay());
        intent.setValidUntil(LocalDate.of(2026, 11, 23).atStartOfDay());
        intent.setSourceEndDateSnapshot(LocalDate.of(2026, 11, 23));
        intent.setQuotedAmount(new BigDecimal("125000.00"));
        intent.setPlanPriceSnapshot(new BigDecimal("125000.00"));
        intent.setQuoteCapturedAt(LocalDateTime.of(2026, 11, 20, 11, 55));
        intent.setPlanNameSnapshot("Captured Plan");
        intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(BigDecimal.ZERO);
        intent.setPtDiscountPercentSnapshot(BigDecimal.ZERO);
        intent.setAllowPtPlanSnapshot(false);
        intent.setAllowOneOnOneSnapshot(true);
        service = new MembershipRenewalPaymentAttemptService(intents, payments, users,
                Clock.fixed(NOW, VN));

        when(intents.findById(INTENT_ID)).thenReturn(Optional.of(intent));
        when(intents.findByIdForUpdate(INTENT_ID)).thenReturn(Optional.of(intent));
        when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of());
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void onlineAttemptUsesFrozenAmountAndFifteenMinuteVietnamDeadline() {
        Payment attempt = service.createAttempt(INTENT_ID, PaymentMethod.ONLINE);

        assertAttempt(attempt, PaymentMethod.ONLINE);
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), attempt.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 15), attempt.getAttemptValidUntil());
        assertEquals(new BigDecimal("125000.00"), attempt.getAmount());
        assertNotNull(attempt.getTransactionCode());
    }

    @Test
    void onlineDeadlineIsCappedAtIntentDeadline() {
        intent.setValidUntil(LocalDateTime.of(2026, 11, 20, 12, 5));

        Payment attempt = service.createAttempt(INTENT_ID, PaymentMethod.ONLINE);

        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 5), attempt.getAttemptValidUntil());
    }

    @Test
    void counterDeadlineEqualsIntentDeadline() {
        Payment attempt = service.createAttempt(INTENT_ID, PaymentMethod.COUNTER);

        assertAttempt(attempt, PaymentMethod.COUNTER);
        assertEquals(intent.getValidUntil(), attempt.getAttemptValidUntil());
    }

    @Test
    void planChangesDoNotChangeCapturedAttemptAmountOrDuration() {
        plan.setPrice(new BigDecimal("999999.00"));
        plan.setDurationMonths(12);

        Payment attempt = service.createAttempt(INTENT_ID, PaymentMethod.COUNTER);

        assertEquals(new BigDecimal("125000.00"), attempt.getAmount());
        assertEquals(2, intent.getDurationMonthsSnapshot());
    }

    @Test
    void exactIntentDeadlineIsRejected() {
        intent.setValidUntil(LocalDateTime.of(2026, 11, 20, 12, 0));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createAttempt(INTENT_ID, PaymentMethod.COUNTER));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void failedAttemptAllowsSequentialRetryUnderSameIntent() {
        Payment failed = priorAttempt(PaymentStatus.CANCELLED, RenewalAttemptCloseReason.PROVIDER_FAILED,
                LocalDateTime.of(2026, 11, 20, 11, 45));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(failed));

        Payment retry = service.createAttempt(INTENT_ID, PaymentMethod.COUNTER);

        assertEquals(INTENT_ID, retry.getRenewalIntent().getId());
        assertEquals(new BigDecimal("125000.00"), retry.getAmount());
        verify(intents, never()).save(any(MembershipRenewalIntent.class));
    }

    @Test
    void memberCancelledAttemptCanBeRetriedWhileIntentRemainsOpen() {
        Payment cancelled = priorAttempt(PaymentStatus.CANCELLED, RenewalAttemptCloseReason.MEMBER_CANCELLED,
                LocalDateTime.of(2026, 11, 20, 12, 15));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(cancelled));

        Payment retry = service.createAttempt(INTENT_ID, PaymentMethod.ONLINE);

        assertEquals(PaymentStatus.PENDING, retry.getStatus());
        assertEquals(INTENT_ID, retry.getRenewalIntent().getId());
        assertEquals(RenewalAttemptCloseReason.MEMBER_CANCELLED, cancelled.getRenewalAttemptCloseReason());
    }

    @Test
    void elapsedPendingAttemptIsClosedWithStructuredReasonBeforeRetry() {
        Payment expired = priorAttempt(PaymentStatus.PENDING, null,
                LocalDateTime.of(2026, 11, 20, 11, 59));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(expired));

        Payment retry = service.createAttempt(INTENT_ID, PaymentMethod.ONLINE);

        assertEquals(PaymentStatus.CANCELLED, expired.getStatus());
        assertEquals(RenewalAttemptCloseReason.ATTEMPT_EXPIRED, expired.getRenewalAttemptCloseReason());
        assertEquals(PaymentStatus.PENDING, retry.getStatus());
    }

    @Test
    void livePendingAttemptBlocksSecondAttempt() {
        Payment pending = priorAttempt(PaymentStatus.PENDING, null,
                LocalDateTime.of(2026, 11, 20, 12, 10));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(pending));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createAttempt(INTENT_ID, PaymentMethod.COUNTER));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void closedIntentAndCollectedEvidenceCannotCreateAnotherAttempt() {
        intent.setIntentStatus(RenewalIntentStatus.CANCELLED);
        ResponseStatusException closed = assertThrows(ResponseStatusException.class,
                () -> service.createAttempt(INTENT_ID, PaymentMethod.ONLINE));
        assertEquals(HttpStatus.CONFLICT, closed.getStatusCode());

        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        Payment collected = priorAttempt(PaymentStatus.REFUND_REQUIRED, null,
                LocalDateTime.of(2026, 11, 20, 11, 45));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(collected));
        assertThrows(ResponseStatusException.class, () -> service.createAttempt(INTENT_ID, PaymentMethod.ONLINE));
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void committedCollectionEvidenceBlocksAnotherAttemptEvenBeforeSettlementStatusChanges() {
        Payment collected = priorAttempt(PaymentStatus.PENDING, null,
                LocalDateTime.of(2026, 11, 20, 12, 15));
        collected.setRenewalCollectionVerifiedAt(LocalDateTime.of(2026, 11, 20, 12, 1));
        when(payments.findByRenewalIntentIdForUpdate(INTENT_ID)).thenReturn(List.of(collected));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createAttempt(INTENT_ID, PaymentMethod.ONLINE));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void providerFailureUsesCancelledStatusAndProviderFailureReason() {
        Payment failed = priorAttempt(PaymentStatus.PENDING, null,
                LocalDateTime.of(2026, 11, 20, 12, 15));
        when(payments.findPaymentLockRow(501)).thenReturn(Optional.of(
                new PendingAutoRenewalLockRow(501, MEMBER_ID, null, PaymentPurpose.MEMBERSHIP)));
        when(payments.findById(501)).thenReturn(Optional.of(failed));
        when(payments.findByIdForUpdate(501)).thenReturn(Optional.of(failed));
        when(payments.saveAndFlush(failed)).thenReturn(failed);

        Payment result = service.recordProviderFailure(501);

        assertEquals(PaymentStatus.CANCELLED, result.getStatus());
        assertEquals(RenewalAttemptCloseReason.PROVIDER_FAILED, result.getRenewalAttemptCloseReason());
    }

    @Test
    void explicitMemberCancellationUsesStructuredReasonAndRejectsStaffOwnedCancellation() {
        Payment pending = priorAttempt(PaymentStatus.PENDING, null,
                LocalDateTime.of(2026, 11, 20, 12, 15));
        when(payments.findPaymentLockRow(501)).thenReturn(Optional.of(
                new PendingAutoRenewalLockRow(501, MEMBER_ID, null, PaymentPurpose.MEMBERSHIP)));
        when(payments.findById(501)).thenReturn(Optional.of(pending));
        when(payments.findByIdForUpdate(501)).thenReturn(Optional.of(pending));
        when(users.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(payments.saveAndFlush(pending)).thenReturn(pending);

        Payment cancelled = service.cancelAttempt(501, MEMBER_ID);

        assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(RenewalAttemptCloseReason.MEMBER_CANCELLED, cancelled.getRenewalAttemptCloseReason());
        assertThrows(ResponseStatusException.class, () -> service.cancelAttempt(501, MEMBER_ID + 1));
    }

    private Payment priorAttempt(PaymentStatus status, RenewalAttemptCloseReason reason, LocalDateTime deadline) {
        Payment payment = new Payment();
        payment.setId(501);
        payment.setMember(member);
        payment.setAmount(intent.getQuotedAmount());
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(PLAN_ID);
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.of(2026, 11, 20, 11, 30));
        payment.setAttemptValidUntil(deadline);
        payment.setRenewalAttemptCloseReason(reason);
        payment.setRenewalIntent(intent);
        payment.setTransactionCode("prior-attempt");
        return payment;
    }

    private void assertAttempt(Payment attempt, PaymentMethod method) {
        assertEquals(member, attempt.getMember());
        assertEquals(method, attempt.getMethod());
        assertEquals(PaymentPurpose.MEMBERSHIP, attempt.getPurpose());
        assertEquals(PLAN_ID, attempt.getReferenceId());
        assertEquals(intent, attempt.getRenewalIntent());
        assertEquals(PaymentStatus.PENDING, attempt.getStatus());
        assertEquals(null, attempt.getAutoRenew());
    }
}
