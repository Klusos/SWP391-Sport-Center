package group5.example.sportCenter.repository;

import group5.example.sportCenter.entity.PaymentPurpose;

/** Scalar lock-order metadata; avoids loading a stale Payment entity before acquiring its locks. */
public record PendingAutoRenewalLockRow(Integer paymentId, Integer memberId, String note, PaymentPurpose purpose) {
}
