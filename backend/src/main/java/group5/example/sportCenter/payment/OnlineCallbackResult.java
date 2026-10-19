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
        AMOUNT_MISMATCH
    }
}
