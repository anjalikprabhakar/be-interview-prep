package org.example.shortener;

import org.example.shortener.dto.ShortLinkResponse;
import org.example.shortener.dto.ShortenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ShortLinkServiceTest {

    private final ShortLinkRepository repository = mock(ShortLinkRepository.class);
    private final ShortCodeGenerator codeGenerator = mock(ShortCodeGenerator.class);
    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkService(repository, codeGenerator, "http://short.test");
    }

    @Test
    void shorten_codeCollision_retriesWithNewCode() {
        given(codeGenerator.generate()).willReturn("TAKEN01", "FREE001");
        given(repository.saveAndFlush(any(ShortLink.class)))
                .willThrow(new DataIntegrityViolationException("duplicate code"))
                .willAnswer(invocation -> invocation.getArgument(0));

        ShortLinkResponse response = service.shorten(new ShortenRequest("https://example.com", null));

        assertThat(response.code()).isEqualTo("FREE001");
        assertThat(response.shortUrl()).isEqualTo("http://short.test/FREE001");
        verify(repository, times(2)).saveAndFlush(any(ShortLink.class));
    }

    @Test
    void shorten_collisionOnEveryAttempt_givesUpAfterMaxAttempts() {
        given(codeGenerator.generate()).willReturn("TAKEN01");
        given(repository.saveAndFlush(any(ShortLink.class)))
                .willThrow(new DataIntegrityViolationException("duplicate code"));

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://example.com", null)))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, times(ShortLinkService.MAX_ATTEMPTS)).saveAndFlush(any(ShortLink.class));
    }
}
