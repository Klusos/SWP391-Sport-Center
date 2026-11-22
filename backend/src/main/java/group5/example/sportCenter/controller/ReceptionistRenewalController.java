package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.MemberRenewalAttemptActionResponse;
import group5.example.sportCenter.dto.MemberRenewalIntentResponse;
import group5.example.sportCenter.service.MemberRenewalApiService;

/** Staff access to Renewal quotes and COUNTER attempts; Intent creation remains scheduler-only. */
@RestController
@RequestMapping("/api/receptionist/members/{memberId}/renewal-intents")
public class ReceptionistRenewalController {
    private final MemberRenewalApiService renewalApi;

    public ReceptionistRenewalController(MemberRenewalApiService renewalApi) {
        this.renewalApi = renewalApi;
    }

    @GetMapping
    public List<MemberRenewalIntentResponse> forMember(
            @PathVariable Integer memberId, @AuthenticationPrincipal Jwt jwt) {
        return renewalApi.listForReceptionist(memberId, jwt);
    }

    @PostMapping("/{intentId}/counter-attempts")
    public MemberRenewalAttemptActionResponse startCounterAttempt(
            @PathVariable Integer memberId,
            @PathVariable Integer intentId,
            @AuthenticationPrincipal Jwt jwt) {
        return renewalApi.startCounterAttemptForReceptionist(memberId, intentId, jwt);
    }
}
