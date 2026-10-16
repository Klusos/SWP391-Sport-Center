package group5.example.sportCenter.controller;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import group5.example.sportCenter.dto.CreatePersonalTrainingBookingRequest;
import group5.example.sportCenter.dto.EligiblePtCoachResponse;
import group5.example.sportCenter.dto.PersonalTrainingBookingResponse;
import group5.example.sportCenter.service.PersonalTrainingBookingService;

@RestController
@RequestMapping("/api/pt-bookings")
public class PersonalTrainingBookingController {
    private final PersonalTrainingBookingService service;

    public PersonalTrainingBookingController(PersonalTrainingBookingService service) {
        this.service = service;
    }

    @GetMapping("/eligible-coaches")
    public List<EligiblePtCoachResponse> eligibleCoaches(
            @RequestParam Integer sportId,
            @RequestParam LocalDateTime startTime,
            @AuthenticationPrincipal Jwt jwt) {
        return service.eligibleCoaches(jwt, sportId, startTime);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingBookingResponse create(
            @Valid @RequestBody CreatePersonalTrainingBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.create(jwt, request);
    }

    @GetMapping("/me")
    public List<PersonalTrainingBookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(jwt);
    }

    @GetMapping("/assigned")
    public List<PersonalTrainingBookingResponse> assigned(@AuthenticationPrincipal Jwt jwt) {
        return service.assignedToMe(jwt);
    }
}
