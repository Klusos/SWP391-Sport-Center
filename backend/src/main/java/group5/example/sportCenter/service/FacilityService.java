package group5.example.sportCenter.service;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.PublicFacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.SportRepository;

@Service
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final SportRepository sportRepository;
    private final ClassSessionRepository classSessionRepository;
    private final Clock clock;

    @Autowired
    public FacilityService(
            FacilityRepository facilityRepository,
            SportRepository sportRepository,
            ClassSessionRepository classSessionRepository) {
        this(facilityRepository, sportRepository, classSessionRepository, Clock.systemDefaultZone());
    }

    FacilityService(
            FacilityRepository facilityRepository,
            SportRepository sportRepository,
            ClassSessionRepository classSessionRepository,
            Clock clock) {

        this.facilityRepository = facilityRepository;
        this.sportRepository = sportRepository;
        this.classSessionRepository = classSessionRepository;
        this.clock = clock;
    }

    @Transactional
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

    public List<PublicFacilityResponse> getPublicActiveFacilities() {
        return facilityRepository.findByStatusAndSportStatus("ACTIVE", "ACTIVE").stream()
                .map(facility -> new PublicFacilityResponse(facility.getId(), facility.getSport().getId(),
                        facility.getSport().getName(), facility.getName(), facility.getType(), facility.getImageUrl()))
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

    @Transactional
    public FacilityResponse updateFacility(
            Integer id,
            UpdateFacilityRequest request) {

        String status = normalizeStatus(request.getStatus());

        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Facility not found with id: " + id));

        boolean deactivating = "ACTIVE".equalsIgnoreCase(facility.getStatus())
                && "INACTIVE".equals(status);
        LocalDateTime now = LocalDateTime.now(clock);
        if (deactivating && classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(
                facility.getId(), "SCHEDULED", now)) {
            throw new IllegalArgumentException(
                    "Facility cannot be set to INACTIVE while an in-progress or upcoming SCHEDULED ClassSession uses it");
        }

        facility.setName(request.getName());
        facility.setType(request.getType());
        facility.setStatus(status);

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
                facility.getStatus(),
                facility.getImageUrl()
        );
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Facility status must be ACTIVE or INACTIVE");
        }
        return normalized;
    }
}
