package com.example.bookreview.service;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.service.model.AiReviewResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OpenAiService openAiService;
    private final CurrencyService currencyService;
    private final GoogleDriveService googleDriveService;

    @Override
    public List<Review> getReviews() {
        return reviewRepository.findAll();
    }

    @Override
    public Optional<Review> getReview(String id) {
        return reviewRepository.findById(id);
    }

    @Override
    @Transactional
    public Review createReview(ReviewRequest request) {
        AiReviewResult aiResult = openAiService.generateImprovedReview(request.getTitle(), request.getOriginalContent());
        BigDecimal krwCost = currencyService.convertUsdToKrw(aiResult.usdCost());

        String markdown = buildMarkdown(request.getTitle(), aiResult.improvedContent());
        String fileId = googleDriveService.uploadMarkdown(request.getTitle() + ".md", markdown);

        Review review = Review.builder()
                .title(request.getTitle())
                .originalContent(request.getOriginalContent())
                .improvedContent(aiResult.improvedContent())
                .tokenCount(aiResult.tokenCount())
                .usdCost(aiResult.usdCost())
                .krwCost(krwCost)
                .googleFileId(fileId)
                .createdAt(LocalDateTime.now())
                .build();

        return reviewRepository.save(review);
    }

    private String buildMarkdown(String title, String improvedContent) {
        return "# " + title + "\n\n" + improvedContent + "\n";
    }
}
