package group5.example.sportCenter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CreateMemberProfileRequest;
import group5.example.sportCenter.dto.MemberProfileResponse;
import group5.example.sportCenter.dto.UpdateMemberProfileRequest;
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