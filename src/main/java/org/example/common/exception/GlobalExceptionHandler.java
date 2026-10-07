package org.example.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid failed on a request body: one entry per invalid field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    /** Body could not be parsed: malformed JSON, or a value of the wrong type (e.g. unknown enum, bad date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            String field = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            return build(HttpStatus.BAD_REQUEST, "Validation failed", request,
                    List.of(new ApiError.FieldError(field, invalidValueMessage(ife.getValue(), ife.getTargetType()))));
        }
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request", request, List.of());
    }

    /** Query or path parameter of the wrong type, e.g. ?status=FOO or /api/tasks/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request,
                List.of(new ApiError.FieldError(ex.getName(), invalidValueMessage(ex.getValue(), ex.getRequiredType()))));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(GoneException.class)
    ResponseEntity<ApiError> handleGone(GoneException ex, HttpServletRequest request) {
        return build(HttpStatus.GONE, ex.getMessage(), request, List.of());
    }

    /**
     * The row changed or disappeared between our read and our write, e.g. two DELETEs (or a DELETE and a PUT)
     * on the same task at the same time. The losing request gets a retryable 409 instead of a 500.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentModification(OptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "The resource was modified or deleted by another request; reload it and retry",
                request, List.of());
    }

    /** Anything else: log the details, but never expose them to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Spring MVC's own exceptions (unknown path 404, wrong method 405, unsupported media type 415...)
        // carry their intended status; keep it instead of turning them into a 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return build(errorResponse.getStatusCode(), errorResponse.getBody().getDetail(), request, List.of());
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, List.of());
    }

    private static String invalidValueMessage(Object value, Class<?> type) {
        if (type != null && type.isEnum()) {
            return "invalid value '" + value + "'; allowed values: " + Arrays.toString(type.getEnumConstants());
        }
        return "invalid value '" + value + "'";
    }

    private static ResponseEntity<ApiError> build(HttpStatusCode status, String message,
                                                  HttpServletRequest request, List<ApiError.FieldError> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
        ApiError body = new ApiError(Instant.now(), status.value(), reason, message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
