package group5.example.sportCenter.service;

import group5.example.sportCenter.dto.LoginRequest;
import group5.example.sportCenter.dto.LoginResponse;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    public LoginResponse login(LoginRequest request) {

        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        // 2. Verify password
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        // 3. Check account status
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive"
            );
        }

        // 4. Generate JWT
        String token = jwtService.generateToken(user);

        // 5. Return login response
        return new LoginResponse(
                token,
                "Bearer",
                Math.toIntExact(Math.multiplyExact(expirationMinutes, 60L)),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}