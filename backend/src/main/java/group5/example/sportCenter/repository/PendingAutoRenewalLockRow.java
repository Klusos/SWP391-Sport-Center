package group5.example.sportCenter.repository;

/** Scalar lock-order metadata; avoids loading a stale Payment entity before acquiring its locks. */
public record PendingAutoRenewalLockRow(Integer paymentId, Integer memberId, String note) {
}
