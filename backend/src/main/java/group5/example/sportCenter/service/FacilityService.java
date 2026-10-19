package group5.example.sportCenter.service;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateFacilityRequest;
import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.PublicFacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.dto.FacilitySportResponse;
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
        List<Sport> sports = resolveActiveSports(request.getSportIds(), request.getSportId());

        Facility facility = new Facility();
        facility.setSports(new LinkedHashSet<>(sports));
        facility.setSport(sports.getFirst());
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
        return facilityRepository.findDistinctByStatusAndSports_Status("ACTIVE", "ACTIVE").stream()
                .map(facility -> {
                    List<FacilitySportResponse> activeSports = facility.getSports().stream()
                            .filter(sport -> "ACTIVE".equalsIgnoreCase(sport.getStatus()))
                            .sorted(Comparator.comparing(Sport::getId))
                            .map(sport -> new FacilitySportResponse(sport.getId(), sport.getName(), sport.getStatus())).toList();
                    FacilitySportResponse primary = activeSports.getFirst();
                    return new PublicFacilityResponse(facility.getId(), primary.id(), primary.name(),
                            facility.getName(), facility.getType(), facility.getImageUrl(), activeSports);
                })
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

        return facilityRepository.findDistinctBySports_Id(sportId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FacilityResponse updateFacility(
            Integer id,
            UpdateFacilityRequest request) {

        String status = normalizeStatus(request.getStatus());

        Facility facility = facilityRepository.findByIdForScheduleUpdate(id)
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

        List<Integer> requestedSportIds = request.getSportIds();
        if (requestedSportIds == null && request.getSportId() != null) {
            requestedSportIds = List.of(request.getSportId());
        }
        if (requestedSportIds != null) {
            Set<Integer> currentIds = facility.getSports().stream().map(Sport::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<Sport> requestedSports = resolveSports(requestedSportIds, currentIds);
            Set<Integer> requestedIds = requestedSports.stream().map(Sport::getId)
                    .collect(java.util.stream.Collectors.toSet());
            for (Integer removedId : currentIds) {
                if (!requestedIds.contains(removedId)
                        && classSessionRepository.existsFutureNonCancelledByFacilityAndCourseSport(
                                facility.getId(), removedId, "CANCELLED", now)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Sport assignment cannot be removed while a future or ongoing non-cancelled ClassSession uses this Facility for that Sport");
                }
            }
            facility.getSports().clear();
            facility.getSports().addAll(requestedSports);
            facility.setSport(requestedSports.getFirst());
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
                facility.getSports().stream().sorted(Comparator.comparing(Sport::getId))
                        .map(sport -> new FacilitySportResponse(sport.getId(), sport.getName(), sport.getStatus())).toList(),

                facility.getName(),
                facility.getType(),
                facility.getStatus(),
                facility.getImageUrl()
        );
    }

    private List<Sport> resolveActiveSports(List<Integer> requestedIds, Integer legacySportId) {
        List<Integer> ids = requestedIds == null ? new ArrayList<>() : new ArrayList<>(requestedIds);
        if (ids.isEmpty() && legacySportId != null) ids.add(legacySportId);
        return resolveSports(ids, Set.of());
    }

    private List<Sport> resolveSports(List<Integer> requestedIds, Set<Integer> retainedSportIds) {
        List<Integer> ids = requestedIds == null ? new ArrayList<>() : new ArrayList<>(requestedIds);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one Sport must be assigned");
        }
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate Sport assignments are not allowed");
        }
        List<Sport> sports = ids.stream().map(sportId -> sportRepository.findById(sportId)
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found with id: " + sportId))).toList();
        if (sports.stream().anyMatch(sport -> !"ACTIVE".equalsIgnoreCase(sport.getStatus())
                && !retainedSportIds.contains(sport.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ACTIVE Sports can be assigned to a Facility");
        }
        return sports;
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
