package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record RevenuePeriodResponse(
        LocalDate periodStart,
        BigDecimal totalRevenue,
        Map<String, BigDecimal> revenueByPurpose) {
}
