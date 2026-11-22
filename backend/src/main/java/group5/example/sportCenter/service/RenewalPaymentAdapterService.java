package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.payment.GatewayCallback;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.RenewalPaymentRoute;

/** Routes only relationally linked Renewal attempts through the trusted internal collection engine. */
@Service
public class RenewalPaymentAdapterService {
    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final MembershipRenewalCollectionService collections;
    private final MembershipRenewalPaymentAttemptService attempts;
    private final PaymentService paymentService;

    public RenewalPaymentAdapterService(PaymentGateway gateway, PaymentRepository payments,
            MembershipRenewalCollectionService collections,
            MembershipRenewalPaymentAttemptService attempts, PaymentService paymentService) {
        this.gateway = gateway;
        this.payments = payments;
        this.collections = collections;
        this.attempts = attempts;
        this.paymentService = paymentService;
    }

    /**
     * Handles a Renewal IPN without an ambient transaction so collection facts commit before settlement.
     * Empty means the payment is not a Renewal attempt and the existing callback path should handle it.
     */
    public Optional<OnlineCallbackResult> processVnpayIpnIfRenewal(Map<String, String> params) {
        Optional<GatewayCallback> verified = gateway.verifyCallback(params);
        if (verified.isEmpty() || !verified.get().providerSignatureVerified()) return Optional.empty();
        GatewayCallback callback = verified.get();
        RenewalPaymentRoute route = payments.findRenewalPaymentRouteByTransactionCode(callback.transactionCode())
                .orElse(null);
        if (route == null || !route.isRenewal()) return Optional.empty();
        if (route.method() != PaymentMethod.ONLINE) {
            return Optional.of(new OnlineCallbackResult(OnlineCallbackResult.Status.NOT_ONLINE, null));
        }

        if (!callback.success()) {
            attempts.recordProviderFailure(route.paymentId());
            return Optional.of(new OnlineCallbackResult(OnlineCallbackResult.Status.APPLIED,
                    paymentService.getPaymentResponseForAdapter(route.paymentId())));
        }

        RenewalSettlementEvidence evidence = onlineEvidence(route.renewalIntentId(), route.paymentId(), callback);
        RenewalSettlementResult result = collections.recordAndSettle(evidence);
        OnlineCallbackResult.Status status = switch (result.outcome()) {
            case ACCEPTED -> OnlineCallbackResult.Status.APPLIED;
            case ALREADY_ACCEPTED -> OnlineCallbackResult.Status.ALREADY_PROCESSED;
            case PAYMENT_NOT_COLLECTED -> OnlineCallbackResult.Status.APPLIED;
            default -> OnlineCallbackResult.Status.RECONCILIATION_REQUIRED;
        };
        return Optional.of(new OnlineCallbackResult(status,
                paymentService.getPaymentResponseForAdapter(route.paymentId())));
    }

    /** Browser Return is informational for Renewal; only a server-to-server IPN may settle it. */
    public Optional<OnlineCallbackResult> processVnpayReturnIfRenewal(Map<String, String> params) {
        Optional<GatewayCallback> verified = gateway.verifyCallback(params);
        if (verified.isEmpty() || !verified.get().providerSignatureVerified()) return Optional.empty();
        RenewalPaymentRoute route = payments.findRenewalPaymentRouteByTransactionCode(
                verified.get().transactionCode()).orElse(null);
        if (route == null || !route.isRenewal()) return Optional.empty();
        return Optional.of(new OnlineCallbackResult(OnlineCallbackResult.Status.AWAITING_IPN, null));
    }

    /** Renewal COUNTER confirmation uses authenticated staff and server time, never request timestamps. */
    public Optional<PaymentResponse> processCounterIfRenewal(Integer paymentId,
            CounterPaymentRequest request, Jwt jwt) {
        RenewalPaymentRoute route = payments.findRenewalPaymentRouteById(paymentId).orElse(null);
        if (route == null || !route.isRenewal()) return Optional.empty();
        if (route.method() != PaymentMethod.COUNTER) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "This payment is not a counter payment");
        }
        var actor = paymentService.authorizeCounterActor(jwt);
        if (!Boolean.TRUE.equals(request.getSuccess())) {
            attempts.recordProviderFailure(paymentId);
            return Optional.of(paymentService.getPaymentResponseForAdapter(paymentId));
        }

        RenewalSettlementEvidence evidence = RenewalSettlementEvidence.verifiedCounterCollection(
                route.renewalIntentId(), paymentId, route.amount(), actor.getId(), route.transactionCode());
        collections.recordAndSettle(evidence);
        return Optional.of(paymentService.getPaymentResponseForAdapter(paymentId));
    }

    /** Existing Payment cancel API routed to the lock-ordered Renewal close operation. */
    public Optional<PaymentResponse> cancelIfRenewal(Integer paymentId, Jwt jwt) {
        RenewalPaymentRoute route = payments.findRenewalPaymentRouteById(paymentId).orElse(null);
        if (route == null || !route.isRenewal()) return Optional.empty();
        var actor = paymentService.authenticatePaymentActor(jwt);
        attempts.cancelAttempt(paymentId, actor.getId());
        return Optional.of(paymentService.getPaymentResponseForAdapter(paymentId));
    }

    private RenewalSettlementEvidence onlineEvidence(Integer intentId, Integer paymentId, GatewayCallback callback) {
        GatewayCallback.PaidTimeStatus status = callback.paidTimeStatus();
        RenewalSettlementEvidence.PaidAtEvidence timeEvidence = switch (status) {
            case VALID -> RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME;
            case MISSING -> RenewalSettlementEvidence.PaidAtEvidence.MISSING;
            case MALFORMED, NOT_SIGNATURE_VERIFIED -> RenewalSettlementEvidence.PaidAtEvidence.MALFORMED;
        };
        BigDecimal amount = fitsSqlDecimal(callback.amount()) ? callback.amount() : null;
        String currency = callback.currency() != null && callback.currency().length() <= 3
                ? callback.currency() : null;
        String providerReference = callback.providerTransactionReference() != null
                && callback.providerTransactionReference().length() <= 64
                        ? callback.providerTransactionReference() : null;
        String issue = callback.isRenewalCollectionEligible() ? null
                : "Signed VNPay collection is missing or has invalid Renewal settlement fields";
        return new RenewalSettlementEvidence(intentId, paymentId, PaymentMethod.ONLINE,
                RenewalSettlementEvidence.CollectionOutcome.COLLECTED, callback.providerSignatureVerified(),
                callback.parsedVerifiedPaidAt(), timeEvidence, "VNPAY_SIGNATURE_VERIFIED",
                callback.transactionCode(), issue, amount, currency, providerReference, null);
    }

    private boolean fitsSqlDecimal(BigDecimal value) {
        return value != null && value.scale() <= 2 && value.precision() - value.scale() <= 10;
    }
}
