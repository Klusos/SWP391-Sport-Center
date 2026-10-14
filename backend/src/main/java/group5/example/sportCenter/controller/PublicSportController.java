package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.PublicSportOptionResponse;
import group5.example.sportCenter.service.SportService;

@RestController
@RequestMapping("/api/public/sports")
public class PublicSportController {
    private final SportService sportService;

    public PublicSportController(SportService sportService) {
        this.sportService = sportService;
    }

    @GetMapping
    public List<PublicSportOptionResponse> getActiveSports() {
        return sportService.getActiveSportOptions();
    }
}
