package org.example.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void jwtSigningKey_secretShorterThan32Bytes_failsAtStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void jwtSigningKey_32ByteSecret_returnsHmacKey() {
        assertThat(config.jwtSigningKey("0123456789abcdef0123456789abcdef").getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
