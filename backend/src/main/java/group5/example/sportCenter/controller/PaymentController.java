package group5.example.sportCenter.controller;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.DecisionNoteRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.dto.PendingCoursePaymentResponse;
import group5.example.sportCenter.dto.PendingPtPaymentResponse;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.OnlineCallbackResult;
import group5.example.sportCenter.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    private final String frontendResultUrl;

    public PaymentController(
            PaymentService paymentService,
            @Value("${app.payment.frontend-result-url:http://localhost:5173/member/membership}")
            String frontendResultUrl) {
        this.paymentService = paymentService;
        this.frontendResultUrl = frontendResultUrl;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest httpRequest) {
        return paymentService.createPayment(request, jwt, httpRequest.getRemoteAddr());
    }

    @PatchMapping("/{id}/counter")
    public PaymentResponse processAtCounter(@PathVariable Integer id,
                                            @Valid @RequestBody CounterPaymentRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.processAtCounter(id, request, jwt);
    }

    @PatchMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return paymentService.cancelPayment(id, jwt);
    }

    /** Callback của mock gateway. Public (permitAll) nhưng bắt buộc có chữ ký hợp lệ. */
    @GetMapping("/online/callback")
    public PaymentResponse onlineCallback(@RequestParam Map<String, String> params) {
        OnlineCallbackResult result = paymentService.processOnlineCallback(params);
        return switch (result.status()) {
            case APPLIED, ALREADY_PROCESSED -> result.payment();
            case INVALID_SIGNATURE ->
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid payment callback signature");
            case NOT_FOUND -> throw new ResourceNotFoundException("Payment not found");
            case NOT_ONLINE ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not an online payment");
            case AMOUNT_MISMATCH ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount does not match");
        };
    }

    /**
     * VNPay Return URL: trình duyệt của member được VNPay chuyển về đây sau khi thanh toán.
     * Xác thực chữ ký, cập nhật payment rồi chuyển tiếp về trang frontend kèm kết quả.
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        OnlineCallbackResult result = paymentService.processOnlineCallback(params);
        UriComponentsBuilder location = UriComponentsBuilder.fromUriString(frontendResultUrl);
        if (result.payment() != null) {
            location.queryParam("paymentId", result.payment().getId())
                    .queryParam("paymentStatus", result.payment().getStatus());
        } else {
            location.queryParam("paymentStatus", "ERROR")
                    .queryParam("reason", result.status().name());
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(location.build().toUri()).build();
    }

    /**
     * VNPay IPN: server VNPay gọi trực tiếp (cần URL public, vd. ngrok, cấu hình trong trang merchant sandbox).
     * Phản hồi theo định dạng VNPay quy định: {"RspCode": "...", "Message": "..."}.
     */
    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            OnlineCallbackResult result = paymentService.processOnlineCallback(params);
            return switch (result.status()) {
                case APPLIED -> vnpayIpnResponse("00", "Confirm Success");
                case ALREADY_PROCESSED -> vnpayIpnResponse("02", "Order already confirmed");
                case INVALID_SIGNATURE -> vnpayIpnResponse("97", "Invalid Checksum");
                case NOT_FOUND, NOT_ONLINE -> vnpayIpnResponse("01", "Order not found");
                case AMOUNT_MISMATCH -> vnpayIpnResponse("04", "Invalid amount");
            };
        } catch (RuntimeException e) {
            return vnpayIpnResponse("99", "Unknown error");
        }
    }

    private static Map<String, String> vnpayIpnResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    @GetMapping("/pending-courses")
    public Page<PendingCoursePaymentResponse> pendingCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getPendingCourseCounterPayments(page, size, jwt);
    }

    @GetMapping("/pending-pt")
    public Page<PendingPtPaymentResponse> pendingPt(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getPendingPtCounterPayments(page, size, jwt);
    }

    @GetMapping("/pending-memberships")
    public Page<PaymentResponse> pendingMemberships(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getPendingMembershipCounterPayments(page, size, jwt);
    }

    @GetMapping("/refund-required")
    public Page<PaymentResponse> refundRequired(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getRefundRequiredPayments(page, size, jwt);
    }

    /** Lễ tân/ADMIN xác nhận đã trả tiền lại cho member (REFUND_REQUIRED -> REFUNDED). */
    @PatchMapping("/{id}/refund")
    public PaymentResponse confirmRefund(@PathVariable Integer id,
                                         @Valid @RequestBody(required = false) DecisionNoteRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return paymentService.confirmRefund(id, request == null ? null : request.note(), jwt);
    }

    @GetMapping("/me")
    public List<PaymentResponse> myHistory(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.getMyHistory(jwt);
    }

    @GetMapping("/member/{memberId}")
    public List<PaymentResponse> historyByMember(@PathVariable Integer memberId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getHistoryByMember(memberId, jwt);
    }
}
