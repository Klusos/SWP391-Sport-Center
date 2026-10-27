package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RevenueReportResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalRevenue;
    private Map<String, BigDecimal> revenueByPurpose;
    private Map<String, BigDecimal> revenueByMethod;
    private String groupBy;
    private List<RevenuePeriodResponse> periods;
}
