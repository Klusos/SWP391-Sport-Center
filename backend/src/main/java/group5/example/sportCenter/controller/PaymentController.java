package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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

import group5.example.sportCenter.dto.CounterPaymentRequest;
import group5.example.sportCenter.dto.CreatePaymentRequest;
import group5.example.sportCenter.dto.PaymentResponse;
import group5.example.sportCenter.service.PaymentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return paymentService.createPayment(request, jwt);
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

    /** Public (permitAll) nhưng bắt buộc có chữ ký hợp lệ từ cổng thanh toán. */
    @GetMapping("/online/callback")
    public PaymentResponse onlineCallback(@RequestParam String transactionCode,
                                          @RequestParam boolean success,
                                          @RequestParam String signature) {
        return paymentService.handleOnlineCallback(transactionCode, success, signature);
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
