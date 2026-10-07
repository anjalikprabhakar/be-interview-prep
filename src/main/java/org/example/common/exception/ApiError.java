package org.example.common.exception;

import java.time.Instant;
import java.util.List;

/** The single JSON error shape returned by every endpoint. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
