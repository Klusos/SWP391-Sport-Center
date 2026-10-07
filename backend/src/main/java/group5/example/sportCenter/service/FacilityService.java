package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;

@Service
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final SportRepository sportRepository;

    public FacilityService(
            FacilityRepository facilityRepository,
            SportRepository sportRepository) {

        this.facilityRepository = facilityRepository;
        this.sportRepository = sportRepository;
    }

    public FacilityResponse createFacility(
            CreateFacilityRequest request) {

        Sport sport = sportRepository.findById(request.getSportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sport not found with id: "
                                        + request.getSportId()));

        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot create facility for an inactive sport");
        }

        Facility facility = new Facility();

        facility.setSport(sport);
        facility.setName(request.getName());
        facility.setType(request.getType());
        facility.setStatus("ACTIVE");

        return toResponse(
                facilityRepository.save(facility));
    }

    public List<FacilityResponse> getAllFacilities() {

        return facilityRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FacilityResponse getFacilityById(Integer id) {

        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Facility not found with id: " + id));

        return toResponse(facility);
    }

    public List<FacilityResponse> getFacilitiesBySport(
            Integer sportId) {

        if (!sportRepository.existsById(sportId)) {
            throw new ResourceNotFoundException(
                    "Sport not found with id: " + sportId);
        }

        return facilityRepository.findBySportId(sportId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FacilityResponse updateFacility(
            Integer id,
            UpdateFacilityRequest request) {

        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Facility not found with id: " + id));

        facility.setName(request.getName());
        facility.setType(request.getType());
        facility.setStatus(request.getStatus());

        return toResponse(
                facilityRepository.save(facility));
    }

    private FacilityResponse toResponse(Facility facility) {

        return new FacilityResponse(
                facility.getId(),

                facility.getSport().getId(),
                facility.getSport().getName(),

                facility.getName(),
                facility.getType(),
                facility.getStatus()
        );
    }
}