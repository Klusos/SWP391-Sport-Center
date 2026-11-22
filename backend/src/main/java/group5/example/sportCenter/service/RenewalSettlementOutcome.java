package group5.example.sportCenter.service;

/** Domain outcome only; callers map outcomes to transport behavior in a later integration phase. */
public enum RenewalSettlementOutcome {
    ACCEPTED,
    ALREADY_ACCEPTED,
    INVALID_EVIDENCE,
    PAYMENT_NOT_COLLECTED,
    PAYMENT_LATE,
    ATTEMPT_CANCELLED,
    INTENT_CLOSED,
    ALREADY_SETTLED_BY_ANOTHER_ATTEMPT,
    RECONCILIATION_REQUIRED,
    CONFLICT,
    LEGACY_PAYMENT_REJECTED
}
