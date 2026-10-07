package org.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the retry works against the real UNIQUE constraint: this fails if shorten() ever runs the
 * retry loop inside one transaction, because the first collision would make it rollback-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortLinkCollisionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ShortLinkRepository repository;

    @MockitoBean
    ShortCodeGenerator codeGenerator;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shorten_generatedCodeAlreadyExists_retriesAndReturns201() throws Exception {
        repository.saveAndFlush(new ShortLink("TAKEN01", "https://example.com/first", null));
        given(codeGenerator.generate()).willReturn("TAKEN01", "FREE001");

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "https://example.com/second"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FREE001"));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByCode("TAKEN01")).get()
                .extracting(ShortLink::getOriginalUrl).isEqualTo("https://example.com/first");
    }
}
