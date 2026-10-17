package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.service.FacilityService;
import group5.example.sportCenter.service.EntityImageService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;
    private final EntityImageService imageService;

    public FacilityController(FacilityService facilityService, EntityImageService imageService) {
        this.facilityService = facilityService;
        this.imageService = imageService;
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

    @PutMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public FacilityResponse replaceImage(@PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        imageService.replaceFacilityImage(id, file);
        return facilityService.getFacilityById(id);
    }
}
