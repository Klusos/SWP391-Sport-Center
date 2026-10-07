package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.AssignCoachSportRequest;
import group5.example.sportCenter.dto.CoachResponse;
import group5.example.sportCenter.dto.CoachSportResponse;
import group5.example.sportCenter.dto.CreateCoachRequest;
import group5.example.sportCenter.dto.UpdateCoachSportRequest;
import group5.example.sportCenter.dto.UpdateCoachStatusRequest;
import group5.example.sportCenter.service.CoachService;
import group5.example.sportCenter.service.CoachSportService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coaches")
public class CoachController {
    
    private final CoachService coachService;
    private final CoachSportService coachSportService;
    
    public CoachController(
            CoachService coachService,
            CoachSportService coachSportService) {

        this.coachService = coachService;
        this.coachSportService = coachSportService;
    }

    @GetMapping
    public List<CoachResponse> getAllCoaches() {
        return coachService.getAllCoaches();
    }

    @GetMapping("/{id}")
    public CoachResponse getCoachById(
            @PathVariable Integer id) {

        return coachService.getCoachById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoachResponse createCoach(
            @Valid @RequestBody CreateCoachRequest request) {

        return coachService.createCoach(request);
    }

    @PatchMapping("/{id}/status")
    public CoachResponse updateCoachStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCoachStatusRequest request) {

        return coachService.updateCoachStatus(
                id,
                request.getStatus());
    }

    @PostMapping("/{coachId}/sports")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachSportResponse assignSport(
            @PathVariable Integer coachId,
            @Valid @RequestBody AssignCoachSportRequest request) {

        return coachSportService.assignSport(
                coachId,
                request);
    }

    @GetMapping("/{coachId}/sports")
    public List<CoachSportResponse> getSportsByCoach(
            @PathVariable Integer coachId) {

        return coachSportService.getSportsByCoach(coachId);
    }

    @PutMapping("/{coachId}/sports/{sportId}")
    public CoachSportResponse updateCoachSport(
            @PathVariable Integer coachId,
            @PathVariable Integer sportId,
            @Valid @RequestBody UpdateCoachSportRequest request) {

        return coachSportService.updateCoachSport(
                coachId,
                sportId,
                request);
    }
}