package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.MembershipResponse;
import group5.example.sportCenter.dto.MembershipUpgradeQuoteResponse;
import group5.example.sportCenter.dto.CreateMembershipUpgradePaymentRequest;
import group5.example.sportCenter.dto.MembershipUpgradePaymentResponse;
import group5.example.sportCenter.service.MembershipService;
import group5.example.sportCenter.service.MembershipUpgradeQuoteService;
import group5.example.sportCenter.service.MembershipUpgradePaymentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipUpgradeQuoteService membershipUpgradeQuoteService;
    private final MembershipUpgradePaymentService membershipUpgradePaymentService;

    public MembershipController(
            MembershipService membershipService,
            MembershipUpgradeQuoteService membershipUpgradeQuoteService,
            MembershipUpgradePaymentService membershipUpgradePaymentService) {
        this.membershipService = membershipService;
        this.membershipUpgradeQuoteService = membershipUpgradeQuoteService;
        this.membershipUpgradePaymentService = membershipUpgradePaymentService;
    }

    @GetMapping
    public List<MembershipResponse> getAllMemberships(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.getAllMemberships(jwt);
    }

    @GetMapping("/me/upgrade-options")
    public MembershipUpgradeQuoteResponse getMyUpgradeOptions(@AuthenticationPrincipal Jwt jwt) {
        return membershipUpgradeQuoteService.getMyUpgradeOptions(jwt);
    }

    @PostMapping("/me/upgrade-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipUpgradePaymentResponse createMyUpgradePayment(
            @jakarta.validation.Valid @RequestBody CreateMembershipUpgradePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return membershipUpgradePaymentService.create(jwt, request);
    }

    @GetMapping("/{id}")
    public MembershipResponse getMembershipById(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.getMembershipById(id, jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse createMembership(
            @Valid @RequestBody CreateMembershipRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.createMembership(request, jwt);
    }

    @GetMapping("/member/{memberId}")
    public List<MembershipResponse> getMembershipsByMemberId(
            @PathVariable Integer memberId,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.getMembershipsByMemberId(memberId, jwt);
    }

    @PatchMapping("/{id}/cancel-renewal")
    public MembershipResponse cancelAutoRenew(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.cancelAutoRenew(id, jwt);
    }

    @PatchMapping("/{id}/enable-auto-renew")
    public MembershipResponse enableAutoRenew(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.enableAutoRenew(id, jwt);
    }

    @PostMapping("/expire")
    public String expireMemberships(@AuthenticationPrincipal Jwt jwt) {

        int expiredCount =
                membershipService.expireMemberships(jwt);

        return expiredCount
                + " membership(s) expired";
    }
}
