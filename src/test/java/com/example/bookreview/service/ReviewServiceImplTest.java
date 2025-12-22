package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReview_slugifiesTitleForFilename() {
        ReviewRequest request = new ReviewRequest("파일 제목 예시", "본문 내용");
        AiReviewResult aiResult = new AiReviewResult("개선된 본문", "gpt-4", 1, 1, 2, new BigDecimal("0.01"));

        when(openAiService.generateImprovedReview(anyString(), anyString())).thenReturn(aiResult);
        when(currencyService.convertUsdToKrw(aiResult.usdCost())).thenReturn(new BigDecimal("13.00"));
        when(googleDriveService.uploadMarkdown(anyString(), anyString())).thenReturn("drive-file");
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return new Review(
                    "saved-id",
                    review.title(),
                    review.originalContent(),
                    review.improvedContent(),
                    review.tokenCount(),
                    review.usdCost(),
                    review.krwCost(),
                    review.googleFileId(),
                    review.createdAt());
        });

        Review saved = reviewService.createReview(request);

        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(googleDriveService).uploadMarkdown(filenameCaptor.capture(), anyString());

        assertThat(filenameCaptor.getValue()).isEqualTo("%ED%8C%8C%EC%9D%BC-%EC%A0%9C%EB%AA%A9-%EC%98%88%EC%8B%9C.md");
        assertThat(saved.id()).isEqualTo("saved-id");
        assertThat(saved.googleFileId()).isEqualTo("drive-file");
    }
}
