package com.example.bookreview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.domain.Review;
import com.example.bookreview.repository.ReviewRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ReviewLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewRepository reviewRepository;

    @Test
    void htmlFormFlow_logsKeyControllerAndServiceSteps(CapturedOutput output) throws Exception {
        Review saved = new Review(
                "6940eff92b72c6cfe75398ed",
                "리뷰테스트",
                "원본 내용",
                "개선된 독후감 예시",
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "fake-file-id",
                LocalDateTime.parse("2025-12-16T14:36:57")
        );

        given(reviewRepository.findAll()).willReturn(List.of());
        given(reviewRepository.save(any())).willReturn(saved);
        given(reviewRepository.findById(saved.id())).willReturn(Optional.of(saved));

        mockMvc.perform(get("/reviews").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reviews/new").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reviews")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "리뷰테스트")
                        .param("originalContent", "원본 내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews/" + saved.id()));

        mockMvc.perform(get("/reviews/" + saved.id()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        String logs = output.getOut();

        assertThat(logs)
                .contains("Rendering review list page")
                .contains("Fetching all reviews from repository")
                .contains("Rendering review creation form")
                .contains("Received HTML form submission for new review: title='리뷰테스트'")
                .contains("Starting review creation for title='리뷰테스트'")
                .contains("Generating improved review using OpenAI mock for title='리뷰테스트'")
                .contains("Returning placeholder AI review result: AiReviewResult[improvedContent=개선된 독후감 예시, tokenCount=0, usdCost=0]")
                .contains("Converting USD to KRW for amount=0")
                .contains("Converted USD 0 to KRW 0 using static rate")
                .contains("Converted cost to KRW: 0")
                .contains("Uploading markdown to Google Drive mock: filename='리뷰테스트.md', contentLength=20")
                .contains("Returning placeholder Google Drive fileId=fake-file-id")
                .contains("Markdown uploaded to Google Drive with fileId=fake-file-id")
                .contains("Review persisted with id=" + saved.id())
                .contains("Review created successfully via form with id=" + saved.id())
                .contains("Displaying review detail page for id=" + saved.id())
                .contains("Fetching review by id=" + saved.id());
    }
}
