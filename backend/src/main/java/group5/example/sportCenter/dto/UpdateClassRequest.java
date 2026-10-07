package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateClassRequest {

    @NotNull(message = "Coach ID is required")
    private Integer coachId;

    @NotBlank(message = "Class name is required")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotBlank(message = "Status is required")
    private String status;
}