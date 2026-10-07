package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CreateSportRequest;
import group5.example.sportCenter.dto.SportResponse;
import group5.example.sportCenter.dto.UpdateSportRequest;
import group5.example.sportCenter.service.SportService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sports")
public class SportController {

    private final SportService sportService;

    public SportController(SportService sportService) {
        this.sportService = sportService;
    }

    @GetMapping
    public List<SportResponse> getAllSports() {
        return sportService.getAllSports();
    }

    @GetMapping("/{id}")
    public SportResponse getSportById(
            @PathVariable Integer id) {

        return sportService.getSportById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SportResponse createSport(
            @Valid @RequestBody CreateSportRequest request) {

        return sportService.createSport(request);
    }

    @PutMapping("/{id}")
    public SportResponse updateSport(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateSportRequest request) {

        return sportService.updateSport(id, request);
    }
}