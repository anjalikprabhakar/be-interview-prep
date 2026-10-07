package org.example.user;

import org.example.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/api/users/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.getByEmail(jwt.getSubject());
    }

    // ADMIN-only: enforced by the /api/admin/** rule in SecurityConfig.
    @GetMapping("/api/admin/users")
    public List<UserResponse> list() {
        return service.list();
    }
}
