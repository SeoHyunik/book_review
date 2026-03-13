package com.example.macronews.config;

import com.example.macronews.dto.domain.User;
import com.example.macronews.repository.UserRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Profile("!dev & !test")
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.allowed-usernames:}")
    private String allowedUsernamesProperty;

    @Value("${app.admin.bootstrap-username:}")
    private String bootstrapUsernameProperty;

    @Value("${app.admin.bootstrap-password:}")
    private String bootstrapPassword;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapEmailProperty;

    @Override
    public void run(String... args) {
        Set<String> allowedUsernames = parseAllowedUsernames(allowedUsernamesProperty);
        String bootstrapUsername = normalizeUsername(bootstrapUsernameProperty);

        if (!StringUtils.hasText(bootstrapUsername) || !StringUtils.hasText(bootstrapPassword)) {
            log.debug("Skipping admin bootstrap: username/password not fully configured.");
            return;
        }

        if (allowedUsernames.isEmpty()) {
            log.warn("Skipping admin bootstrap for '{}': admin allowlist is empty.", bootstrapUsername);
            return;
        }

        if (!allowedUsernames.contains(bootstrapUsername)) {
            log.warn("Skipping admin bootstrap for '{}': username is not in app.admin.allowed-usernames.",
                    bootstrapUsername);
            return;
        }

        String bootstrapEmail = StringUtils.hasText(bootstrapEmailProperty) ? bootstrapEmailProperty.trim() : null;

        userRepository.findByUsername(bootstrapUsernameProperty.trim()).ifPresentOrElse(existing -> {
            Set<String> updatedRoles = new LinkedHashSet<>(existing.roles());
            if (updatedRoles.add("ADMIN")) {
                userRepository.save(User.builder()
                        .id(existing.id())
                        .username(existing.username())
                        .passwordHash(existing.passwordHash())
                        .email(existing.email())
                        .roles(updatedRoles)
                        .enabled(existing.enabled())
                        .createdAt(existing.createdAt())
                        .build());
                log.info("Granted ADMIN role to existing allowlisted user '{}'.", existing.username());
            } else {
                log.info("Allowlisted bootstrap user '{}' already has ADMIN role; no changes applied.",
                        existing.username());
            }
        }, () -> {
            User user = User.builder()
                    .username(bootstrapUsernameProperty.trim())
                    .passwordHash(passwordEncoder.encode(bootstrapPassword))
                    .email(bootstrapEmail)
                    .roles(Set.of("ADMIN", "USER"))
                    .enabled(true)
                    .build();

            userRepository.save(user);
            log.info("Created allowlisted bootstrap admin user '{}'.", user.username());
        });
    }

    private Set<String> parseAllowedUsernames(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalizeUsername)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase() : "";
    }
}
