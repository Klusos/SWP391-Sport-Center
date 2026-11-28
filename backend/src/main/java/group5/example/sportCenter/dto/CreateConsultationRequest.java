package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import jakarta.validation.constraints.Past;
import group5.example.sportCenter.validation.VietnamesePhone;

public record CreateConsultationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) @VietnamesePhone String phone,
        @NotBlank(message = "Vui lòng nhập địa chỉ email hợp lệ để đăng ký tư vấn.")
        @Email(message = "Vui lòng nhập địa chỉ email hợp lệ để đăng ký tư vấn.")
        @Size(max = 150) String email,
        @NotNull @Positive Integer sportId,
        @Min(1) @Max(120) Integer age,
        String experience,
        String note,
        @Past LocalDate dateOfBirth) {

    public CreateConsultationRequest {
        phone = phone == null ? null : phone.trim();
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Compatibility constructor for existing callers while old clients are retired. */
    public CreateConsultationRequest(String name, String phone, String email, Integer sportId,
            Integer age, String experience, String note) {
        this(name, phone, email, sportId, age, experience, note, null);
    }
}
