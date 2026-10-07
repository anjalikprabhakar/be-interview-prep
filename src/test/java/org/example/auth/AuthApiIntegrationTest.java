package org.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.example.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository repository;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void register_validRequest_returns201AndStoresBcryptHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "Alice@Example.com", "password": "%s"}
                        """.formatted(AuthTestSupport.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String storedHash = repository.findByEmail("alice@example.com").orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo(AuthTestSupport.PASSWORD);
    }

    @Test
    void register_roleInBody_isIgnoredAndUserIsCreated() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "sneaky@example.com", "password": "%s", "role": "ADMIN"}
                        """.formatted(AuthTestSupport.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_emailTakenInDifferentCase_returns409() throws Exception {
        AuthTestSupport.register(mvc, "bob@example.com");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "BOB@example.com", "password": "%s"}
                        """.formatted(AuthTestSupport.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void register_invalidEmailAndShortPassword_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].message")
                        .value("email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message")
                        .value("password must be at least 8 characters"));
    }

    @Test
    void register_passwordOver72Bytes_returns400() throws Exception {
        // 30 characters but 90 UTF-8 bytes: within a character limit, over BCrypt's 72-byte limit.
        String password = "€".repeat(30);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "euro@example.com", "password": "%s"}
                        """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at most 72 bytes"));
    }

    @Test
    void login_validCredentials_returnsBearerTokenValidFor15Minutes() throws Exception {
        AuthTestSupport.register(mvc, "carol@example.com");

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "carol@example.com", "password": "%s"}
                        """.formatted(AuthTestSupport.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(objectMapper.readTree(body).get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo("carol@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void login_wrongPasswordOrUnknownEmail_returnsSameGeneric401() throws Exception {
        AuthTestSupport.register(mvc, "dave@example.com");

        for (String email : List.of("dave@example.com", "nobody@example.com")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "%s", "password": "wrong-password"}
                            """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }
    }

    @Test
    void me_withToken_returnsOwnProfile() throws Exception {
        // Two users, logged in as the second: a /me that ignored the token and returned any user would fail.
        AuthTestSupport.register(mvc, "other@example.com");
        AuthTestSupport.register(mvc, "erin@example.com");
        String token = AuthTestSupport.login(mvc, objectMapper, "erin@example.com");

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("erin@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_noToken_returns401JsonWithoutCreatingSession() throws Exception {
        // Without STATELESS, a rejected request is saved in an HttpSession (to replay after login),
        // so this is the path where a session would appear.
        MvcResult result = mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void me_tokenExpired30SecondsAgo_returns401() throws Exception {
        AuthTestSupport.register(mvc, "frank@example.com");
        // 30s past expiry would still pass with the default 60s clock skew; zero skew rejects it.
        Instant expiredAt = Instant.now().minusSeconds(30);
        String token = encode(jwtEncoder, "frank@example.com", expiredAt.minus(Duration.ofMinutes(15)), expiredAt);

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void me_tokenSignedWithAnotherKey_returns401() throws Exception {
        AuthTestSupport.register(mvc, "grace@example.com");
        byte[] otherKey = "a-completely-different-key-of-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        Instant now = Instant.now();
        String token = encode(forger, "grace@example.com", now, now.plus(Duration.ofMinutes(15)));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    private static String encode(JwtEncoder encoder, String subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("USER"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
