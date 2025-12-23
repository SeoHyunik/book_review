package com.example.bookreview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.service.OpenAiService;
import com.example.bookreview.service.GoogleDriveService;
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

    @MockBean
    private OpenAiService openAiService;

    @MockBean
    private GoogleDriveService googleDriveService;

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
                new IntegrationStatus(IntegrationStatus.Status.SUCCESS, IntegrationStatus.Status.SUCCESS,
                        IntegrationStatus.Status.SUCCESS, null),
                LocalDateTime.parse("2025-12-16T14:36:57")
        );

        given(reviewRepository.findAll()).willReturn(List.of());
        given(reviewRepository.save(any())).willReturn(saved);
        given(reviewRepository.findById(saved.id())).willReturn(Optional.of(saved));
        given(openAiService.generateImprovedReview("리뷰테스트", "원본 내용"))
                .willReturn(new AiReviewResult("개선된 독후감 예시", "gpt-4o", 2, 3, 5, BigDecimal.ZERO));
        given(googleDriveService.uploadMarkdown(any(), any())).willReturn("fake-file-id");

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
                .contains("[MVC] Rendering review list page")
                .contains("[SERVICE] Fetching all reviews from repository")
                .contains("[MVC] Rendering review creation form")
                .contains("[MVC] Received HTML form submission for new review: title='리뷰테스트'")
                .contains("[SERVICE] Starting review creation for title='리뷰테스트'")
                .contains("[CURRENCY] Converting USD to KRW for amount=0")
                .contains("[SERVICE] Converted cost to KRW: 0")
                .contains("[SERVICE] Markdown uploaded to Google Drive with fileId=fake-file-id")
                .contains("[SERVICE] Review persisted with id=" + saved.id())
                .contains("[MVC] Review created successfully via form with id=" + saved.id())
                .contains("[MVC] Displaying review detail page for id=" + saved.id())
                .contains("[SERVICE] Fetching review by id=" + saved.id());
    }
}
