package org.example.auth;

import org.example.user.Role;
import org.example.user.User;
import org.example.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The only way an ADMIN is created: registration always makes a USER.
 * Runs on startup when ADMIN_EMAIL and ADMIN_PASSWORD are set, and does nothing otherwise.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository repository, PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email}") String email,
                       @Value("${app.admin.password}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = User.normalizeEmail(email);
        Optional<User> existing = repository.findByEmail(normalized);
        if (existing.isPresent()) {
            // Never promote an existing account: whoever registered this email first would become ADMIN.
            if (existing.get().getRole() != Role.ADMIN) {
                log.warn("ADMIN_EMAIL {} is already registered as {}; no admin was created",
                        normalized, existing.get().getRole());
            }
            return;
        }
        repository.save(new User(normalized, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Seeded admin account {}", normalized);
    }
}
