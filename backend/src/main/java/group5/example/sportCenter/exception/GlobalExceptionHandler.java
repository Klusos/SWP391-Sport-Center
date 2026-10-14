package group5.example.sportCenter.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR = "An unexpected error occurred";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessConflict(IllegalArgumentException exception) {
        return response(HttpStatus.CONFLICT, safeMessage(exception.getMessage(), "The request conflicts with current business rules"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
        }
        if (status.is5xxServerError()) {
            return response(status, INTERNAL_ERROR);
        }
        return response(status, safeMessage(exception.getReason(), status.getReasonPhrase()));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleFrameworkError(ErrorResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
        }
        if (status.is5xxServerError()) {
            return response(status, INTERNAL_ERROR);
        }
        ProblemDetail detail = exception.getBody();
        return response(status, safeMessage(detail.getDetail(), status.getReasonPhrase()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        return validationResponse(exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; ")));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindingFailure(BindException exception) {
        return validationResponse(exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; ")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + exception.getName());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Request validation failed" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body is invalid or malformed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
    }

    private String fieldMessage(FieldError error) {
        String detail = safeMessage(error.getDefaultMessage(), "is invalid");
        return error.getField() + ": " + detail;
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(String message) {
        return response(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Request validation failed" : message);
    }

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(), status.getReasonPhrase(), message, Instant.now()));
    }
}
