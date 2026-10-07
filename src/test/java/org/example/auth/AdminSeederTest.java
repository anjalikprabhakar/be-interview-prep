package org.example.auth;

import org.example.user.Role;
import org.example.user.User;
import org.example.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminSeederTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void run_credentialsSet_createsAdminWithHashedPassword() {
        given(repository.findByEmail("admin@example.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("admin-password")).willReturn("$2a$hashed");

        new AdminSeeder(repository, passwordEncoder, "Admin@Example.com", "admin-password").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
    }

    @Test
    void run_credentialsNotSet_createsNothing() {
        new AdminSeeder(repository, passwordEncoder, "", "").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void run_emailRegisteredAsUser_doesNotPromoteIt() {
        User existing = new User("admin@example.com", "$2a$existing", Role.USER);
        given(repository.findByEmail("admin@example.com")).willReturn(Optional.of(existing));

        new AdminSeeder(repository, passwordEncoder, "admin@example.com", "admin-password").run(null);

        verify(repository, never()).save(any());
        assertThat(existing.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void run_adminAlreadyExists_createsNothing() {
        given(repository.findByEmail("admin@example.com"))
                .willReturn(Optional.of(new User("admin@example.com", "$2a$existing", Role.ADMIN)));

        new AdminSeeder(repository, passwordEncoder, "admin@example.com", "admin-password").run(null);

        verify(repository, never()).save(any());
    }
}
