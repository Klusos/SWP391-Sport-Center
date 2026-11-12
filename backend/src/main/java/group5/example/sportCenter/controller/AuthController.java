package group5.example.sportCenter.controller;

import group5.example.sportCenter.dto.LoginRequest;
import group5.example.sportCenter.dto.LoginResponse;
import group5.example.sportCenter.service.AuthService;
import group5.example.sportCenter.service.JwtRevocationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(AuthService authService, JwtRevocationService jwtRevocationService) {
        this.authService = authService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        jwtRevocationService.revoke(jwt);
    }
}
