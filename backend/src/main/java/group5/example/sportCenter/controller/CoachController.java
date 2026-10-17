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
import group5.example.sportCenter.dto.UpdateCoachProfileRequest;
import group5.example.sportCenter.service.CoachService;
import group5.example.sportCenter.service.CoachSportService;
import group5.example.sportCenter.service.EntityImageService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/coaches")
public class CoachController {
    
    private final CoachService coachService;
    private final CoachSportService coachSportService;
    private final EntityImageService imageService;
    
    public CoachController(
            CoachService coachService,
            CoachSportService coachSportService,
            EntityImageService imageService) {

        this.coachService = coachService;
        this.coachSportService = coachSportService;
        this.imageService = imageService;
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

    @PutMapping("/{id}/profile")
    public CoachResponse updateProfile(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCoachProfileRequest request) {
        return coachService.updateProfile(id, request);
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

    @PutMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public CoachResponse replaceImage(@PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        imageService.replaceCoachImage(id, file);
        return coachService.getCoachById(id);
    }
}
