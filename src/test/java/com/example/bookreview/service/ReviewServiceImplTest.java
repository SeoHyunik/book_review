package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OpenAiService openAiService;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private GoogleDriveService googleDriveService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository, openAiService, currencyService, googleDriveService);
    }

    @Test
    void createReview_persistsWhenOpenAiMissingKey() {
        when(openAiService.generateImprovedReview(any(), any())).thenThrow(new MissingApiKeyException("key missing"));
        when(currencyService.convertUsdToKrw(any())).thenReturn(new BigDecimal("1500"));
        when(googleDriveService.uploadMarkdown(any(), any())).thenReturn("file-123");
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review incoming = invocation.getArgument(0);
            return new Review("id-1", incoming.title(), incoming.originalContent(), incoming.improvedContent(),
                    incoming.tokenCount(), incoming.usdCost(), incoming.krwCost(), incoming.googleFileId(),
                    incoming.integrationStatus(), incoming.createdAt());
        });

        Review saved = reviewService.createReview(new ReviewRequest("제목", "내용"));

        assertThat(saved.id()).isEqualTo("id-1");
        assertThat(saved.integrationStatus().openAiStatus()).isEqualTo(IntegrationStatus.Status.SKIPPED);
        assertThat(saved.improvedContent()).contains("[IMPROVEMENT_SKIPPED]");
        assertThat(saved.tokenCount()).isZero();
    }

    @Test
    void createReview_stillSavesWhenGoogleDriveFails() {
        when(openAiService.generateImprovedReview(any(), any())).thenThrow(new MissingApiKeyException("key missing"));
        when(currencyService.convertUsdToKrw(any())).thenReturn(new BigDecimal("1500"));
        when(googleDriveService.uploadMarkdown(any(), any())).thenThrow(new RuntimeException("drive down"));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review incoming = invocation.getArgument(0);
            return new Review("id-2", incoming.title(), incoming.originalContent(), incoming.improvedContent(),
                    incoming.tokenCount(), incoming.usdCost(), incoming.krwCost(), incoming.googleFileId(),
                    incoming.integrationStatus(), incoming.createdAt());
        });

        Review saved = reviewService.createReview(new ReviewRequest("제목", "내용"));

        assertThat(saved.id()).isEqualTo("id-2");
        assertThat(saved.googleFileId()).isNull();
        assertThat(saved.integrationStatus().driveStatus()).isEqualTo(IntegrationStatus.Status.FAILED);
    }
}
