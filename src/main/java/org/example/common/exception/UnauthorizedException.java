package org.example.common.exception;

/** Credentials were missing or wrong (e.g. a failed login). Mapped to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
