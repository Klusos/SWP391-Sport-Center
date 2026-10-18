package group5.example.sportCenter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CounterPaymentRequest {

    @NotNull(message = "Success flag is required")
    private Boolean success;

    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
