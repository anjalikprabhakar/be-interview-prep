package org.example.auth;

import org.example.auth.dto.LoginRequest;
import org.example.auth.dto.RegisterRequest;
import org.example.auth.dto.TokenResponse;
import org.example.common.exception.ConflictException;
import org.example.common.exception.UnauthorizedException;
import org.example.user.Role;
import org.example.user.User;
import org.example.user.UserRepository;
import org.example.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Compared against when the email is unknown, so a login takes the same BCrypt time either way
    // and response time does not reveal which emails have accounts.
    private final String dummyHash;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Always creates a USER: clients can never choose their own role.
     * Deliberately not @Transactional: the UNIQUE constraint is the duplicate check, and its violation
     * must be caught outside the failed transaction to become a 409 (no racy "exists?" check first).
     */
    public UserResponse register(RegisterRequest request) {
        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.USER);
        try {
            return UserResponse.from(repository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email is already registered");
        }
    }

    /** One generic message for unknown email and wrong password, so attackers can't enumerate accounts. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = repository.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return tokenService.issue(user.get());
    }
}
