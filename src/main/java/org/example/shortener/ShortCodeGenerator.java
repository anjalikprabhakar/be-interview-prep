package org.example.shortener;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Random Base62 codes: unpredictable (not enumerable like a sequential id) and URL-safe. */
@Component
public class ShortCodeGenerator {

    static final int LENGTH = 7; // 62^7 ≈ 3.5 trillion combinations
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
