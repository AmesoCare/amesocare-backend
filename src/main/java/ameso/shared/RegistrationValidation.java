package ameso.shared;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RegistrationValidation {
    private RegistrationValidation() {}
    public static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    public static String deviceId(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw invalid("deviceId must be a UUID");
        return UUID.fromString(value).toString();
    }
    public static String text(String value, String label, boolean required) {
        if (value != null && value.length() > 200) throw invalid(label + " must be at most 200 characters");
        var trimmed = value == null ? "" : value.strip();
        if (required && trimmed.isBlank()) throw invalid(label + " is required");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
