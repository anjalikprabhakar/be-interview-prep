package org.example.shortener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against the real (H2) database: controller → service → JPA → Flyway schema. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ShortLinkApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ShortLinkRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shorten_sameUrlTwice_returnsDifferentCodes() throws Exception {
        String first = shorten("https://example.com/same").get("code").asText();
        String second = shorten("https://example.com/same").get("code").asText();

        assertThat(first).matches("[A-Za-z0-9]{7}");
        assertThat(second).isNotEqualTo(first);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void redirect_knownCode_returns302WithLocationAndCountsVisit() throws Exception {
        JsonNode created = shorten("https://example.com/page?q=1");
        String code = created.get("code").asText();
        assertThat(created.get("shortUrl").asText()).isEqualTo("http://localhost:8080/" + code);

        mvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page?q=1"));
        mvc.perform(get("/{code}", code))
                .andExpect(status().isFound());

        mvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/page?q=1"))
                .andExpect(jsonPath("$.visitCount").value(2))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        mvc.perform(get("/nope123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short link nope123 not found"))
                .andExpect(jsonPath("$.path").value("/nope123"));
    }

    @Test
    void redirect_expiredCode_returns410AndDoesNotCountVisit() throws Exception {
        // The API rejects past expiries, so an expired link can only be set up directly in the DB.
        repository.saveAndFlush(new ShortLink("expired", "https://example.com",
                Instant.now().minus(1, ChronoUnit.HOURS)));

        mvc.perform(get("/expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"))
                .andExpect(jsonPath("$.message").value("Short link expired has expired"));

        assertThat(repository.findByCode("expired")).get()
                .extracting(ShortLink::getVisitCount).isEqualTo(0L);
    }

    @Test
    void stats_expiredCode_returns200WithStats() throws Exception {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        repository.saveAndFlush(new ShortLink("old1234", "https://example.com/old", expiresAt));

        mvc.perform(get("/api/urls/{code}/stats", "old1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitCount").value(0))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));
    }

    @Test
    void stats_unknownCode_returns404() throws Exception {
        mvc.perform(get("/api/urls/{code}/stats", "nope123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short link nope123 not found"));
    }

    @Test
    void shorten_withFutureExpiry_returnsExpiryAndStillRedirects() throws Exception {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String body = mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "https://example.com", "expiresAt": "%s"}
                        """.formatted(expiresAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(body).get("code").asText();

        mvc.perform(get("/{code}", code))
                .andExpect(status().isFound());
    }

    private JsonNode shorten(String url) throws Exception {
        String body = mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "%s"}
                        """.formatted(url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
