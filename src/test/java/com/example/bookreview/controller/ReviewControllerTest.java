package com.example.bookreview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    private Review sampleReview() {
        return new Review(
                "r1",
                "샘플 제목",
                "원본 내용",
                "개선된 내용",
                123L,
                new BigDecimal("0.12"),
                new BigDecimal("150.0"),
                "drive-file-id",
                LocalDateTime.parse("2024-01-01T10:00:00")
        );
    }

    @Test
    void listJson_returnsMockData() throws Exception {
        when(reviewService.getReviews()).thenReturn(List.of(sampleReview()));

        mockMvc.perform(get("/reviews").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"))
                .andExpect(jsonPath("$[0].title").value("샘플 제목"))
                .andExpect(jsonPath("$[0].improvedContent").value("개선된 내용"));
    }

    @Test
    void detailJson_returnsSingleMockReview() throws Exception {
        when(reviewService.getReview("r1")).thenReturn(Optional.of(sampleReview()));

        mockMvc.perform(get("/reviews/r1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleFileId").value("drive-file-id"));
    }

    @Test
    void createJson_savesReviewAndReturnsLocation() throws Exception {
        when(reviewService.createReview(any())).thenReturn(sampleReview());

        ReviewRequest request = new ReviewRequest("새 제목", "새 내용");

        mockMvc.perform(post("/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/reviews/r1"))
                .andExpect(jsonPath("$.title").value("샘플 제목"));
    }
}
