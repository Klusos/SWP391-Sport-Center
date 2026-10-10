package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

import group5.example.sportCenter.dto.ChangePasswordRequest;
import group5.example.sportCenter.dto.CreateUserRequest;
import group5.example.sportCenter.dto.UpdateUserRequest;
import group5.example.sportCenter.dto.UserResponse;
import group5.example.sportCenter.service.UserService;
import group5.example.sportCenter.dto.UserProfileResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        return userService.getAllUsers(jwt);
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.getUserById(id, jwt);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateUserRequest request) {

        return userService.updateUser(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request) {

        return userService.createUser(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
    }

    @PatchMapping("/{id}/password")
    public String changePassword(
            @PathVariable Integer id,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(id, request);

        return "Password changed successfully";
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        return userService.getCurrentUser(jwt);
    }
}
