package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CreateSportRequest;
import group5.example.sportCenter.dto.SportResponse;
import group5.example.sportCenter.dto.UpdateSportRequest;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.SportRepository;

@Service
public class SportService {

    private final SportRepository sportRepository;

    public SportService(SportRepository sportRepository) {
        this.sportRepository = sportRepository;
    }

    public List<SportResponse> getAllSports() {
        return sportRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SportResponse getSportById(Integer id) {

        Sport sport = sportRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sport not found with id: " + id));

        return toResponse(sport);
    }

    public SportResponse createSport(
            CreateSportRequest request) {

        if (sportRepository
                .existsByNameIgnoreCase(request.getName())) {

            throw new IllegalArgumentException(
                    "Sport name already exists");
        }

        Sport sport = new Sport();

        sport.setName(request.getName());
        sport.setDescription(request.getDescription());
        sport.setStatus("ACTIVE");

        Sport savedSport =
                sportRepository.save(sport);

        return toResponse(savedSport);
    }

    public SportResponse updateSport(
            Integer id,
            UpdateSportRequest request) {

        Sport sport = sportRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sport not found with id: " + id));

        if (!sport.getName()
                .equalsIgnoreCase(request.getName())
                && sportRepository
                    .existsByNameIgnoreCase(request.getName())) {

            throw new IllegalArgumentException(
                    "Sport name already exists");
        }

        sport.setName(request.getName());
        sport.setDescription(request.getDescription());
        sport.setStatus(request.getStatus());

        Sport updatedSport =
                sportRepository.save(sport);

        return toResponse(updatedSport);
    }

    private SportResponse toResponse(Sport sport) {

        return new SportResponse(
                sport.getId(),
                sport.getName(),
                sport.getDescription(),
                sport.getStatus()
        );
    }
}