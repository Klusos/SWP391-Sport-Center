package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PaymentResponse {
    private Integer id;
    private Integer memberId;
    private String memberName;
    private BigDecimal amount;
    private String method;
    private String purpose;
    private Integer referenceId;
    private String status;
    private String transactionCode;
    /** Chỉ có khi tạo payment ONLINE. */
    private String paymentUrl;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private Integer purchasedMonths;
}
