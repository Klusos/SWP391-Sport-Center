package group5.example.sportCenter.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Integer id,
        String name,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String role,
        String status
) {}