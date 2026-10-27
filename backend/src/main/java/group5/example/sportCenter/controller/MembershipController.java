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

import group5.example.sportCenter.dto.CancelMembershipRequest;
import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.MembershipResponse;
import group5.example.sportCenter.dto.MembershipUpgradeOptionsResponse;
import group5.example.sportCenter.service.MembershipAutoRenewalService;
import group5.example.sportCenter.service.MembershipService;
import group5.example.sportCenter.service.MembershipUpgradeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipUpgradeService membershipUpgradeService;

    public MembershipController(
            MembershipService membershipService,
            MembershipUpgradeService membershipUpgradeService) {
        this.membershipService = membershipService;
        this.membershipUpgradeService = membershipUpgradeService;
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

    /** Gói hiện tại, các gói có thể nâng lên và số tiền phải trả; payment tạo qua POST /api/payments (purpose UPGRADE). */
    @GetMapping("/member/{memberId}/upgrade-options")
    public MembershipUpgradeOptionsResponse getUpgradeOptions(
            @PathVariable Integer memberId,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipUpgradeService.getOptions(memberId, jwt);
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

    /** Huỷ gói chưa bắt đầu (member/staff, hoàn 100%) hoặc gói đang chạy (chỉ ADMIN, cần lý do). */
    @PatchMapping("/{id}/cancel")
    public MembershipResponse cancelMembership(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) CancelMembershipRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return membershipService.cancelMembership(id, request == null ? null : request.reason(), jwt);
    }

    @PostMapping("/auto-renew/run")
    public MembershipAutoRenewalService.RunResult runAutoRenewal(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.runAutoRenewal(jwt);
    }

    @PostMapping("/expire")
    public String expireMemberships(@AuthenticationPrincipal Jwt jwt) {

        int expiredCount =
                membershipService.expireMemberships(jwt);

        return expiredCount
                + " membership(s) expired";
    }
}
