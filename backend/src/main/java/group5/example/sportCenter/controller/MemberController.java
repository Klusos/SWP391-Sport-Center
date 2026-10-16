package group5.example.sportCenter.controller;

import group5.example.sportCenter.dto.CreateMemberRequest;
import group5.example.sportCenter.dto.MemberResponse;
import group5.example.sportCenter.dto.UpdateMemberRequest;
import group5.example.sportCenter.service.MemberRegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRegistrationService memberRegistrationService;

    @PostMapping
    public ResponseEntity<MemberResponse> createMember(
            @Valid @RequestBody CreateMemberRequest request) {

        MemberResponse response =
                memberRegistrationService.createMember(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public MemberResponse updateMember(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMemberRequest request) {
        return memberRegistrationService.updateMember(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public MemberResponse deactivateMember(@PathVariable Integer id) {
        return memberRegistrationService.deactivateMember(id);
    }
}
