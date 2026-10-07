package org.example.shortener;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generate_manyCodes_areSevenUrlSafeCharsAndDistinct() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String code = generator.generate();
            assertThat(code).matches("[A-Za-z0-9]{7}");
            codes.add(code);
        }
        // 1,000 draws from 62^7 values: a duplicate here would mean the generator is broken, not unlucky.
        assertThat(codes).hasSize(1_000);
    }
}
