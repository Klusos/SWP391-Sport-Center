package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CreateSportRequest;
import group5.example.sportCenter.dto.SportResponse;
import group5.example.sportCenter.dto.UpdateSportRequest;
import group5.example.sportCenter.service.SportService;
import group5.example.sportCenter.service.EntityImageService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/sports")
public class SportController {

    private final SportService sportService;
    private final EntityImageService imageService;

    public SportController(SportService sportService, EntityImageService imageService) {
        this.sportService = sportService;
        this.imageService = imageService;
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

    @PutMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public SportResponse replaceImage(@PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        imageService.replaceSportImage(id, file);
        return sportService.getSportById(id);
    }
}
