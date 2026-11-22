package group5.example.sportCenter.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.service.PaymentService;
import group5.example.sportCenter.service.RenewalPaymentAdapterService;

@ExtendWith(MockitoExtension.class)
class PaymentControllerRenewalRoutingTest {
    @Mock private PaymentService payments;
    @Mock private RenewalPaymentAdapterService renewal;

    @Test
    void signedRenewalReturnRedirectsAsPendingWithoutCallingSettlementPath() {
        when(renewal.processVnpayReturnIfRenewal(anyMap()))
                .thenReturn(Optional.of(new OnlineCallbackResult(OnlineCallbackResult.Status.AWAITING_IPN, null)));
        PaymentController controller = new PaymentController(payments, renewal, "http://frontend.test/result");

        ResponseEntity<Void> response = controller.vnpayReturn(Map.of("vnp_TxnRef", "TXNREN123"));

        assertEquals(302, response.getStatusCode().value());
        assertTrue(response.getHeaders().getLocation().getQuery().contains("paymentStatus=PENDING"));
        verifyNoInteractions(payments);
    }

    @Test
    void renewalIpnIsDispatchedToAdapterBeforeGenericPaymentCallback() {
        when(renewal.processVnpayIpnIfRenewal(anyMap()))
                .thenReturn(Optional.of(new OnlineCallbackResult(OnlineCallbackResult.Status.RECONCILIATION_REQUIRED,
                        mock(PaymentResponse.class))));
        PaymentController controller = new PaymentController(payments, renewal, "http://frontend.test/result");

        Map<String, String> response = controller.vnpayIpn(Map.of("vnp_TxnRef", "TXNREN123"));

        assertEquals("00", response.get("RspCode"));
        verifyNoInteractions(payments);
    }

    @Test
    void renewalCounterResultDoesNotFallThroughToGenericPaymentProcessing() {
        PaymentResponse expected = mock(PaymentResponse.class);
        when(renewal.processCounterIfRenewal(eq(51), any(CounterPaymentRequest.class), isNull()))
                .thenReturn(Optional.of(expected));
        PaymentController controller = new PaymentController(payments, renewal, "http://frontend.test/result");

        PaymentResponse response = controller.processAtCounter(51, new CounterPaymentRequest(), null);

        assertSame(expected, response);
        verifyNoInteractions(payments);
    }

    @Test
    void renewalCancellationUsesLockOrderedAttemptOperation() {
        PaymentResponse expected = mock(PaymentResponse.class);
        when(renewal.cancelIfRenewal(eq(52), isNull())).thenReturn(Optional.of(expected));
        PaymentController controller = new PaymentController(payments, renewal, "http://frontend.test/result");

        assertSame(expected, controller.cancel(52, null));
        verifyNoInteractions(payments);
    }
}
