package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.RenewalPaymentRoute;

@ExtendWith(MockitoExtension.class)
class RenewalPaymentAdapterServiceTest {
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 21, 10, 15);
    private static final RenewalPaymentRoute ONLINE = new RenewalPaymentRoute(41, 12, 7,
            PaymentMethod.ONLINE, PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING,
            new BigDecimal("250000.00"), "TXN-REN-ABC123");
    private static final RenewalPaymentRoute COUNTER = new RenewalPaymentRoute(42, 12, 7,
            PaymentMethod.COUNTER, PaymentPurpose.MEMBERSHIP, PaymentStatus.PENDING,
            new BigDecimal("250000.00"), "TXN-REN-DEF456");

    @Mock private PaymentGateway gateway;
    @Mock private PaymentRepository payments;
    @Mock private MembershipRenewalCollectionService collections;
    @Mock private MembershipRenewalPaymentAttemptService attempts;
    @Mock private PaymentService paymentService;
    private RenewalPaymentAdapterService adapter;

    @BeforeEach
    void setUp() {
        adapter = new RenewalPaymentAdapterService(gateway, payments, collections, attempts, paymentService);
    }

    @Test
    void signedSuccessfulIpnPersistsTrustedFactsIncludingMalformedTimeForReconciliation() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", true,
                new BigDecimal("250000.00"), null, "bad-time", "123456", "VND", true,
                null, GatewayCallback.PaidTimeStatus.MALFORMED);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));
        when(collections.recordAndSettle(any())).thenReturn(
                new RenewalSettlementResult(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, null, null, "bad time"));

        Optional<OnlineCallbackResult> result = adapter.processVnpayIpnIfRenewal(Map.of("vnp_TxnRef", "..."));

        assertTrue(result.isPresent());
        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, result.get().status());
        ArgumentCaptor<RenewalSettlementEvidence> captor = ArgumentCaptor.forClass(RenewalSettlementEvidence.class);
        verify(collections).recordAndSettle(captor.capture());
        RenewalSettlementEvidence evidence = captor.getValue();
        assertTrue(evidence.verified());
        assertEquals(RenewalSettlementEvidence.PaidAtEvidence.MALFORMED, evidence.paidAtEvidence());
        assertEquals("123456", evidence.providerTransactionReference());
        assertEquals(new BigDecimal("250000.00"), evidence.verifiedCollectionAmount());
        assertEquals("VND", evidence.verifiedCollectionCurrency());
        verify(paymentService).getPaymentResponseForAdapter(41);
    }

    @Test
    void signedSuccessWithMissingProviderReferenceIsRoutedForManualReconciliation() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", true,
                new BigDecimal("250000.00"), null, "20261121101500", null, "VND", true,
                PAID_AT, GatewayCallback.PaidTimeStatus.VALID);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));
        when(collections.recordAndSettle(any())).thenReturn(
                new RenewalSettlementResult(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, null, null, "missing ref"));

        var result = adapter.processVnpayIpnIfRenewal(Map.of());

        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, result.orElseThrow().status());
        ArgumentCaptor<RenewalSettlementEvidence> captor = ArgumentCaptor.forClass(RenewalSettlementEvidence.class);
        verify(collections).recordAndSettle(captor.capture());
        assertNull(captor.getValue().providerTransactionReference());
        assertEquals(PAID_AT, captor.getValue().verifiedPaidAt());
    }

    @Test
    void signedAmountAndCurrencyMismatchArePassedToSettlementForReconciliation() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", true,
                new BigDecimal("250001.00"), null, "20261121101500", "123456", "USD", true,
                PAID_AT, GatewayCallback.PaidTimeStatus.VALID);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));
        when(collections.recordAndSettle(any())).thenReturn(
                new RenewalSettlementResult(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, null, null,
                        "signed amount/currency differs from the quote"));

        var result = adapter.processVnpayIpnIfRenewal(Map.of());

        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, result.orElseThrow().status());
        ArgumentCaptor<RenewalSettlementEvidence> captor = ArgumentCaptor.forClass(RenewalSettlementEvidence.class);
        verify(collections).recordAndSettle(captor.capture());
        assertEquals(new BigDecimal("250001.00"), captor.getValue().verifiedCollectionAmount());
        assertEquals("USD", captor.getValue().verifiedCollectionCurrency());
    }

    @Test
    void signedSuccessWithUnparseableAmountIsNotDiscardedAndCannotSettle() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", true,
                null, null, "20261121101500", "123456", "VND", true,
                PAID_AT, GatewayCallback.PaidTimeStatus.VALID);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));
        when(collections.recordAndSettle(any())).thenReturn(
                new RenewalSettlementResult(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, null, null,
                        "signed amount is malformed"));

        var result = adapter.processVnpayIpnIfRenewal(Map.of());

        assertEquals(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED, result.orElseThrow().status());
        ArgumentCaptor<RenewalSettlementEvidence> captor = ArgumentCaptor.forClass(RenewalSettlementEvidence.class);
        verify(collections).recordAndSettle(captor.capture());
        assertNull(captor.getValue().verifiedCollectionAmount());
        assertEquals("VNPAY_SIGNATURE_VERIFIED", captor.getValue().verificationProvenance());
    }

    @Test
    void invalidSignatureDoesNotCreateRenewalCollectionEvidence() {
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.empty());

        assertTrue(adapter.processVnpayIpnIfRenewal(Map.of()).isEmpty());
        verifyNoInteractions(collections, attempts);
    }

    @Test
    void signedProviderFailureClosesAttemptWithoutSettlement() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", false,
                new BigDecimal("250000.00"), "declined", null, null, "VND", true,
                null, GatewayCallback.PaidTimeStatus.MISSING);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));

        var result = adapter.processVnpayIpnIfRenewal(Map.of());

        assertEquals(OnlineCallbackResult.Status.APPLIED, result.orElseThrow().status());
        verify(attempts).recordProviderFailure(41);
        verifyNoInteractions(collections);
    }

    @Test
    void browserReturnForRenewalOnlyReportsWaitingForIpn() {
        GatewayCallback callback = new GatewayCallback("TXN-REN-ABC123", true,
                new BigDecimal("250000.00"), null, "20261121101500", "123456", "VND", true,
                PAID_AT, GatewayCallback.PaidTimeStatus.VALID);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.of(ONLINE));

        var result = adapter.processVnpayReturnIfRenewal(Map.of());

        assertEquals(OnlineCallbackResult.Status.AWAITING_IPN, result.orElseThrow().status());
        verifyNoInteractions(collections, attempts, paymentService);
    }

    @Test
    void counterUsesStaffIdentityAndFrozenPaymentAmount() {
        when(payments.findRenewalPaymentRouteById(42)).thenReturn(Optional.of(COUNTER));
        User receptionist = new User();
        receptionist.setId(9);
        when(paymentService.authorizeCounterActor(any(Jwt.class))).thenReturn(receptionist);
        when(paymentService.getPaymentResponseForAdapter(42)).thenReturn(mock(PaymentResponse.class));
        CounterPaymentRequest request = new CounterPaymentRequest();
        request.setSuccess(true);

        var result = adapter.processCounterIfRenewal(42, request, mock(Jwt.class));

        assertTrue(result.isPresent());
        ArgumentCaptor<RenewalSettlementEvidence> captor = ArgumentCaptor.forClass(RenewalSettlementEvidence.class);
        verify(collections).recordAndSettle(captor.capture());
        RenewalSettlementEvidence evidence = captor.getValue();
        assertEquals(PaymentMethod.COUNTER, evidence.method());
        assertEquals(COUNTER.renewalIntentId(), evidence.intentId());
        assertEquals(new BigDecimal("250000.00"), evidence.verifiedCollectionAmount());
        assertEquals(9, evidence.counterActorId());
        assertEquals(COUNTER.transactionCode(), evidence.verificationReference());
        assertEquals(RenewalSettlementEvidence.PaidAtEvidence.SERVER_CONFIRMATION_TIME, evidence.paidAtEvidence());
        assertNull(evidence.verifiedPaidAt(), "Persistence boundary generates COUNTER paidAt on the server");
    }

    @Test
    void nonRenewalRoutesAreLeftToExistingPaymentFlow() {
        GatewayCallback callback = new GatewayCallback("TXN-ORD-1", true,
                new BigDecimal("1000.00"), null, "20261121101500", "123456", "VND", true,
                PAID_AT, GatewayCallback.PaidTimeStatus.VALID);
        when(gateway.verifyCallback(anyMap())).thenReturn(Optional.of(callback));
        when(payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())).thenReturn(Optional.empty());

        assertTrue(adapter.processVnpayIpnIfRenewal(Map.of()).isEmpty());
        assertTrue(adapter.processVnpayReturnIfRenewal(Map.of()).isEmpty());
        verifyNoInteractions(collections, attempts);
    }
}
