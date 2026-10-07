package org.example.common.exception;

/** The request clashes with existing state, e.g. an email that is already registered. Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
