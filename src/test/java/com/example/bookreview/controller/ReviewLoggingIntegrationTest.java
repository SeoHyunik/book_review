package com.example.bookreview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.dto.domain.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.service.openai.OpenAiService;
import com.example.bookreview.service.google.GoogleDriveService;
import com.example.bookreview.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private ReviewRepository reviewRepository;

    @MockitoBean
    private OpenAiService openAiService;

    @MockitoBean
    private GoogleDriveService googleDriveService;

    @MockitoBean
    private CurrentUserService currentUserService;

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
                "admin-id",
                new IntegrationStatus(IntegrationStatus.Status.SUCCESS, IntegrationStatus.Status.SUCCESS,
                        IntegrationStatus.Status.SUCCESS, null),
                LocalDateTime.parse("2025-12-16T14:36:57")
        );

        given(currentUserService.isAdmin()).willReturn(true);
        given(currentUserService.getCurrentUserIdOrThrow()).willReturn("admin-id");
        given(reviewRepository.findAll()).willReturn(List.of());
        given(reviewRepository.save(any())).willReturn(saved);
        given(reviewRepository.findById(saved.id())).willReturn(Optional.of(saved));
        given(openAiService.generateImprovedReview("리뷰테스트", "원본 내용"))
                .willReturn(new AiReviewResult("개선된 독후감 예시", "gpt-4o", 2, 3, 5, BigDecimal.ZERO));
        given(googleDriveService.uploadMarkdown(any(), any())).willReturn("fake-file-id");

        mockMvc.perform(get("/reviews").with(user("admin").roles("ADMIN")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reviews/new").with(user("admin").roles("ADMIN")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reviews")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "리뷰테스트")
                        .param("originalContent", "원본 내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews/" + saved.id()));

        mockMvc.perform(get("/reviews/" + saved.id()).with(user("admin").roles("ADMIN"))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        String logs = output.getOut();

        assertThat(logs)
                .contains("Received HTML form submission for new review: title='리뷰테스트'")
                .contains("Starting review creation for title='리뷰테스트'")
                .contains("Markdown uploaded to Google Drive with fileId=fake-file-id")
                .contains("Review persisted with id=" + saved.id())
                .contains("Review created successfully via form with id=" + saved.id());
    }
}
