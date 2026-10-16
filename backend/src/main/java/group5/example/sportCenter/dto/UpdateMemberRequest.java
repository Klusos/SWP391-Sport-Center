package group5.example.sportCenter.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Member account fields that administrators may edit without changing role, status, or credentials. */
@Getter
@Setter
public class UpdateMemberRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 150)
    private String email;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 1)
    private String gender;

    @Size(max = 20)
    private String phone;
}
