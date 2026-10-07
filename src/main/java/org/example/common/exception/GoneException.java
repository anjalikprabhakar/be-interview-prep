package org.example.common.exception;

/** The resource existed but is no longer available (e.g. an expired short link). Mapped to 410. */
public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }
}
