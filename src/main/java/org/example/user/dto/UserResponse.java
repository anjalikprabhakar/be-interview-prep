package org.example.user.dto;

import org.example.user.Role;
import org.example.user.User;

import java.time.Instant;

/** Never includes the password hash. */
public record UserResponse(
        Long id,
        String email,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
