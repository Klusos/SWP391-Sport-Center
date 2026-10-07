package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.service.FacilityService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityResponse createFacility(
            @Valid @RequestBody CreateFacilityRequest request) {

        return facilityService.createFacility(request);
    }

    @GetMapping
    public List<FacilityResponse> getAllFacilities() {
        return facilityService.getAllFacilities();
    }

    @GetMapping("/{id}")
    public FacilityResponse getFacilityById(
            @PathVariable Integer id) {

        return facilityService.getFacilityById(id);
    }

    @GetMapping("/sport/{sportId}")
    public List<FacilityResponse> getFacilitiesBySport(
            @PathVariable Integer sportId) {

        return facilityService
                .getFacilitiesBySport(sportId);
    }

    @PutMapping("/{id}")
    public FacilityResponse updateFacility(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateFacilityRequest request) {

        return facilityService
                .updateFacility(id, request);
    }
}