package org.example.shortener.dto;

import org.example.shortener.ShortLink;

import java.time.Instant;

public record ShortLinkStatsResponse(
        String originalUrl,
        long visitCount,
        Instant createdAt,
        Instant expiresAt
) {
    public static ShortLinkStatsResponse from(ShortLink link) {
        return new ShortLinkStatsResponse(link.getOriginalUrl(), link.getVisitCount(),
                link.getCreatedAt(), link.getExpiresAt());
    }
}
