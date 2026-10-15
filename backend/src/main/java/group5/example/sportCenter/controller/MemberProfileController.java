package group5.example.sportCenter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateMemberProfileRequest;
import group5.example.sportCenter.dto.MemberProfileRequest;
import group5.example.sportCenter.dto.MemberProfileResponse;
import group5.example.sportCenter.dto.UpdateMemberProfileRequest;
import java.util.List;
import group5.example.sportCenter.service.MemberProfileService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/member-profiles")
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    public MemberProfileController(
            MemberProfileService memberProfileService) {

        this.memberProfileService = memberProfileService;
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberProfileResponse createMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MemberProfileRequest request) {

        return memberProfileService.createMyProfile(jwt, request);
    }

    @GetMapping("/me")
    public MemberProfileResponse getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        return memberProfileService.getMyProfile(jwt);
    }

    @GetMapping
    public List<MemberProfileResponse> getAllProfiles() {
        return memberProfileService.getAllProfiles();
    }

    @PutMapping("/me")
    public MemberProfileResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MemberProfileRequest request) {

        if (request.height() == null || request.weight() == null
                || !request.isGoalProvided() || request.goal() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "height, weight, and a non-null goal field are required");
        }

        return memberProfileService.updateMyProfile(jwt, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberProfileResponse createProfile(
            @Valid @RequestBody CreateMemberProfileRequest request) {

        return memberProfileService.createProfile(request);
    }

    @GetMapping("/{id}")
    public MemberProfileResponse getProfileById(
            @PathVariable Integer id) {

        return memberProfileService.getProfileById(id);
    }

    @GetMapping("/member/{memberId}")
    public MemberProfileResponse getProfileByMemberId(
            @PathVariable Integer memberId) {

        return memberProfileService
                .getProfileByMemberId(memberId);
    }

    @PutMapping("/{id}")
    public MemberProfileResponse updateProfile(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMemberProfileRequest request) {

        return memberProfileService
                .updateProfile(id, request);
    }
}
