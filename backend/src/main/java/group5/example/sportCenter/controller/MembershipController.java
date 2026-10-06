package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
    public List<MembershipResponse> getAllMemberships() {
        return membershipService.getAllMemberships();
    }

    @GetMapping("/{id}")
    public MembershipResponse getMembershipById(
            @PathVariable Integer id) {

        return membershipService.getMembershipById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse createMembership(
            @Valid @RequestBody CreateMembershipRequest request) {

        return membershipService.createMembership(request);
    }

    @GetMapping("/member/{memberId}")
    public List<MembershipResponse> getMembershipsByMemberId(
            @PathVariable Integer memberId) {

        return membershipService.getMembershipsByMemberId(memberId);
    }

    @PatchMapping("/{id}/cancel-renewal")
    public MembershipResponse cancelAutoRenew(
            @PathVariable Integer id) {

        return membershipService.cancelAutoRenew(id);
    }

    @PatchMapping("/{id}/enable-auto-renew")
    public MembershipResponse enableAutoRenew(
            @PathVariable Integer id) {

        return membershipService.enableAutoRenew(id);
    }

    @PostMapping("/expire")
    public String expireMemberships() {

        int expiredCount =
                membershipService.expireMemberships();

        return expiredCount
                + " membership(s) expired";
    }
}