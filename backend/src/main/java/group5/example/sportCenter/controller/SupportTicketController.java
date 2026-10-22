package group5.example.sportCenter.controller;

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

import group5.example.sportCenter.dto.CreateSupportTicketMessageRequest;
import group5.example.sportCenter.dto.CreateSupportTicketRequest;
import group5.example.sportCenter.dto.SupportTicketDetailResponse;
import group5.example.sportCenter.dto.SupportTicketSummaryResponse;
import group5.example.sportCenter.dto.UpdateSupportTicketStatusRequest;
import group5.example.sportCenter.service.SupportTicketService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/support-tickets")
public class SupportTicketController {
    private final SupportTicketService service;

    public SupportTicketController(SupportTicketService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketDetailResponse create(@Valid @RequestBody CreateSupportTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, jwt);
    }

    @GetMapping("/me")
    public Page<SupportTicketSummaryResponse> mine(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return service.mine(page, size, jwt);
    }

    @GetMapping
    public Page<SupportTicketSummaryResponse> staffList(@RequestParam(required = false) String status,
            @RequestParam(required = false) String category, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.staffList(status, category, search, page, size, jwt);
    }

    @GetMapping("/{id}")
    public SupportTicketDetailResponse get(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, jwt);
    }

    @PostMapping("/{id}/messages")
    public SupportTicketDetailResponse reply(@PathVariable Integer id,
            @Valid @RequestBody CreateSupportTicketMessageRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.reply(id, request, jwt);
    }

    @PatchMapping("/{id}/status")
    public SupportTicketDetailResponse status(@PathVariable Integer id,
            @Valid @RequestBody UpdateSupportTicketStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.updateStatus(id, request, jwt);
    }

    @PatchMapping("/{id}/assign-to-me")
    public SupportTicketDetailResponse assignToMe(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return service.assignToMe(id, jwt);
    }
}
