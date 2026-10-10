package group5.example.sportCenter.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.UserProfileResponse;
import group5.example.sportCenter.dto.UserResponse;
import group5.example.sportCenter.dto.ChangePasswordRequest;
import group5.example.sportCenter.dto.CreateUserRequest;
import group5.example.sportCenter.dto.UpdateUserRequest;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN", "RECEPTIONIST", "MEMBER", "COACH");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> getAllUsers(Jwt callerJwt) {
        User actor = getActiveActor(callerJwt);
        String role = actor.getRole().toUpperCase(Locale.ROOT);

        if (role.equals("ADMIN")) {
            return userRepository.findAll().stream().map(this::toResponse).toList();
        }
        if (role.equals("RECEPTIONIST")) {
            java.util.ArrayList<UserResponse> visibleUsers = new java.util.ArrayList<>();
            visibleUsers.addAll(userRepository.findByRole("MEMBER").stream().map(this::toResponse).toList());
            visibleUsers.addAll(userRepository.findByRole("COACH").stream().map(this::toResponse).toList());
            return List.copyOf(visibleUsers);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can list users");
    }

    private User getActiveActor(Jwt jwt) {
        final Integer userId;
        try {
            userId = Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }
        return actor;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    public UserResponse createUser(CreateUserRequest request) {

    String role = normalizeRole(request.getRole());

    if (userRepository.existsByEmail(request.getEmail())) {
        throw new IllegalArgumentException("Email already exists");
    }

    User user = new User();

    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setDateOfBirth(request.getDateOfBirth());
    user.setGender(request.getGender());
    user.setPhone(request.getPhone());

    user.setPasswordHash(
            passwordEncoder.encode(request.getPassword())
    );

    user.setRole(role);
    user.setStatus("ACTIVE");

    User savedUser = userRepository.save(user);

    return toResponse(savedUser);
    }

    public UserResponse updateUser(Integer id, UpdateUserRequest request) {

        String role = normalizeRole(request.getRole());

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {

            throw new IllegalArgumentException("Email already exists");
        }

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setPhone(request.getPhone());
        user.setRole(role);
        user.setStatus(request.getStatus());

        User updatedUser = userRepository.save(user);

        return toResponse(updatedUser);
    }

    private String normalizeRole(String role) {

        if (role == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role is required");
        }

        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role must be one of ADMIN, RECEPTIONIST, MEMBER, COACH");
        }

        return normalizedRole;
    }

    public void deleteUser(Integer id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        userRepository.delete(user);
    }

    public void changePassword(
            Integer userId,
            ChangePasswordRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId));

        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect");
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "New password must be different from current password");
        }

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getNewPassword())
        );

        userRepository.save(user);
    }

    public UserResponse getUserById(Integer id, Jwt callerJwt) {
        User actor = getActiveActor(callerJwt);
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with id: " + id));

        String actorRole = actor.getRole().toUpperCase(Locale.ROOT);
        if (actorRole.equals("ADMIN")) {
            return toResponse(user);
        }
        if (actorRole.equals("RECEPTIONIST")) {
            String targetRole = user.getRole().toUpperCase(Locale.ROOT);
            if (targetRole.equals("MEMBER") || targetRole.equals("COACH")) {
                return toResponse(user);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "RECEPTIONIST can only view MEMBER or COACH users");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can view users by ID");

    }

    public UserProfileResponse getCurrentUser(Jwt jwt) {

        Integer userId;

        try {
                userId = Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid token subject"
                );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User no longer exists"
                ));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Account is inactive"
                );
        }

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getPhone(),
                user.getRole(),
                user.getStatus()
        );
        }
}
