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
import jakarta.servlet.http.HttpServletRequest;

/** Authenticated Member read/attempt API. It never settles a Payment or creates a Membership. */
@RestController
@RequestMapping("/api/memberships/renewal-intents")
public class MemberRenewalController {
    private final MemberRenewalApiService renewalApi;

    public MemberRenewalController(MemberRenewalApiService renewalApi) {
        this.renewalApi = renewalApi;
    }

    @GetMapping("/me")
    public List<MemberRenewalIntentResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return renewalApi.listMine(jwt);
    }

    @GetMapping("/{intentId}")
    public MemberRenewalIntentResponse getMine(
            @PathVariable Integer intentId,
            @AuthenticationPrincipal Jwt jwt) {
        return renewalApi.getMine(intentId, jwt);
    }

    @PostMapping("/{intentId}/attempts")
    public MemberRenewalAttemptActionResponse startOnlineAttempt(
            @PathVariable Integer intentId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return renewalApi.startOnlineAttempt(intentId, jwt, request.getRemoteAddr());
    }
}
