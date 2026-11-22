package group5.example.sportCenter.payment;

import group5.example.sportCenter.dto.PaymentResponse;

/** Kết quả xử lý một callback online; payment là null khi callback bị từ chối. */
public record OnlineCallbackResult(Status status, PaymentResponse payment) {

    public enum Status {
        /** Callback hợp lệ và đã cập nhật payment. */
        APPLIED,
        /** Payment đã được xử lý trước đó (callback lặp lại). */
        ALREADY_PROCESSED,
        INVALID_SIGNATURE,
        NOT_FOUND,
        NOT_ONLINE,
        AMOUNT_MISMATCH,
        /** Renewal may settle only through the trusted VNPay IPN adapter. */
        RENEWAL_IPN_REQUIRED,
        /** A signed browser Return was received; wait for the server-to-server IPN. */
        AWAITING_IPN,
        /** Signed collection was recorded, but requires manual reconciliation. */
        RECONCILIATION_REQUIRED
    }
}
