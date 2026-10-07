package org.example.shortener;

import org.example.shortener.dto.ShortLinkResponse;
import org.example.shortener.dto.ShortenRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortLinkController.class)
class ShortLinkControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ShortLinkService service;

    @Test
    void shorten_validUrl_returns201WithLocationAndShortUrl() throws Exception {
        given(service.shorten(any(ShortenRequest.class))).willReturn(new ShortLinkResponse(
                "aB3dE5f", "http://localhost:8080/aB3dE5f", "https://example.com/page", null));

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "https://example.com/page"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/urls/aB3dE5f/stats")))
                .andExpect(jsonPath("$.code").value("aB3dE5f"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB3dE5f"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/page"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "example.com", "/relative/path", "javascript:alert(1)",
            "ftp://example.com/file", "http://", "mailto:someone@example.com"})
    void shorten_invalidUrl_returns400WithFieldError(String url) throws Exception {
        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "%s"}
                        """.formatted(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/urls"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a valid http or https URL"));
        verifyNoInteractions(service);
    }

    @Test
    void shorten_missingUrl_returns400WithFieldError() throws Exception {
        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("url is required"));
        verifyNoInteractions(service);
    }

    @Test
    void shorten_pastExpiry_returns400WithFieldError() throws Exception {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "https://example.com", "expiresAt": "%s"}
                        """.formatted(yesterday)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expiresAt"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("expiresAt must be in the future"));
        verifyNoInteractions(service);
    }

    @Test
    void redirect_pathNotMatchingCodeFormat_isNotRoutedToRedirect() throws Exception {
        // 9 characters: longer than any code, so the root mapping must not catch it.
        mvc.perform(get("/abcdefghi"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
