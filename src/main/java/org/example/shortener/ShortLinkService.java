package org.example.shortener;

import org.example.shortener.dto.ShortLinkResponse;
import org.example.shortener.dto.ShortenRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);
    static final int MAX_ATTEMPTS = 5;

    private final ShortLinkRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final String baseUrl;

    public ShortLinkService(ShortLinkRepository repository, ShortCodeGenerator codeGenerator,
                            @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.baseUrl = baseUrl;
    }

    /**
     * Always creates a new code, even for a URL shortened before, so each link has its own expiry and stats.
     * Deliberately not @Transactional: each saveAndFlush runs in its own transaction, so a code collision
     * (UNIQUE constraint) only rolls back that one attempt and the next attempt can still commit.
     */
    public ShortLinkResponse shorten(ShortenRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            try {
                ShortLink saved = repository.saveAndFlush(new ShortLink(code, request.url(), request.expiresAt()));
                return toResponse(saved);
            } catch (DataIntegrityViolationException e) {
                log.warn("Short code collision on attempt {}, retrying", attempt);
            }
        }
        throw new IllegalStateException("Could not generate a unique short code after " + MAX_ATTEMPTS + " attempts");
    }

    private ShortLinkResponse toResponse(ShortLink link) {
        return new ShortLinkResponse(link.getCode(), baseUrl + "/" + link.getCode(),
                link.getOriginalUrl(), link.getExpiresAt());
    }
}
