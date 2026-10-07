package org.example.common.exception;

/** Invalid input that Bean Validation can't express (e.g. an unknown sort field). Mapped to 400 with a field error. */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
