package group5.example.sportCenter.dto;

import java.time.LocalDate;

public record MemberResponse(
        Integer id,
        String name,
        String email,
        LocalDate dateOfBirth,
        String phone,
        String gender,
        String role,
        String status
) {}