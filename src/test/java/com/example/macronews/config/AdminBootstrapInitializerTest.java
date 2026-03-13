package com.example.macronews.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.dto.domain.User;
import com.example.macronews.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapInitializer adminBootstrapInitializer;

    @Test
    @DisplayName("Dev data initializer should be limited to dev and test profiles")
    void devDataInitializer_isLimitedToDevAndTestProfiles() {
        Profile profile = DevDataInitializer.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev", "test");
        assertThat(profile.value()).doesNotContain("default");
    }

    @Test
    @DisplayName("Admin bootstrap should skip creation when allowlist is blank")
    void run_skipsBootstrapWhenAllowlistIsBlank() {
        configureBootstrap("", "admin", "secret", "admin@example.com");

        adminBootstrapInitializer.run();

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Admin bootstrap should skip creation when username is not allowlisted")
    void run_skipsBootstrapWhenUsernameIsNotAllowlisted() {
        configureBootstrap("ops-admin", "admin", "secret", "admin@example.com");

        adminBootstrapInitializer.run();

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Admin bootstrap should create allowlisted admin when username and password are configured")
    void run_createsAllowlistedBootstrapAdmin() {
        configureBootstrap("admin, market-ops", " Admin ", "secret", " admin@example.com ");
        given(userRepository.findByUsername("admin")).willReturn(Optional.empty());
        given(passwordEncoder.encode("secret")).willReturn("encoded-secret");

        adminBootstrapInitializer.run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().username()).isEqualTo("admin");
        assertThat(savedUser.getValue().passwordHash()).isEqualTo("encoded-secret");
        assertThat(savedUser.getValue().email()).isEqualTo("admin@example.com");
        assertThat(savedUser.getValue().roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(savedUser.getValue().enabled()).isTrue();
    }

    @Test
    @DisplayName("Admin bootstrap should add ADMIN role to existing allowlisted user without duplicating the account")
    void run_addsAdminRoleToExistingAllowlistedUser() {
        configureBootstrap("admin", "ADMIN", "secret", "admin@example.com");
        User existingUser = User.builder()
                .id("user-1")
                .username("admin")
                .passwordHash("stored-hash")
                .email("admin@example.com")
                .roles(Set.of("USER"))
                .enabled(true)
                .createdAt(Instant.parse("2026-03-13T00:00:00Z"))
                .build();
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(existingUser));

        adminBootstrapInitializer.run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        verify(passwordEncoder, never()).encode(any());
        assertThat(savedUser.getValue().id()).isEqualTo("user-1");
        assertThat(savedUser.getValue().username()).isEqualTo("admin");
        assertThat(savedUser.getValue().passwordHash()).isEqualTo("stored-hash");
        assertThat(savedUser.getValue().roles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    @DisplayName("Admin bootstrap should leave existing allowlisted admin unchanged")
    void run_leavesExistingAdminUnchanged() {
        configureBootstrap("admin", "admin", "secret", "admin@example.com");
        User existingAdmin = User.builder()
                .id("admin-1")
                .username("admin")
                .passwordHash("stored-hash")
                .email("admin@example.com")
                .roles(Set.of("ADMIN", "USER"))
                .enabled(true)
                .createdAt(Instant.parse("2026-03-13T00:00:00Z"))
                .build();
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(existingAdmin));

        adminBootstrapInitializer.run();

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private void configureBootstrap(String allowedUsernames, String username, String password, String email) {
        ReflectionTestUtils.setField(adminBootstrapInitializer, "allowedUsernamesProperty", allowedUsernames);
        ReflectionTestUtils.setField(adminBootstrapInitializer, "bootstrapUsernameProperty", username);
        ReflectionTestUtils.setField(adminBootstrapInitializer, "bootstrapPassword", password);
        ReflectionTestUtils.setField(adminBootstrapInitializer, "bootstrapEmailProperty", email);
    }
}
