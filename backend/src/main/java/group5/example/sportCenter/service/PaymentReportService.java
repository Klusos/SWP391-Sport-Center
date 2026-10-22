package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.PaymentReportRow;
import group5.example.sportCenter.dto.RevenueGrouping;
import group5.example.sportCenter.dto.RevenuePeriodResponse;
import group5.example.sportCenter.dto.RevenueReportResponse;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Báo cáo cho "Manager" trong sơ đồ. Project không có role MANAGER nên dùng ADMIN. */
@Service
@Transactional(readOnly = true)
public class PaymentReportService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private static final int MAX_REPORT_PERIODS = 366;

    public PaymentReportService(PaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    public RevenueReportResponse revenueReport(LocalDate from, LocalDate to, RevenueGrouping groupBy, Jwt jwt) {
        requireAdmin(jwt);
        validateRevenueRange(from, to, groupBy);
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay(); // nửa mở: [from, to+1)

        Map<String, BigDecimal> byPurpose = new LinkedHashMap<>();
        for (Object[] r : paymentRepository.revenueByPurpose(start, end)) {
            byPurpose.put(r[0].toString(), (BigDecimal) r[1]);
        }
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        for (Object[] r : paymentRepository.revenueByMethod(start, end)) {
            byMethod.put(r[0].toString(), (BigDecimal) r[1]);
        }
        Map<LocalDate, Map<String, BigDecimal>> daily = new TreeMap<>();
        for (Object[] row : paymentRepository.revenueBySettlementDay(start, end)) {
            LocalDate day = LocalDate.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            daily.computeIfAbsent(day, ignored -> emptyPurposeTotals())
                    .put(row[3].toString(), (BigDecimal) row[4]);
        }

        Map<LocalDate, Map<String, BigDecimal>> grouped = new TreeMap<>();
        daily.forEach((day, amounts) -> {
            Map<String, BigDecimal> totals = grouped.computeIfAbsent(bucketStart(day, groupBy),
                    ignored -> emptyPurposeTotals());
            amounts.forEach((purpose, amount) -> totals.merge(purpose, amount, BigDecimal::add));
        });

        List<RevenuePeriodResponse> periods = new ArrayList<>();
        for (LocalDate bucket = bucketStart(from, groupBy), last = bucketStart(to, groupBy);
             !bucket.isAfter(last); bucket = nextBucket(bucket, groupBy)) {
            Map<String, BigDecimal> values = grouped.getOrDefault(bucket, emptyPurposeTotals());
            BigDecimal bucketTotal = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            periods.add(new RevenuePeriodResponse(bucket, bucketTotal, values));
        }
        for (PaymentPurpose purpose : PaymentPurpose.values()) {
            byPurpose.putIfAbsent(purpose.name(), BigDecimal.ZERO);
        }
        return new RevenueReportResponse(from, to, paymentRepository.sumRevenue(start, end), byPurpose, byMethod,
                groupBy.name(), periods);
    }

    private Map<String, BigDecimal> emptyPurposeTotals() {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PaymentPurpose purpose : PaymentPurpose.values()) totals.put(purpose.name(), BigDecimal.ZERO);
        return totals;
    }

    private LocalDate bucketStart(LocalDate date, RevenueGrouping groupBy) {
        return switch (groupBy) {
            case DAY -> date;
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private LocalDate nextBucket(LocalDate date, RevenueGrouping groupBy) {
        return switch (groupBy) {
            case DAY -> date.plusDays(1);
            case MONTH -> date.plusMonths(1);
            case YEAR -> date.plusYears(1);
        };
    }

    private void validateRevenueRange(LocalDate from, LocalDate to, RevenueGrouping groupBy) {
        validateRange(from, to);
        if (groupBy == null || to.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("A supported groupBy and date range are required");
        }
        long periods = switch (groupBy) {
            case DAY -> ChronoUnit.DAYS.between(from, to) + 1;
            case MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            case YEAR -> (long) to.getYear() - from.getYear() + 1;
        };
        if (periods > MAX_REPORT_PERIODS) {
            throw new IllegalArgumentException("Revenue reports are limited to 366 output periods");
        }
    }

    public List<PaymentReportRow> memberReport(LocalDate from, LocalDate to, Jwt jwt) {
        requireAdmin(jwt);
        validateRange(from, to);
        return toRows(paymentRepository.memberReport(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    public List<PaymentReportRow> classRegistrationReport(LocalDate from, LocalDate to, Jwt jwt) {
        requireAdmin(jwt);
        validateRange(from, to);
        return toRows(paymentRepository.classRegistrationReport(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    private List<PaymentReportRow> toRows(List<Object[]> raw) {
        return raw.stream()
                .map(r -> new PaymentReportRow((Integer) r[0], (String) r[1], (Long) r[2], (BigDecimal) r[3]))
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
    }

    private void requireAdmin(Jwt jwt) {
        final Integer userId;
        try {
            userId = Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus()) || !"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can view reports");
        }
    }
}
