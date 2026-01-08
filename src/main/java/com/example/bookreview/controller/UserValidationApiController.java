package com.example.bookreview.controller;

import com.example.bookreview.dto.validation.EmailAvailabilityResponse;
import com.example.bookreview.dto.validation.UsernameAvailabilityResponse;
import com.example.bookreview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserValidationApiController {

    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    private final UserRepository userRepository;

    @GetMapping("/check-username")
    public ResponseEntity<UsernameAvailabilityResponse> checkUsername(
            @RequestParam("username") String username) {
        String trimmed = username == null ? "" : username.trim();

        if (!isValidUsername(trimmed)) {
            log.debug("Invalid username format for availability check: '{}'", username);
            return ResponseEntity.badRequest().body(new UsernameAvailabilityResponse(false));
        }

        boolean exists = userRepository.existsByUsername(trimmed);
        return ResponseEntity.ok(new UsernameAvailabilityResponse(!exists));
    }

    @GetMapping("/check-email")
    public ResponseEntity<EmailAvailabilityResponse> checkEmail(
            @RequestParam("email") String email) {
        String trimmed = email == null ? "" : email.trim();

        if (!isValidEmail(trimmed)) {
            log.debug("Invalid email format for availability check: '{}'", email);
            return ResponseEntity.badRequest().body(new EmailAvailabilityResponse(false));
        }

        boolean exists = userRepository.existsByEmail(trimmed);
        return ResponseEntity.ok(new EmailAvailabilityResponse(!exists));
    }

    private boolean isValidUsername(String username) {
        return StringUtils.hasText(username) && username.length() >= 3 && username.matches(
                USERNAME_PATTERN);
    }

    private boolean isValidEmail(String email) {
        return StringUtils.hasText(email) && email.contains("@");
    }
}
