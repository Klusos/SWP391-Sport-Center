package group5.example.sportCenter.entity;

/** Values currently permitted by dbo.ck_payment_renewal_attempt_fields. */
public enum RenewalAttemptCloseReason {
    DUPLICATE_SETTLEMENT,
    LATE_PROVIDER_PAYMENT,
    INTENT_EXPIRED,
    MEMBER_CANCELLED,
    ATTEMPT_EXPIRED,
    PROVIDER_FAILED
}
