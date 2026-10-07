package org.example.user;

import org.example.common.exception.NotFoundException;
import org.example.user.dto.UserResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** The email comes from the verified token, never from the request, so users only ever see themselves. */
    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        return repository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User " + email + " not found"));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }
}
