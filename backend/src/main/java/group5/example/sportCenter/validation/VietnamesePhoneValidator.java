package group5.example.sportCenter.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class VietnamesePhoneValidator implements ConstraintValidator<VietnamesePhone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) return true;
        return VietnamesePhoneNumbers.isValid(value.trim());
    }
}
