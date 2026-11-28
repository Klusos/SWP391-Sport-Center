package group5.example.sportCenter.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class VietnamesePhoneNumbers {
    public static final String MESSAGE = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.";

    private VietnamesePhoneNumbers() {}

    public static boolean isValid(String value) {
        return value != null && value.matches("^0[0-9]{9}$");
    }

    public static String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (!isValid(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MESSAGE);
        }
        return normalized;
    }
}
