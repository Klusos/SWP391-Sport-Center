package group5.example.sportCenter.dto;

import group5.example.sportCenter.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreateMembershipUpgradePaymentRequest(
        @NotNull Integer targetPlanId,
        @NotNull PaymentMethod paymentMethod) {}
