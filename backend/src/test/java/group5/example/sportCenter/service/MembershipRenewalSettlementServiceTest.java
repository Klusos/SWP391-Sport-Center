package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MembershipRenewalSettlementServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Integer MEMBER_ID = 101;
    private static final Integer INTENT_ID = 301;
    private static final Integer PAYMENT_ID = 501;
    private static final Integer SOURCE_ID = 401;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime ATTEMPT_DEADLINE = NOW.plusMinutes(15);
    private static final LocalDate SOURCE_END = LocalDate.of(2026, 11, 23);

    @Mock UserRepository users;
    @Mock MembershipRenewalIntentRepository intents;
    @Mock PaymentRepository payments;
    @Mock MembershipRepository memberships;
    @Mock EntityManager entityManager;
    @Mock NotificationService notifications;
    @Mock AuditLogService auditLogs;

    private MembershipRenewalSettlementService service;
    private User member;
    private Membership source;
    private MembershipPlan plan;
    private MembershipRenewalIntent intent;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new MembershipRenewalSettlementService(users, intents, payments, memberships,
                Clock.fixed(NOW.atZone(VN).toInstant(), VN), entityManager, notifications, auditLogs);
        member = new User(); member.setId(MEMBER_ID); member.setRole("MEMBER"); member.setStatus("ACTIVE");
        plan = new MembershipPlan(); plan.setId(22); plan.setName("Frozen plan");
        source = new Membership(); source.setId(SOURCE_ID); source.setMember(member); source.setPlan(plan);
        source.setStatus("ACTIVE"); source.setAutoRenew(true);
        source.setStartDate(SOURCE_END.minusMonths(1)); source.setEndDate(SOURCE_END);
        intent = new MembershipRenewalIntent(); intent.setId(INTENT_ID); intent.setSourceMembership(source);
        intent.setTargetPlan(plan); intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setAutoRenewOptInSnapshot(true); intent.setSourceEndDateSnapshot(SOURCE_END);
        intent.setSuccessorStartDate(SOURCE_END); intent.setSuccessorEndDate(SOURCE_END.plusMonths(2));
        intent.setValidFrom(NOW.minusHours(10)); intent.setValidUntil(SOURCE_END.atStartOfDay());
        intent.setQuoteCapturedAt(NOW.minusDays(2)); intent.setPlanNameSnapshot("Purchased snapshot");
        intent.setPlanDescriptionSnapshot("Frozen description"); intent.setPlanPriceSnapshot(new BigDecimal("800000.00"));
        intent.setQuotedAmount(new BigDecimal("800000.00")); intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(new BigDecimal("12.50"));
        intent.setPtDiscountPercentSnapshot(new BigDecimal("7.00"));
        intent.setAllowPtPlanSnapshot(true); intent.setAllowOneOnOneSnapshot(false);
        payment = new Payment(); payment.setId(PAYMENT_ID); payment.setMember(member);
        payment.setAmount(new BigDecimal("800000.00")); payment.setMethod(PaymentMethod.ONLINE);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP); payment.setReferenceId(plan.getId());
        payment.setStatus(PaymentStatus.PENDING); payment.setCreatedAt(NOW);
        payment.setAttemptValidUntil(NOW.plusMinutes(15)); payment.setRenewalIntent(intent);
        payment.setTransactionCode("TXN-RENEW-TEST");
        lenient().when(payments.findPaymentLockRow(PAYMENT_ID))
                .thenReturn(Optional.of(new PendingAutoRenewalLockRow(PAYMENT_ID, MEMBER_ID, null, PaymentPurpose.MEMBERSHIP)));
        lenient().when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        lenient().when(users.findByIdForBookingUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        lenient().when(intents.findByIdForUpdate(INTENT_ID)).thenReturn(Optional.of(intent));
        lenient().when(payments.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        lenient().when(memberships.findByIdForRenewalUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
        lenient().when(memberships.findByRenewalIntent_Id(INTENT_ID)).thenReturn(Optional.empty());
        lenient().when(payments.existsByRenewalIntent_IdAndRenewalSettlementAcceptedAtIsNotNull(INTENT_ID)).thenReturn(false);
        lenient().when(payments.existsConflictingPendingMembershipOrUpgrade(MEMBER_ID, PAYMENT_ID)).thenReturn(false);
        lenient().when(memberships.findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                MEMBER_ID, "ACTIVE", NOW.toLocalDate(), NOW.toLocalDate())).thenReturn(List.of(source));
        lenient().when(memberships.existsByMemberIdAndStatusAndStartDateGreaterThan(
                MEMBER_ID, "ACTIVE", NOW.toLocalDate())).thenReturn(false);
        lenient().when(payments.saveAndFlush(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(intents.saveAndFlush(any(MembershipRenewalIntent.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(memberships.saveAndFlush(any(Membership.class))).thenAnswer(call -> {
            Membership saved = call.getArgument(0); saved.setId(601); return saved;
        });
    }

    @Test
    void onlineSettlementCreatesSingleFutureSuccessorFromFrozenSnapshotAndPaymentAmount() {
        plan.setName("Edited plan");
        plan.setPrice(new BigDecimal("950000.00"));
        plan.setDurationMonths(99);
        plan.setCourseDiscountPercent(BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("INACTIVE");
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                payment.getAmount(), "VND", "12345678", NOW, "VNPAY_SIGNATURE_VERIFIED", "provider-txn-123");

        RenewalSettlementResult result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ACCEPTED, result.outcome());
        Membership successor = result.successor();
        assertEquals(SOURCE_END, successor.getStartDate());
        assertEquals(SOURCE_END.plusMonths(2), successor.getEndDate());
        assertEquals("ACTIVE", successor.getStatus());
        assertEquals("RENEWAL", successor.getBenefitSnapshotSource());
        assertEquals("Purchased snapshot", successor.getPlanNameSnapshot());
        assertEquals(new BigDecimal("800000.00"), successor.getPlanPriceSnapshot());
        assertEquals(new BigDecimal("12.50"), successor.getCourseDiscountPercentSnapshot());
        assertEquals(new BigDecimal("7.00"), successor.getPtDiscountPercentSnapshot());
        assertEquals("VERIFIED", successor.getPaidValueBasisStatus());
        assertEquals(new BigDecimal("800000.00"), successor.getPaidValueBasisAmount());
        assertEquals("RENEWAL_PAYMENT", successor.getPaidValueBasisSource());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(NOW, payment.getPaidAt());
        assertEquals(NOW, payment.getRenewalSettlementAcceptedAt());
        assertEquals(RenewalIntentStatus.SETTLED, intent.getIntentStatus());
        assertSame(payment, intent.getSettledPayment());
        assertEquals("ACTIVE", source.getStatus()); // source is not changed by Renewal settlement
        assertEquals("12345678", payment.getRenewalProviderTransactionReference());
        assertEquals(0, payment.getAmount().compareTo(payment.getRenewalVerifiedCollectionAmount()));
        assertEquals("VND", payment.getRenewalVerifiedCollectionCurrency());
        InOrder order = inOrder(users, intents, payments, memberships);
        order.verify(users).findByIdForBookingUpdate(MEMBER_ID);
        order.verify(intents).findByIdForUpdate(INTENT_ID);
        order.verify(payments).findByIdForUpdate(PAYMENT_ID);
        order.verify(memberships).findByIdForRenewalUpdate(SOURCE_ID);
        order.verify(payments).saveAndFlush(payment);
        order.verify(intents).saveAndFlush(intent);
        order.verify(payments).saveAndFlush(payment);
        order.verify(memberships).saveAndFlush(any(Membership.class));
    }

    @Test
    void counterSettlementRequiresServerConfirmationTimeAndUsesThatTime() {
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setCreatedAt(NOW.minusMinutes(5));
        payment.setAttemptValidUntil(intent.getValidUntil());
        var evidence = RenewalSettlementEvidence.verifiedCounterCollection(INTENT_ID, PAYMENT_ID,
                NOW.minusMinutes(1), "RECEPTIONIST_SERVER_CONFIRMATION", "receipt-77");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ACCEPTED, result.outcome());
        assertEquals(NOW.minusMinutes(1), payment.getPaidAt());
    }

    @Test
    void exactAttemptDeadlineIsLateAndRequiresReconciliation() {
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, ATTEMPT_DEADLINE, "VNPAY_VERIFIED", "late-attempt");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.PAYMENT_LATE, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertEquals(RenewalAttemptCloseReason.LATE_PROVIDER_PAYMENT, payment.getRenewalAttemptCloseReason());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void exactIntentDeadlineIsLateEvenWhenAttemptDeadlineIsLater() {
        payment.setCreatedAt(SOURCE_END.atStartOfDay().minusMinutes(15));
        payment.setAttemptValidUntil(SOURCE_END.atStartOfDay());
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, SOURCE_END.atStartOfDay(), "VNPAY_VERIFIED", "late-intent");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.PAYMENT_LATE, result.outcome());
        assertEquals(RenewalAttemptCloseReason.LATE_PROVIDER_PAYMENT, payment.getRenewalAttemptCloseReason());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void delayedCallbackAcceptsStrictlyTimelyVerifiedPaidAtAfterAttemptExpiry() {
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.ATTEMPT_EXPIRED);
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "delayed-callback");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ACCEPTED, result.outcome());
        assertEquals(601, result.successor().getId());
    }

    @Test
    void missingOrMalformedPaidAtPreservesCollectionWithoutInventingTime() {
        var evidence = RenewalSettlementEvidence.verifiedCollectionWithTimestampIssue(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, RenewalSettlementEvidence.PaidAtEvidence.MALFORMED,
                "VNPAY_VERIFIED", "bad-time", "provider timestamp malformed");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertNull(payment.getPaidAt());
        assertNull(payment.getRenewalSettlementAcceptedAt());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void recordedMemberCancellationBlocksEvenTimelyCollection() {
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "cancelled-then-paid");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ATTEMPT_CANCELLED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertEquals(RenewalAttemptCloseReason.MEMBER_CANCELLED, payment.getRenewalAttemptCloseReason());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void closedIntentBlocksTimelyCollectionAndKeepsEvidence() {
        intent.setIntentStatus(RenewalIntentStatus.CANCELLED);
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setAttemptValidUntil(intent.getValidUntil());
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
        var evidence = RenewalSettlementEvidence.verifiedCounterCollection(INTENT_ID, PAYMENT_ID,
                NOW, "COUNTER_SERVER", "receipt-cancelled");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.INTENT_CLOSED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void differentAttemptAfterWinnerRequiresReconciliationWithoutSuccessor() {
        Payment winner = new Payment(); winner.setId(499);
        intent.setIntentStatus(RenewalIntentStatus.SETTLED); intent.setSettledPayment(winner);
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "duplicate-collected");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertEquals(RenewalAttemptCloseReason.DUPLICATE_SETTLEMENT, payment.getRenewalAttemptCloseReason());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void repeatedWinningPaymentReturnsExistingSuccessorWithoutApplyingAgain() {
        Membership successor = new Membership(); successor.setId(601);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(NOW);
        payment.setRenewalSettlementAcceptedAt(NOW);
        intent.setIntentStatus(RenewalIntentStatus.SETTLED);
        intent.setSettledPayment(payment);
        when(memberships.findByRenewalIntent_Id(INTENT_ID)).thenReturn(Optional.of(successor));
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "same-winner");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.ALREADY_ACCEPTED, result.outcome());
        assertSame(successor, result.successor());
        verify(payments, never()).saveAndFlush(any(Payment.class));
        verify(intents, never()).saveAndFlush(any(MembershipRenewalIntent.class));
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void amountMismatchIsReconciledAndDoesNotCreateSuccessor() {
        payment.setAmount(new BigDecimal("799999.99"));
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "amount-mismatch");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertEquals(NOW, payment.getPaidAt());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void existingSuccessorPreventsSecondMembershipAndPreservesCollectionForReview() {
        Membership priorSuccessor = new Membership(); priorSuccessor.setId(600);
        lenient().when(memberships.findByRenewalIntent_Id(INTENT_ID)).thenReturn(Optional.of(priorSuccessor));
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "unexpected-successor");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        assertEquals(NOW, payment.getPaidAt());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void mismatchedSpecifiedIntentCannotApplyPaymentToAnotherIntent() {
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID + 1, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "wrong-intent");

        var result = service.settle(evidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        assertEquals(PaymentStatus.REFUND_REQUIRED, payment.getStatus());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void unverifiedOrNonCollectedEvidenceNeverCreatesEntitlement() {
        var invalid = RenewalSettlementEvidence.invalid(INTENT_ID, PAYMENT_ID, PaymentMethod.ONLINE, "bad signature");
        assertEquals(RenewalSettlementOutcome.INVALID_EVIDENCE, service.settle(invalid).outcome());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        var notCollected = RenewalSettlementEvidence.verifiedNonCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, "VNPAY_VERIFIED", "declined");
        assertEquals(RenewalSettlementOutcome.PAYMENT_NOT_COLLECTED, service.settle(notCollected).outcome());
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }

    @Test
    void legacyPaymentIsNotSettledThroughIntentEngine() {
        payment.setRenewalIntent(null);
        var evidence = RenewalSettlementEvidence.verifiedOnlineCollection(INTENT_ID, PAYMENT_ID,
                PaymentMethod.ONLINE, NOW, "VNPAY_VERIFIED", "legacy-payment");

        assertEquals(RenewalSettlementOutcome.LEGACY_PAYMENT_REJECTED, service.settle(evidence).outcome());
        verify(users, never()).findByIdForBookingUpdate(any());
        verify(memberships, never()).saveAndFlush(any(Membership.class));
    }
}
