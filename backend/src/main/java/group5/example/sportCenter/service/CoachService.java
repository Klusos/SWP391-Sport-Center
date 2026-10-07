package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import group5.example.sportCenter.dto.CoachResponse;
import group5.example.sportCenter.dto.CreateCoachRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class CoachService {

    private final PasswordEncoder passwordEncoder;
    private final CoachRepository coachRepository;
    private final UserRepository userRepository;

    public CoachService(
            CoachRepository coachRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.coachRepository = coachRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<CoachResponse> getAllCoaches() {

        return coachRepository.findAll()
                .stream()
                .map(this::toResponse)
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

        Coach savedCoach = coachRepository.save(coach);

        return toResponse(savedCoach);
    }

    public CoachResponse updateCoachStatus(
            Integer id,
            String status) {

        Coach coach = coachRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Coach not found with id: " + id));

        coach.setStatus(status);

        return toResponse(coachRepository.save(coach));
    }

    private CoachResponse toResponse(Coach coach) {

        return new CoachResponse(
                coach.getId(),
                coach.getUser().getId(),
                coach.getUser().getName(),
                coach.getUser().getEmail(),
                coach.getStatus()
        );
    }
}