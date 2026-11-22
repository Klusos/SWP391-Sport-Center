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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CreateMembershipUpgradePathRequest;
import group5.example.sportCenter.dto.MembershipUpgradePathResponse;
import group5.example.sportCenter.dto.UpdateMembershipUpgradePathStatusRequest;
import group5.example.sportCenter.service.MembershipUpgradePathService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/membership-upgrade-paths")
public class MembershipUpgradePathController {
    private final MembershipUpgradePathService pathService;

    public MembershipUpgradePathController(MembershipUpgradePathService pathService) {
        this.pathService = pathService;
    }

    @GetMapping
    public List<MembershipUpgradePathResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return pathService.list(jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipUpgradePathResponse create(
            @Valid @RequestBody CreateMembershipUpgradePathRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return pathService.create(request, jwt);
    }

    @PatchMapping("/{id}/status")
    public MembershipUpgradePathResponse setStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMembershipUpgradePathStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return pathService.setStatus(id, request.active(), jwt);
    }
}
