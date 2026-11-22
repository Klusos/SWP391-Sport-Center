package group5.example.sportCenter.repository;

import java.math.BigDecimal;

import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;

/** Scalar routing data used to choose Renewal lock ordering before locking a Payment row. */
public record RenewalPaymentRoute(
        Integer paymentId,
        Integer renewalIntentId,
        Integer memberId,
        PaymentMethod method,
        PaymentPurpose purpose,
        PaymentStatus status,
        BigDecimal amount,
        String transactionCode) {

    public boolean isRenewal() {
        return renewalIntentId != null;
    }
}
