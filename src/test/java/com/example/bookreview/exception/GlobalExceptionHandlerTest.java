package com.example.bookreview.exception;

import com.example.bookreview.controller.ReviewController;
import com.example.bookreview.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    @DisplayName("Unsupported HTTP methods return 405 with standardized error response")
    void methodNotAllowedReturnsErrorResponse() throws Exception {
        mockMvc.perform(put("/reviews").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(HttpStatus.METHOD_NOT_ALLOWED.value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/reviews"));
    }

    @Test
    @DisplayName("Validation failures return 400 with details and success flag false")
    void validationErrorReturnsBadRequest() throws Exception {
        String payload = """
                {"title": "", "originalContent": ""}
                """;

        mockMvc.perform(post("/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/reviews"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
