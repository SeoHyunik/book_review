package com.example.bookreview.service;

import com.example.bookreview.domain.User;
import com.example.bookreview.repository.UserRepository;
import com.example.bookreview.web.request.RegistrationRequest;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(RegistrationRequest request) {
        validateRequest(request);

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateKeyException("Username already exists");
        }

        if (StringUtils.hasText(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new DuplicateKeyException("Email already exists");
        }

        Set<String> roles = new HashSet<>();
        roles.add("USER");

        String email = StringUtils.hasText(request.email()) ? request.email() : null;

        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .email(email)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user with username={}", saved.getUsername());
        return saved;
    }

    private void validateRequest(RegistrationRequest request) {
        if (request == null
                || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())
                || !StringUtils.hasText(request.confirmPassword())) {
            throw new IllegalArgumentException("Username and password are required");
        }

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
    }
}
