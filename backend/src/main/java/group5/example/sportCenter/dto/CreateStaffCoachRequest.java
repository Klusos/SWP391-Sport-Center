package group5.example.sportCenter.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStaffCoachRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @jakarta.validation.constraints.Email @Size(max = 150)
    private String email;
    @Size(max = 20)
    private String phone;
    @Size(max = 1)
    private String gender;
    @NotBlank @Size(min = 6)
    private String password;
    @NotEmpty
    private List<@Valid Specialization> specializations;

    @Getter
    @Setter
    public static class Specialization {
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Positive
        private Integer sportId;
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Min(0)
        private Integer experienceYears;
        @Size(max = 255)
        private String certification;
    }
}
