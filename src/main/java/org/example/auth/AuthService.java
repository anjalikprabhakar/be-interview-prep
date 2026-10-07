package org.example.auth;

import org.example.auth.dto.RegisterRequest;
import org.example.common.exception.ConflictException;
import org.example.user.Role;
import org.example.user.User;
import org.example.user.UserRepository;
import org.example.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
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
}
