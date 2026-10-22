package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectRenewalPaymentMethodRequest {
    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

}
