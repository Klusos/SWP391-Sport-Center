package group5.example.sportCenter.entity;

/** Local lifecycle of a single provider checkout attempt. */
public enum CheckoutAttemptStatus {
    CREATED,
    URL_ISSUED,
    FAILED,
    CAPTURED,
    RECONCILIATION_REQUIRED
}
