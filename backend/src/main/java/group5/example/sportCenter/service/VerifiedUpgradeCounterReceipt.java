package group5.example.sportCenter.service;

import java.time.Instant;

import group5.example.sportCenter.entity.User;

/** Internal trusted receipt created only after staff authorization and server checks. */
final class VerifiedUpgradeCounterReceipt {
    final Integer paymentId;
    final User staff;
    final String note;
    final Instant receivedAt;

    VerifiedUpgradeCounterReceipt(Integer paymentId, User staff, String note, Instant receivedAt) {
        this.paymentId = paymentId;
        this.staff = staff;
        this.note = note;
        this.receivedAt = receivedAt;
    }
}
