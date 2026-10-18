package group5.example.sportCenter.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.PaymentReportRow;
import group5.example.sportCenter.dto.RevenueReportResponse;
import group5.example.sportCenter.service.PaymentReportService;

@RestController
@RequestMapping("/api/reports")
public class PaymentReportController {

    private final PaymentReportService reportService;

    public PaymentReportController(PaymentReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return reportService.revenueReport(from, to, jwt);
    }

    @GetMapping("/members")
    public List<PaymentReportRow> members(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return reportService.memberReport(from, to, jwt);
    }

    @GetMapping("/class-registrations")
    public List<PaymentReportRow> classRegistrations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return reportService.classRegistrationReport(from, to, jwt);
    }
}
