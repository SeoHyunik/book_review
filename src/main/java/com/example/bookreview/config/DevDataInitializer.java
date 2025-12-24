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
        if (userRepository.count() > 0) {
            log.info("User collection already populated; skipping seed data.");
            return;
        }

        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("password"))
                .roles(Set.of("ADMIN", "USER"))
                .build();

        User user = User.builder()
                .username("user")
                .passwordHash(passwordEncoder.encode("password"))
                .roles(Set.of("USER"))
                .build();

        userRepository.save(admin);
        userRepository.save(user);
        log.info("Seeded default users: admin/password and user/password");
    }
}
