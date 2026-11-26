package group5.example.sportCenter.controller;

import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.PtBookingOutcomeRequest;
import group5.example.sportCenter.dto.PtBookingOutcomeResponse;
import group5.example.sportCenter.service.PtBookingOutcomeService;
import group5.example.sportCenter.service.PersonalTrainingBookingService;
import group5.example.sportCenter.dto.PersonalTrainingBookingResponse;

@RestController
@RequestMapping("/api/admin/pt-bookings")
public class AdminPtBookingOutcomeController {
    private final PtBookingOutcomeService outcomes;
    private final PersonalTrainingBookingService bookings;

    public AdminPtBookingOutcomeController(PtBookingOutcomeService outcomes, PersonalTrainingBookingService bookings) {
        this.outcomes = outcomes;
        this.bookings = bookings;
    }

    @GetMapping
    public java.util.List<PersonalTrainingBookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bookings.allForAdmin(jwt);
    }

    @GetMapping("/{id}/outcome")
    public PtBookingOutcomeResponse get(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return outcomes.getForAdmin(jwt, id);
    }

    @PatchMapping("/{id}/outcome")
    public PtBookingOutcomeResponse correct(@PathVariable Integer id,
            @Valid @RequestBody PtBookingOutcomeRequest request, @AuthenticationPrincipal Jwt jwt) {
        return outcomes.correctAsAdmin(jwt, id, request.outcome(), request.reason());
    }
}
