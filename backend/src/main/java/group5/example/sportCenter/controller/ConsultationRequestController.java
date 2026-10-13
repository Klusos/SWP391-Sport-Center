package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CancelConsultationRequest;
import group5.example.sportCenter.dto.CompleteConsultationRequest;
import group5.example.sportCenter.dto.ConsultationResponse;
import group5.example.sportCenter.dto.CreateConsultationMemberRequest;
import group5.example.sportCenter.dto.CreateConsultationRequest;
import group5.example.sportCenter.dto.LinkConsultationMemberRequest;
import group5.example.sportCenter.service.ConsultationRequestService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/consultation-requests")
public class ConsultationRequestController {

    private final ConsultationRequestService service;

    public ConsultationRequestController(ConsultationRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse create(
            @Valid @RequestBody CreateConsultationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, jwt);
    }

    @GetMapping("/me")
    public List<ConsultationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(jwt);
    }

    @GetMapping
    public Page<ConsultationResponse> all(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.all(status, page, size, jwt);
    }

    @GetMapping("/{id}")
    public ConsultationResponse get(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, jwt);
    }

    @PatchMapping("/{id}/take")
    public ConsultationResponse take(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return service.take(id, jwt);
    }

    @PatchMapping("/{id}/complete")
    public ConsultationResponse complete(
            @PathVariable Integer id,
            @Valid @RequestBody CompleteConsultationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.complete(id, request, jwt);
    }

    @PatchMapping("/{id}/cancel")
    public ConsultationResponse cancel(
            @PathVariable Integer id,
            @Valid @RequestBody CancelConsultationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, request, jwt);
    }

    @PostMapping("/{id}/create-member")
    public ConsultationResponse createMember(
            @PathVariable Integer id,
            @Valid @RequestBody CreateConsultationMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.createMember(id, request, jwt);
    }

    @PatchMapping("/{id}/link-member")
    public ConsultationResponse linkMember(
            @PathVariable Integer id,
            @Valid @RequestBody LinkConsultationMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.linkMember(id, request, jwt);
    }
}
