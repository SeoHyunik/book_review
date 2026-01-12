package com.example.bookreview.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.config.SecurityConfig;
import com.example.bookreview.repository.UserRepository;
import com.example.bookreview.security.CustomUserDetailsService;
import com.example.bookreview.security.LoggingAuthenticationFailureHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserValidationApiController.class, properties = "spring.cache.type=none")
@Import(SecurityConfig.class)
class UserValidationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private LoggingAuthenticationFailureHandler loggingAuthenticationFailureHandler;

    @Test
    @DisplayName("Available username should return available true")
    void checkUsername_available() throws Exception {
        given(userRepository.existsByUsername("newuser")).willReturn(false);

        mockMvc.perform(get("/api/users/check-username").param("username", "newuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("Duplicate username should return available false")
    void checkUsername_duplicate() throws Exception {
        given(userRepository.existsByUsername("taken"))
                .willReturn(true);

        mockMvc.perform(get("/api/users/check-username").param("username", "taken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("Invalid username should return bad request")
    void checkUsername_invalidInput() throws Exception {
        mockMvc.perform(get("/api/users/check-username").param("username", "!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.available").value(false));

        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("Endpoint should be accessible without authentication")
    void checkUsername_accessibleWithoutAuth() throws Exception {
        given(userRepository.existsByUsername("guest"))
                .willReturn(false);

        mockMvc.perform(get("/api/users/check-username").param("username", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
