package org.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shortener.ShortLink;
import org.example.shortener.ShortLinkRepository;
import org.example.user.Role;
import org.example.user.User;
import org.example.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role rules end to end: real login, real signed tokens, the production SecurityFilterChain. */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ShortLinkRepository shortLinkRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
        shortLinkRepository.deleteAll();
    }

    @Test
    void listUsers_asUser_returns403Json() throws Exception {
        AuthTestSupport.register(mvc, "user@example.com");
        String token = AuthTestSupport.login(mvc, objectMapper, "user@example.com");

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @Test
    void listUsers_noToken_returns401Json() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @Test
    void listUsers_asAdmin_returns200WithAllUsers() throws Exception {
        // Admins can't register through the API, so this one is created directly (as AdminSeeder does).
        userRepository.save(new User("admin@example.com",
                passwordEncoder.encode(AuthTestSupport.PASSWORD), Role.ADMIN));
        AuthTestSupport.register(mvc, "user@example.com");
        String token = AuthTestSupport.login(mvc, objectMapper, "admin@example.com");

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].email").value("user@example.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void existingApi_noToken_returns401Json() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shortLinkRedirect_noToken_staysPublic() throws Exception {
        shortLinkRepository.saveAndFlush(new ShortLink("pub1234", "https://example.com", null));

        mvc.perform(get("/pub1234"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com"));
    }

    @Test
    void shortLinkRedirect_noTokenUnknownCode_returns404NotA401() throws Exception {
        mvc.perform(get("/nope123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shortLinkRedirect_noTokenExpiredCode_returns410NotA401() throws Exception {
        shortLinkRepository.saveAndFlush(new ShortLink("old1234", "https://example.com",
                Instant.now().minus(1, ChronoUnit.HOURS)));

        mvc.perform(get("/old1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }
}
