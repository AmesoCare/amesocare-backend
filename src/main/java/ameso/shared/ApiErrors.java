package ameso.shared;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Ameso.obj("error", error.getReason()));
    }
}
