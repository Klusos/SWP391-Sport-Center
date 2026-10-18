package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Dòng báo cáo chung cho member report và class registration report. */
@Getter
@Setter
@AllArgsConstructor
public class PaymentReportRow {
    private Integer id;
    private String name;
    private long paymentCount;
    private BigDecimal totalAmount;
}
