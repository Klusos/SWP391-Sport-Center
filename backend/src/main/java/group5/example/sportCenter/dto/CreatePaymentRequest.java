package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Số tiền KHÔNG nhận từ client; server lấy theo giá của plan / offering. */
@Getter
@Setter
public class CreatePaymentRequest {

    /** MEMBER có thể bỏ trống (mặc định là chính mình). ADMIN/RECEPTIONIST bắt buộc truyền. */
    private Integer memberId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    @NotNull(message = "Payment purpose is required")
    private PaymentPurpose purpose;

    @NotNull(message = "Reference ID is required")
    private Integer referenceId;

    /** Dùng khi purpose = MEMBERSHIP; mặc định false. */
    private Boolean autoRenew;
}
