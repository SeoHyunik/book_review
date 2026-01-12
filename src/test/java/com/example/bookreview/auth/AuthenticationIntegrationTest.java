package com.example.bookreview.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.dto.domain.User;
import com.example.bookreview.repository.UserRepository;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.util.StringUtils;

// TODO: Ensure Docker/Testcontainers is available in CI so this integration test runs there.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires Docker / Testcontainers")
class AuthenticationIntegrationTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0.12");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginAndRegisterPagesAreAccessible() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void registerPageExposesSingleSubmitButton() throws Exception {
        MvcResult result = mockMvc.perform(get("/register")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        assertThat(StringUtils.countOccurrencesOf(content, "id=\"submit-btn\"")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(content, "type=\"submit\"")).isEqualTo(1);
    }

    @Test
    void canRegisterNewUserAndLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "newuser1")
                        .param("password", "newpass123")
                        .param("confirmPassword", "newpass123")
                        .param("email", "newuser1@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.findByUsername("newuser1")).isPresent();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "newuser1")
                        .param("password", "newpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "badpassuser")
                        .param("password", "rightpass123")
                        .param("confirmPassword", "rightpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "badpassuser")
                        .param("password", "wrongpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void duplicateUsernameShowsValidationError() throws Exception {
        if (!userRepository.existsByUsername("duplicateUser")) {
            userRepository.save(User.builder()
                    .username("duplicateUser")
                    .passwordHash(passwordEncoder.encode("secret"))
                    .roles(Set.of("USER"))
                    .enabled(true)
                    .build());
        }

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "duplicateUser")
                        .param("password", "anotherpass")
                        .param("confirmPassword", "anotherpass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This username is already taken.")));
    }

    @Test
    void passwordMismatchReturnsToForm() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "mismatchUser")
                        .param("password", "password123")
                        .param("confirmPassword", "password124"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void seededAdminAccountAllowsLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews"));
    }
}
