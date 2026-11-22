package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import group5.example.sportCenter.entity.PaymentMethod;

/** Trusted internal evidence produced by a payment-verification adapter; never bind this to an HTTP request. */
public record RenewalSettlementEvidence(
        Integer intentId,
        Integer paymentId,
        PaymentMethod method,
        CollectionOutcome collectionOutcome,
        boolean verified,
        LocalDateTime verifiedPaidAt,
        PaidAtEvidence paidAtEvidence,
        String verificationProvenance,
        String verificationReference,
        String validationIssue,
        BigDecimal verifiedCollectionAmount,
        String verifiedCollectionCurrency,
        String providerTransactionReference,
        Integer counterActorId) {

    public enum CollectionOutcome { COLLECTED, NOT_COLLECTED, UNKNOWN }
    public enum PaidAtEvidence { VERIFIED_PROVIDER_TIME, SERVER_CONFIRMATION_TIME, MISSING, MALFORMED, NOT_APPLICABLE }

    public static RenewalSettlementEvidence verifiedOnlineCollection(
            Integer intentId, Integer paymentId, PaymentMethod method,
            LocalDateTime verifiedPaidAt, String provenance, String reference) {
        return new RenewalSettlementEvidence(intentId, paymentId, method, CollectionOutcome.COLLECTED,
                true, verifiedPaidAt, PaidAtEvidence.VERIFIED_PROVIDER_TIME, provenance, reference, null,
                null, null, reference, null);
    }

    public static RenewalSettlementEvidence verifiedOnlineCollection(
            Integer intentId, Integer paymentId, BigDecimal amount, String currency,
            String providerTransactionReference, LocalDateTime verifiedPaidAt,
            String provenance, String verificationReference) {
        return new RenewalSettlementEvidence(intentId, paymentId, PaymentMethod.ONLINE,
                CollectionOutcome.COLLECTED, true, verifiedPaidAt, PaidAtEvidence.VERIFIED_PROVIDER_TIME,
                provenance, verificationReference, null, amount, currency, providerTransactionReference, null);
    }

    public static RenewalSettlementEvidence verifiedCounterCollection(
            Integer intentId, Integer paymentId, LocalDateTime serverConfirmedAt, String provenance, String reference) {
        return new RenewalSettlementEvidence(intentId, paymentId, PaymentMethod.COUNTER,
                CollectionOutcome.COLLECTED, true, serverConfirmedAt, PaidAtEvidence.SERVER_CONFIRMATION_TIME,
                provenance, reference, null, null, "VND", null, null);
    }

    public static RenewalSettlementEvidence verifiedCounterCollection(
            Integer intentId, Integer paymentId, BigDecimal amount, Integer staffActorId,
            String verificationReference) {
        return new RenewalSettlementEvidence(intentId, paymentId, PaymentMethod.COUNTER,
                CollectionOutcome.COLLECTED, true, null, PaidAtEvidence.SERVER_CONFIRMATION_TIME,
                "COUNTER_SERVER_CONFIRMATION", verificationReference, null, amount, "VND", null, staffActorId);
    }

    public static RenewalSettlementEvidence verifiedCollectionWithTimestampIssue(
            Integer intentId, Integer paymentId, PaymentMethod method, PaidAtEvidence timestampEvidence,
            String provenance, String reference, String issue) {
        return new RenewalSettlementEvidence(intentId, paymentId, method, CollectionOutcome.COLLECTED,
                true, null, timestampEvidence, provenance, reference, issue, null, null, reference, null);
    }

    public static RenewalSettlementEvidence verifiedCollectionWithTimestampIssue(
            Integer intentId, Integer paymentId, BigDecimal amount, String currency,
            String providerTransactionReference, PaidAtEvidence timestampEvidence,
            String provenance, String verificationReference, String issue) {
        return new RenewalSettlementEvidence(intentId, paymentId, PaymentMethod.ONLINE,
                CollectionOutcome.COLLECTED, true, null, timestampEvidence, provenance, verificationReference,
                issue, amount, currency, providerTransactionReference, null);
    }

    public static RenewalSettlementEvidence verifiedNonCollection(
            Integer intentId, Integer paymentId, PaymentMethod method, String provenance, String reference) {
        return new RenewalSettlementEvidence(intentId, paymentId, method, CollectionOutcome.NOT_COLLECTED,
                true, null, PaidAtEvidence.NOT_APPLICABLE, provenance, reference, null, null, null, null, null);
    }

    public static RenewalSettlementEvidence invalid(
            Integer intentId, Integer paymentId, PaymentMethod method, String issue) {
        return new RenewalSettlementEvidence(intentId, paymentId, method, CollectionOutcome.UNKNOWN,
                false, null, PaidAtEvidence.MALFORMED, null, null, issue, null, null, null, null);
    }
}
