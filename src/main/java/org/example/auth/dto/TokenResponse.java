package org.example.auth.dto;

/** expiresIn is in seconds, as in the OAuth2 token response. */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
