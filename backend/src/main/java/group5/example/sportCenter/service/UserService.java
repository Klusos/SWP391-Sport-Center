package group5.example.sportCenter.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.UserResponse;
import group5.example.sportCenter.dto.ChangePasswordRequest;
import group5.example.sportCenter.dto.CreateUserRequest;
import group5.example.sportCenter.dto.UpdateUserRequest;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAges(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    public UserResponse createUser(CreateUserRequest request) {

    if (userRepository.existsByEmail(request.getEmail())) {
        throw new IllegalArgumentException("Email already exists");
    }

    User user = new User();

    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setAges(request.getAges());
    user.setDateOfBirth(request.getDateOfBirth());
    user.setGender(request.getGender());
    user.setPhone(request.getPhone());

    user.setPasswordHash(
            passwordEncoder.encode(request.getPassword())
    );

    user.setRole(request.getRole());
    user.setStatus("ACTIVE");

    User savedUser = userRepository.save(user);

    return toResponse(savedUser);
    }

    public UserResponse updateUser(Integer id, UpdateUserRequest request) {

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
        user.setAges(request.getAges());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());

        User updatedUser = userRepository.save(user);

        return toResponse(updatedUser);
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

    public UserResponse getUserById(Integer id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with id: " + id));

        return toResponse(user);
    }
}