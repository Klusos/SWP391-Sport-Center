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
import group5.example.sportCenter.service.MembershipService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(
            MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<MembershipResponse> getAllMemberships(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.getAllMemberships(jwt);
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
