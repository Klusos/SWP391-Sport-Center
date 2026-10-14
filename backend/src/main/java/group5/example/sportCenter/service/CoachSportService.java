package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.AssignCoachSportRequest;
import group5.example.sportCenter.dto.CoachSportResponse;
import group5.example.sportCenter.dto.UpdateCoachSportRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.SportRepository;

@Service
@Transactional(readOnly = true)
public class CoachSportService {

    private final CoachSportRepository coachSportRepository;
    private final CoachRepository coachRepository;
    private final SportRepository sportRepository;

    public CoachSportService(
            CoachSportRepository coachSportRepository,
            CoachRepository coachRepository,
            SportRepository sportRepository) {

        this.coachSportRepository = coachSportRepository;
        this.coachRepository = coachRepository;
        this.sportRepository = sportRepository;
    }

    @Transactional
    public CoachSportResponse assignSport(
            Integer coachId,
            AssignCoachSportRequest request) {

        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Coach not found with id: " + coachId));

        Sport sport = sportRepository.findById(request.getSportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sport not found with id: "
                                        + request.getSportId()));

        if (coachSportRepository.existsByCoachIdAndSportId(
                coachId,
                request.getSportId())) {

            throw new IllegalArgumentException(
                    "Coach is already assigned to this sport");
        }

        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            throw new IllegalArgumentException(
                    "Inactive coach cannot be assigned to a sport");
        }

        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot assign an inactive sport");
        }

        CoachSport coachSport = new CoachSport();

        coachSport.setCoach(coach);
        coachSport.setSport(sport);
        coachSport.setExperienceYears(
                request.getExperienceYears());
        coachSport.setCertification(
                request.getCertification());

        return toResponse(
                coachSportRepository.save(coachSport));
    }

    public List<CoachSportResponse> getSportsByCoach(
            Integer coachId) {

        if (!coachRepository.existsById(coachId)) {
            throw new ResourceNotFoundException(
                    "Coach not found with id: " + coachId);
        }

        return coachSportRepository.findByCoachId(coachId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CoachSportResponse updateCoachSport(
            Integer coachId,
            Integer sportId,
            UpdateCoachSportRequest request) {

        CoachSport coachSport =
                coachSportRepository
                        .findByCoachIdAndSportId(
                                coachId,
                                sportId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Sport assignment not found for coach"));

        coachSport.setExperienceYears(
                request.getExperienceYears());

        coachSport.setCertification(
                request.getCertification());

        return toResponse(
                coachSportRepository.save(coachSport));
    }

    private CoachSportResponse toResponse(
            CoachSport coachSport) {

        return new CoachSportResponse(
                coachSport.getId(),

                coachSport.getCoach().getId(),
                coachSport.getCoach().getUser().getName(),

                coachSport.getSport().getId(),
                coachSport.getSport().getName(),

                coachSport.getExperienceYears(),
                coachSport.getCertification()
        );
    }
}
