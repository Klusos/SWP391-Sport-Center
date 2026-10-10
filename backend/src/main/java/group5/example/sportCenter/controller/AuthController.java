package group5.example.sportCenter.controller;

import group5.example.sportCenter.dto.LoginRequest;
import group5.example.sportCenter.dto.LoginResponse;
import group5.example.sportCenter.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}