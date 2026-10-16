package group5.example.sportCenter.service;

import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CatalogValues {
    private CatalogValues() {}

    static String status(String value, String field, List<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " status must be one of " + String.join(", ", allowed));
        }
        return normalized;
    }

    static String courseLevel(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Course level must be BEGINNER, INTERMEDIATE, or ADVANCED");
        }
        return normalized;
    }
}
