package group5.example.sportCenter.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class MembershipResponse {

    private Integer id;

    private Integer memberId;
    private String memberName;

    private Integer planId;
    private String planName;

    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoRenew;
    private String status;
    private Integer purchasedMonths;
    private BigDecimal paidAmount;
    private Integer paymentId;
}
