package group5.example.sportCenter.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import group5.example.sportCenter.dto.CoachResponse;
import group5.example.sportCenter.dto.PublicCoachResponse;
import group5.example.sportCenter.dto.UpdateCoachProfileRequest;
import group5.example.sportCenter.dto.CreateCoachRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class CoachService {

    private final PasswordEncoder passwordEncoder;
    private final CoachRepository coachRepository;
    private final CoachSportRepository coachSportRepository;
    private final UserRepository userRepository;

    public CoachService(
            CoachRepository coachRepository,
            CoachSportRepository coachSportRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.coachRepository = coachRepository;
        this.coachSportRepository = coachSportRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<CoachResponse> getAllCoaches() {

        return coachRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PublicCoachResponse> getPublicActiveCoaches() {
        var coaches = coachRepository.findByStatusAndUserStatus("ACTIVE", "ACTIVE");
        if (coaches.isEmpty()) {
            return List.of();
        }
        var coachIds = coaches.stream().map(Coach::getId).toList();
        var assignments = coachSportRepository.findByCoachIdInAndSportStatus(coachIds, "ACTIVE");
        var sportsByCoach = assignments.stream().collect(java.util.stream.Collectors.groupingBy(
                assignment -> assignment.getCoach().getId(),
                java.util.stream.Collectors.mapping(assignment -> new PublicCoachResponse.PublicCoachSport(
                        assignment.getSport().getId(), assignment.getSport().getName(),
                        assignment.getExperienceYears(), assignment.getCertification()),
                        java.util.stream.Collectors.toList())));
        return coaches.stream()
                .map(coach -> new PublicCoachResponse(coach.getId(), coach.getUser().getName(),
                        coach.getImageUrl(), coach.getBio(),
                        sportsByCoach.getOrDefault(coach.getId(), List.of())))
                .toList();
    }

    public CoachResponse getCoachById(Integer id) {

        Coach coach = coachRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Coach not found with id: " + id));

        return toResponse(coach);
    }

    @Transactional
    public CoachResponse createCoach(CreateCoachRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already exists");
        }

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());

        user.setRole("COACH");
        user.setStatus("ACTIVE");

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        User savedUser = userRepository.save(user);

        Coach coach = new Coach();

        coach.setUser(savedUser);
        coach.setStatus("ACTIVE");
        coach.setBio(normalizeBio(request.getBio()));

        Coach savedCoach = coachRepository.save(coach);

        return toResponse(savedCoach);
    }

    @Transactional
    public CoachResponse updateProfile(Integer id, UpdateCoachProfileRequest request) {
        Coach coach = coachRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coach not found with id: " + id));
        coach.setBio(normalizeBio(request.getBio()));
        return toResponse(coachRepository.save(coach));
    }

    private String normalizeBio(String bio) {
        if (bio == null) return null;
        String normalized = bio.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Transactional
    public CoachResponse updateCoachStatus(
            Integer id,
            String requestedStatus) {

        Coach coach = coachRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Coach not found with id: " + id));

        String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or INACTIVE");
        }

        coach.setStatus(status);
        coach.getUser().setStatus(status);
        userRepository.save(coach.getUser());

        return toResponse(coachRepository.save(coach));
    }

    private CoachResponse toResponse(Coach coach) {

        return new CoachResponse(
                coach.getId(),
                coach.getUser().getId(),
                coach.getUser().getName(),
                coach.getUser().getEmail(),
                coach.getStatus(),
                coach.getImageUrl(),
                coach.getBio()
        );
    }
}
