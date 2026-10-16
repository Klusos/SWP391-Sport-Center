package group5.example.sportCenter.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStaffRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Email @Size(max = 150)
    private String email;
    @Past
    private LocalDate dateOfBirth;
    @Size(max = 1)
    private String gender;
    @Size(max = 20)
    private String phone;
}
