package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberRenewalApiServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Integer MEMBER_ID = 7;
    private static final Integer INTENT_ID = 20;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    @Mock MembershipRenewalIntentRepository intents;
    @Mock MembershipRepository memberships;
    @Mock PaymentRepository payments;
    @Mock MembershipRenewalPaymentAttemptService attemptService;
    @Mock PaymentService paymentService;
    @Mock PaymentGateway gateway;
    private MemberRenewalApiService service;
    private Jwt jwt;
    private User member;
    private MembershipRenewalIntent intent;

    @BeforeEach
    void setUp() {
        service = new MemberRenewalApiService(intents, memberships, payments, attemptService, paymentService,
                gateway, Clock.fixed(NOW.atZone(VN).toInstant(), VN));
        jwt = Jwt.withTokenValue("test").header("alg", "none").subject(String.valueOf(MEMBER_ID)).build();
        member = new User();
        member.setId(MEMBER_ID);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        Membership source = new Membership();
        source.setId(10);
        source.setMember(member);
        source.setStatus("ACTIVE");
        source.setAutoRenew(true);
        source.setStartDate(LocalDate.of(2026, 10, 1));
        source.setEndDate(LocalDate.of(2026, 11, 23));
        intent = new MembershipRenewalIntent();
        intent.setId(INTENT_ID);
        intent.setSourceMembership(source);
        intent.setAutoRenewOptInSnapshot(true);
        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(NOW.minusDays(2));
        intent.setValidFrom(NOW.minusDays(3));
        intent.setValidUntil(NOW.plusDays(2));
        intent.setSourceEndDateSnapshot(LocalDate.of(2026, 11, 23));
        intent.setPlanNameSnapshot("Frozen Plan");
        intent.setQuotedAmount(new BigDecimal("120000.00"));
        intent.setDurationMonthsSnapshot(3);
        intent.setSuccessorStartDate(LocalDate.of(2026, 11, 23));
        intent.setSuccessorEndDate(LocalDate.of(2027, 2, 23));
        when(paymentService.authenticatePaymentActor(jwt)).thenReturn(member);
        when(intents.findWithSourceMembershipMemberById(INTENT_ID)).thenReturn(Optional.of(intent));
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID)).thenReturn(List.of());
        when(memberships.findByRenewalIntent_Id(INTENT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void memberCanReadOwnImmutableQuoteAndAttempts() {
        var response = service.getMine(INTENT_ID, jwt);

        assertEquals(INTENT_ID, response.intentId());
        assertEquals("Frozen Plan", response.planName());
        assertEquals(new BigDecimal("120000.00"), response.quotedAmount());
        assertEquals("OPEN", response.status());
    }

    @Test
    void crossMemberIntentIsHiddenAndCannotBePaid() {
        User otherMember = new User();
        otherMember.setId(999);
        intent.getSourceMembership().setMember(otherMember);

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.getMine(INTENT_ID, jwt));
        assertEquals("Renewal Intent not found", error.getMessage());
        assertThrows(ResourceNotFoundException.class,
                () -> service.startOnlineAttempt(INTENT_ID, jwt, "127.0.0.1"));
        verify(attemptService, never()).createAttempt(INTENT_ID, PaymentMethod.ONLINE);
    }

    @Test
    void onlyMemberRoleCanAccessMemberRenewalApi() {
        member.setRole("RECEPTIONIST");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.listMine(jwt));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void receptionistCanReadSelectedMembersRenewalQuote() {
        member.setRole("RECEPTIONIST");
        when(paymentService.authorizeCounterActor(jwt)).thenReturn(member);
        when(intents.findAllBySourceMembership_Member_IdOrderByCreatedAtDesc(MEMBER_ID))
                .thenReturn(List.of(intent));
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID)).thenReturn(List.of());

        var result = service.listForReceptionist(MEMBER_ID, jwt);

        assertEquals(1, result.size());
        assertEquals(INTENT_ID, result.getFirst().intentId());
        assertEquals(new BigDecimal("120000.00"), result.getFirst().quotedAmount());
        assertEquals("OPEN", result.getFirst().status());
        verify(paymentService).authorizeCounterActor(jwt);
    }

    @Test
    void receptionistCanCreateOnlyCounterAttemptForIntentOwnedBySelectedMember() {
        member.setRole("RECEPTIONIST");
        when(paymentService.authorizeCounterActor(jwt)).thenReturn(member);
        Payment counter = pendingOnlinePayment();
        counter.setMethod(PaymentMethod.COUNTER);
        when(attemptService.createAttempt(INTENT_ID, PaymentMethod.COUNTER)).thenReturn(counter);
        when(intents.findById(INTENT_ID)).thenReturn(Optional.of(intent));
        when(intents.findWithSourceMembershipMemberById(INTENT_ID)).thenReturn(Optional.of(intent));
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID)).thenReturn(List.of(counter));

        var action = service.startCounterAttemptForReceptionist(MEMBER_ID, INTENT_ID, jwt);

        assertEquals(INTENT_ID, action.intent().intentId());
        assertEquals(counter.getId(), action.attempt().paymentId());
        assertEquals("COUNTER", action.attempt().method());
        assertEquals(new BigDecimal("120000.00"), action.attempt().amount());
        assertEquals(null, action.checkoutUrl());
        verify(attemptService).createAttempt(INTENT_ID, PaymentMethod.COUNTER);
        verify(paymentService).authorizeCounterActor(jwt);
    }

    @Test
    void receptionistCannotCreateCounterAttemptForAnotherMembersIntent() {
        member.setRole("RECEPTIONIST");
        when(paymentService.authorizeCounterActor(jwt)).thenReturn(member);
        User otherMember = new User();
        otherMember.setId(999);
        intent.getSourceMembership().setMember(otherMember);
        when(intents.findById(INTENT_ID)).thenReturn(Optional.of(intent));

        assertThrows(ResourceNotFoundException.class,
                () -> service.startCounterAttemptForReceptionist(MEMBER_ID, INTENT_ID, jwt));
        verify(attemptService, never()).createAttempt(INTENT_ID, PaymentMethod.COUNTER);
    }

    @Test
    void createsAttemptFromServiceAndReturnsExistingGatewayCheckoutAction() {
        Payment payment = pendingOnlinePayment();
        when(attemptService.createAttempt(INTENT_ID, PaymentMethod.ONLINE)).thenReturn(payment);
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID))
                .thenReturn(List.of(), List.of(payment));
        when(gateway.createPaymentUrl("TXN-1", new BigDecimal("120000.00"), "127.0.0.1"))
                .thenReturn("https://sandbox.example/checkout");

        var action = service.startOnlineAttempt(INTENT_ID, jwt, "127.0.0.1");

        assertEquals("https://sandbox.example/checkout", action.checkoutUrl());
        assertEquals(payment.getId(), action.attempt().paymentId());
        assertEquals(new BigDecimal("120000.00"), action.attempt().amount());
        verify(attemptService).createAttempt(INTENT_ID, PaymentMethod.ONLINE);
    }

    @Test
    void repeatedStartResumesTheSameLiveAttemptInsteadOfCreatingAnother() {
        Payment existing = pendingOnlinePayment();
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID))
                .thenReturn(List.of(existing));
        when(gateway.createPaymentUrl("TXN-1", new BigDecimal("120000.00"), "127.0.0.1"))
                .thenReturn("https://sandbox.example/checkout");

        var action = service.startOnlineAttempt(INTENT_ID, jwt, "127.0.0.1");

        assertEquals(existing.getId(), action.attempt().paymentId());
        verify(attemptService, never()).createAttempt(INTENT_ID, PaymentMethod.ONLINE);
    }

    @Test
    void unresolvedCollectionNeverResumesCheckoutOrCreatesRetry() {
        Payment collected = pendingOnlinePayment();
        collected.setRenewalCollectionVerifiedAt(NOW.minusMinutes(1));
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID)).thenReturn(List.of(collected));
        when(attemptService.createAttempt(INTENT_ID, PaymentMethod.ONLINE))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "manual reconciliation"));

        assertThrows(ResponseStatusException.class,
                () -> service.startOnlineAttempt(INTENT_ID, jwt, "127.0.0.1"));
        verify(gateway, never()).createPaymentUrl(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void optOutSourceDoesNotResumeAnExistingPendingCheckout() {
        intent.getSourceMembership().setAutoRenew(false);
        Payment existing = pendingOnlinePayment();
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID))
                .thenReturn(List.of(existing));
        when(attemptService.createAttempt(INTENT_ID, PaymentMethod.ONLINE))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "intent/source is no longer eligible"));

        assertThrows(ResponseStatusException.class,
                () -> service.startOnlineAttempt(INTENT_ID, jwt, "127.0.0.1"));
        verify(gateway, never()).createPaymentUrl(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settledIntentIsReadAsResultAndDoesNotExposeRefundAsCompleted() {
        intent.setIntentStatus(RenewalIntentStatus.SETTLED);
        Payment accepted = pendingOnlinePayment();
        accepted.setStatus(PaymentStatus.SUCCESS);
        accepted.setRenewalSettlementAcceptedAt(NOW);
        when(payments.findByRenewalIntent_IdOrderByCreatedAtAsc(INTENT_ID)).thenReturn(List.of(accepted));
        when(memberships.findByRenewalIntent_Id(INTENT_ID)).thenReturn(Optional.of(new Membership() {{ setId(77); }}));

        var response = service.getMine(INTENT_ID, jwt);

        assertEquals("SETTLED", response.latestResult());
        assertEquals(77, response.successorMembershipId());
        assertEquals("SUCCESS", response.attempts().getFirst().status());
        assertFalse(response.attempts().getFirst().manualReconciliationRequired());
        assertTrue(response.attempts().getFirst().settlementAccepted());
    }

    private Payment pendingOnlinePayment() {
        Payment payment = new Payment();
        payment.setId(41);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("120000.00"));
        payment.setTransactionCode("TXN-1");
        payment.setCreatedAt(NOW.minusMinutes(2));
        payment.setAttemptValidUntil(NOW.plusMinutes(13));
        payment.setRenewalIntent(intent);
        return payment;
    }
}
