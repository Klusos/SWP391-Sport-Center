package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.PaymentReportRow;
import group5.example.sportCenter.dto.RevenueReportResponse;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Báo cáo cho "Manager" trong sơ đồ. Project không có role MANAGER nên dùng ADMIN. */
@Service
@Transactional(readOnly = true)
public class PaymentReportService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public PaymentReportService(PaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    public RevenueReportResponse revenueReport(LocalDate from, LocalDate to, Jwt jwt) {
        requireAdmin(jwt);
        validateRange(from, to);
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
        return new RevenueReportResponse(from, to, paymentRepository.sumRevenue(start, end), byPurpose, byMethod);
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
