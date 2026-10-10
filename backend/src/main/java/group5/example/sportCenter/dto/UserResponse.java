package group5.example.sportCenter.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserResponse {

    private Integer id;
    private String name;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime createdAt;
}