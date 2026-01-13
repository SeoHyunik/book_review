package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.domain.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.CostResult;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.exception.MissingApiKeyException;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.security.CurrentUserService;
import com.example.bookreview.service.currency.CurrencyService;
import com.example.bookreview.service.google.GoogleDriveService;
import com.example.bookreview.service.openai.OpenAiService;
import com.example.bookreview.service.review.ReviewServiceImpl;
import com.example.bookreview.util.TokenCostCalculator;
import java.math.BigDecimal;
import java.util.Optional;
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

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TokenCostCalculator tokenCostCalculator;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository, openAiService, currencyService, googleDriveService,
                currentUserService, tokenCostCalculator);
        when(currentUserService.getCurrentUserIdOrThrow()).thenReturn("user-1");
    }

    @Test
    void createReview_persistsWhenOpenAiMissingKey() {
        when(openAiService.generateImprovedReview(any(), any())).thenThrow(new MissingApiKeyException("key missing"));
        when(currencyService.convertUsdToKrw(any())).thenReturn(new BigDecimal("1500"));
        when(googleDriveService.uploadMarkdown(any(), any())).thenReturn(Optional.of("file-123"));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review incoming = invocation.getArgument(0);
            return new Review("id-1", incoming.title(), incoming.originalContent(), incoming.improvedContent(),
                    incoming.tokenCount(), incoming.usdCost(), incoming.krwCost(), incoming.googleFileId(),
                    incoming.ownerUserId(), incoming.integrationStatus(), incoming.createdAt());
        });

        Review saved = reviewService.createReview(new ReviewRequest("제목", "내용"));

        assertThat(saved.id()).isEqualTo("id-1");
        assertThat(saved.integrationStatus().openAiStatus()).isEqualTo(IntegrationStatus.Status.SKIPPED);
        assertThat(saved.improvedContent()).contains("[IMPROVEMENT_SKIPPED]");
        assertThat(saved.tokenCount()).isZero();
    }

    @Test
    void createReview_stillSavesWhenGoogleDriveSkipped() {
        when(openAiService.generateImprovedReview(any(), any())).thenThrow(new MissingApiKeyException("key missing"));
        when(currencyService.convertUsdToKrw(any())).thenReturn(new BigDecimal("1500"));
        when(googleDriveService.uploadMarkdown(any(), any())).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review incoming = invocation.getArgument(0);
            return new Review("id-2", incoming.title(), incoming.originalContent(), incoming.improvedContent(),
                    incoming.tokenCount(), incoming.usdCost(), incoming.krwCost(), incoming.googleFileId(),
                    incoming.ownerUserId(), incoming.integrationStatus(), incoming.createdAt());
        });

        Review saved = reviewService.createReview(new ReviewRequest("제목", "내용"));

        assertThat(saved.id()).isEqualTo("id-2");
        assertThat(saved.googleFileId()).isNull();
        assertThat(saved.integrationStatus().driveStatus()).isEqualTo(IntegrationStatus.Status.SKIPPED);
    }

    @Test
    void createReview_persistsTokenCostFromAiResponse() {
        AiReviewResult aiResult = new AiReviewResult("개선된 내용", true, "gpt-4o", "stop", 50, 10,
                60);
        when(openAiService.generateImprovedReview(any(), any())).thenReturn(aiResult);
        when(tokenCostCalculator.calculate("gpt-4o", 50, 10)).thenReturn(
                new CostResult(60, new BigDecimal("0.012345")));
        when(currencyService.convertUsdToKrw(any())).thenReturn(new BigDecimal("18.52"));
        when(googleDriveService.uploadMarkdown(any(), any())).thenReturn(Optional.of("file-123"));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review incoming = invocation.getArgument(0);
            return new Review("id-3", incoming.title(), incoming.originalContent(), incoming.improvedContent(),
                    incoming.tokenCount(), incoming.usdCost(), incoming.krwCost(), incoming.googleFileId(),
                    incoming.ownerUserId(), incoming.integrationStatus(), incoming.createdAt());
        });

        Review saved = reviewService.createReview(new ReviewRequest("제목", "내용"));

        assertThat(saved.tokenCount()).isEqualTo(60);
        assertThat(saved.usdCost()).isEqualByComparingTo("0.012345");
        assertThat(saved.krwCost()).isEqualByComparingTo("18.52");
        assertThat(saved.integrationStatus().openAiStatus()).isEqualTo(IntegrationStatus.Status.SUCCESS);
    }
}
