package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentCloseReason;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MembershipRenewalLifecycleServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    @Mock MembershipRepository memberships;
    @Mock MembershipRenewalIntentRepository intents;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock MembershipRenewalIntentService intentService;
    @Mock NotificationService notifications;
    private MembershipRenewalLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new MembershipRenewalLifecycleService(memberships, intents, payments, users, intentService,
                notifications, Clock.fixed(NOW.atZone(VN).toInstant(), VN));
    }

    @Test
    void catchUpScansOnlyTheApprovedThreeDayWindowAndCreatesNoAttemptItself() {
        when(memberships.findRenewalIntentCandidates(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 24)))
                .thenReturn(List.of(10, 11));
        when(intentService.createForScheduler(10)).thenReturn(new MembershipRenewalIntentService.CreationResult(
                MembershipRenewalIntentService.Outcome.CREATED, new MembershipRenewalIntent()));
        when(intentService.createForScheduler(11)).thenReturn(new MembershipRenewalIntentService.CreationResult(
                MembershipRenewalIntentService.Outcome.INACTIVE_PLAN, null));
        when(intents.findExpiredOpenIds(any())).thenReturn(List.of());
        when(intents.findOpenIdsForReminder(any(), any())).thenReturn(List.of());

        var result = service.runDueLifecycle();

        assertEquals(1, result.intentsCreated());
        assertEquals(1, result.inactivePlan());
        verify(intentService).createForScheduler(10);
        verify(intentService).createForScheduler(11);
        verifyNoInteractions(payments);
    }

    @Test
    void openIntentInsideItsFinalDayGetsOneStableDeadlineReminder() {
        User member = member();
        MembershipRenewalIntent intent = intent(source(member), RenewalIntentStatus.OPEN);
        intent.setValidUntil(NOW.plusHours(12));
        when(memberships.findRenewalIntentCandidates(any(), any())).thenReturn(List.of());
        when(intents.findExpiredOpenIds(any())).thenReturn(List.of());
        when(intents.findOpenIdsForReminder(NOW, NOW.plusDays(1))).thenReturn(List.of(20));
        when(intents.findWithSourceMembershipMemberById(20)).thenReturn(Optional.of(intent));

        service.runDueLifecycle();

        verify(notifications).createForUserOnce(eq(7), eq("Renewal deadline approaching"), any());
    }

    @Test
    void exactIntentDeadlineExpiresOpenIntentAndCancelsUncollectedAttempt() {
        User member = member();
        Membership source = source(member);
        MembershipRenewalIntent intent = intent(source, RenewalIntentStatus.OPEN);
        intent.setValidUntil(NOW);
        Payment attempt = new Payment();
        attempt.setStatus(PaymentStatus.PENDING);
        when(intents.findById(20)).thenReturn(Optional.of(intent));
        when(users.findByIdForBookingUpdate(7)).thenReturn(Optional.of(member));
        when(intents.findByIdForUpdate(20)).thenReturn(Optional.of(intent));
        when(payments.findByRenewalIntentIdForUpdate(20)).thenReturn(List.of(attempt));

        assertTrue(service.expireIntent(20));
        assertEquals(RenewalIntentStatus.EXPIRED, intent.getIntentStatus());
        assertEquals(RenewalIntentCloseReason.DEADLINE_REACHED, intent.getClosedReason());
        assertEquals(PaymentStatus.CANCELLED, attempt.getStatus());
        assertEquals(RenewalAttemptCloseReason.INTENT_EXPIRED, attempt.getRenewalAttemptCloseReason());
        verify(notifications).createForUserOnce(eq(7), eq("Renewal expired"), any());
    }

    @Test
    void optOutPreservesCollectionEvidenceAndRequiresManualReconciliation() {
        User member = member();
        Membership source = source(member);
        MembershipRenewalIntent intent = intent(source, RenewalIntentStatus.OPEN);
        Payment attempt = new Payment();
        attempt.setId(30);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setRenewalCollectionVerifiedAt(NOW.minusMinutes(1));
        attempt.setRenewalProviderTransactionReference("provider-123");
        when(memberships.findById(10)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(7)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(10)).thenReturn(Optional.of(intent));
        when(payments.findByRenewalIntentIdForUpdate(20)).thenReturn(List.of(attempt));

        service.closeForAutoRenewDisabled(10);

        assertEquals(RenewalIntentStatus.CANCELLED, intent.getIntentStatus());
        assertEquals(RenewalIntentCloseReason.AUTO_RENEW_DISABLED, intent.getClosedReason());
        assertEquals(PaymentStatus.REFUND_REQUIRED, attempt.getStatus());
        assertEquals("provider-123", attempt.getRenewalProviderTransactionReference());
        assertTrue(attempt.hasRecordedRenewalCollection());
        verify(notifications).renewalReconciliationRequired(30, 7);
    }

    @Test
    void optOutDoesNotReverseAlreadyAcceptedIntent() {
        User member = member();
        Membership source = source(member);
        MembershipRenewalIntent intent = intent(source, RenewalIntentStatus.SETTLED);
        when(memberships.findById(10)).thenReturn(Optional.of(source));
        when(users.findByIdForBookingUpdate(7)).thenReturn(Optional.of(member));
        when(intents.findBySourceMembershipIdForUpdate(10)).thenReturn(Optional.of(intent));

        service.closeForAutoRenewDisabled(10);

        assertEquals(RenewalIntentStatus.SETTLED, intent.getIntentStatus());
        verifyNoInteractions(payments);
        verifyNoInteractions(notifications);
    }

    private User member() {
        User member = new User();
        member.setId(7);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        return member;
    }

    private Membership source(User member) {
        Membership source = new Membership();
        source.setId(10);
        source.setMember(member);
        source.setStatus("ACTIVE");
        source.setAutoRenew(true);
        return source;
    }

    private MembershipRenewalIntent intent(Membership source, RenewalIntentStatus status) {
        MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setId(20);
        intent.setSourceMembership(source);
        intent.setIntentStatus(status);
        return intent;
    }
}
