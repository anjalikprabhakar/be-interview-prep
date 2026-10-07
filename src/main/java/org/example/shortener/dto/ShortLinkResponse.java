package org.example.shortener.dto;

import java.time.Instant;

public record ShortLinkResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant expiresAt
) {
}
