package org.example.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        // BCrypt accepts at most 72 bytes (Spring Security 6.5 throws on longer input), so reject it here as a 400.
        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        @MaxBytes(value = 72, message = "password must be at most 72 bytes")
        String password
) {
}
