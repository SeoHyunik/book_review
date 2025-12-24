package com.example.bookreview.config;

import com.example.bookreview.domain.User;
import com.example.bookreview.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "default", "test"})
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUser("admin", Set.of("ADMIN", "USER"));
        seedUser("user", Set.of("USER"));
    }

    private void seedUser(String username, Set<String> roles) {
        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            log.debug("User '{}' already exists; skipping creation.", username);
        }, () -> {
            User user = User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode("password"))
                    .roles(roles)
                    .build();

            userRepository.save(user);
            log.info("Seeded default user: {}/password", username);
        });
    }
}
