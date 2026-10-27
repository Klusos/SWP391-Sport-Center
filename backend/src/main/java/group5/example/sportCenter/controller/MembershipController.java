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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CancelMembershipRequest;
import group5.example.sportCenter.dto.CreateMembershipCancellationRequest;
import group5.example.sportCenter.dto.CreateMembershipRequest;
import group5.example.sportCenter.dto.DecisionNoteRequest;
import group5.example.sportCenter.dto.MembershipCancellationRequestResponse;
import group5.example.sportCenter.dto.MembershipResponse;
import group5.example.sportCenter.dto.MembershipUpgradeOptionsResponse;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.service.MembershipAutoRenewalService;
import group5.example.sportCenter.service.MembershipCancellationRequestService;
import group5.example.sportCenter.service.MembershipService;
import group5.example.sportCenter.service.MembershipUpgradeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipUpgradeService membershipUpgradeService;
    private final MembershipCancellationRequestService cancellationRequestService;

    public MembershipController(
            MembershipService membershipService,
            MembershipUpgradeService membershipUpgradeService,
            MembershipCancellationRequestService cancellationRequestService) {
        this.membershipService = membershipService;
        this.membershipUpgradeService = membershipUpgradeService;
        this.cancellationRequestService = cancellationRequestService;
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

    /** Member gửi yêu cầu huỷ gói chưa bắt đầu, chậm nhất 72 giờ trước ngày bắt đầu. */
    @PostMapping("/cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipCancellationRequestResponse requestCancellation(
            @Valid @RequestBody CreateMembershipCancellationRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return cancellationRequestService.create(request.membershipId(), request.reason(), jwt);
    }

    @GetMapping("/cancellation-requests/me")
    public List<MembershipCancellationRequestResponse> myCancellationRequests(@AuthenticationPrincipal Jwt jwt) {
        return cancellationRequestService.getMine(jwt);
    }

    /** Hàng đợi cho lễ tân/ADMIN; mặc định các yêu cầu đang chờ duyệt. */
    @GetMapping("/cancellation-requests")
    public List<MembershipCancellationRequestResponse> cancellationRequests(
            @RequestParam(defaultValue = "PENDING") MembershipCancellationRequestStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        return cancellationRequestService.getByStatus(status, jwt);
    }

    @PatchMapping("/cancellation-requests/{id}/withdraw")
    public MembershipCancellationRequestResponse withdrawCancellationRequest(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {

        return cancellationRequestService.withdraw(id, jwt);
    }

    @PatchMapping("/cancellation-requests/{id}/approve")
    public MembershipCancellationRequestResponse approveCancellationRequest(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return cancellationRequestService.approve(id, request == null ? null : request.note(), jwt);
    }

    /** Từ chối bắt buộc có ghi chú để gửi cho member. */
    @PatchMapping("/cancellation-requests/{id}/reject")
    public MembershipCancellationRequestResponse rejectCancellationRequest(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return cancellationRequestService.reject(id, request == null ? null : request.note(), jwt);
    }

    /** Staff huỷ trực tiếp: gói chưa bắt đầu (hoàn 100%) hoặc gói đang chạy (chỉ ADMIN, cần lý do). */
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
