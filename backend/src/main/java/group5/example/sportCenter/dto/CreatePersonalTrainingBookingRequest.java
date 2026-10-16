package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePersonalTrainingBookingRequest {
    @NotNull
    @Positive
    private Integer coachId;

    @NotNull
    @Positive
    private Integer sportId;

    @NotNull
    private LocalDateTime startTime;
}
