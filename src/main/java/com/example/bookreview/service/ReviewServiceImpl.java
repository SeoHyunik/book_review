package com.example.bookreview.service;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.OpenAiResponse;
import com.example.bookreview.dto.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final OpenAiService openAiService;
  private final CurrencyService currencyService;
  private final GoogleDriveService googleDriveService;

  @Override
  public List<Review> getReviews() {
    log.debug("[SERVICE] Fetching all reviews from repository");
    return reviewRepository.findAll();
  }

  @Override
  public Optional<Review> getReview(String id) {
    log.debug("[SERVICE] Fetching review by id={}", id);
    return reviewRepository.findById(id);
  }

    @Override
    @Transactional
    public Review createReview(ReviewRequest request) {
        log.info("[SERVICE] Starting review creation for title='{}'", request.title());
        OpenAiResponse aiResult = openAiService.improveReview(request.originalContent()).block();
        if (aiResult == null) {
            throw new IllegalStateException("Failed to generate AI review content");
        }
        long tokenCount = (long) aiResult.inputTokens() + aiResult.outputTokens();
        BigDecimal usdCost = BigDecimal.ZERO;
        log.debug("[SERVICE] AI review generated with tokenCount={} and usdCost={}", tokenCount, usdCost);
        BigDecimal krwCost = currencyService.convertUsdToKrw(usdCost);
        log.debug("[SERVICE] Converted cost to KRW: {}", krwCost);

    String markdown = buildMarkdown(request.title(), aiResult.improvedContent());
    String fileId = googleDriveService.uploadMarkdown(request.title() + ".md", markdown);
    log.info("[SERVICE] Markdown uploaded to Google Drive with fileId={}", fileId);

        Review review = new Review(
            null,
            request.title(),
            request.originalContent(),
            aiResult.improvedContent(),
            tokenCount,
            usdCost,
            krwCost,
            fileId,
            LocalDateTime.now()
        );

    Review saved = reviewRepository.save(review);
    log.info("[SERVICE] Review persisted with id={}", saved.id());
    return saved;
  }

  private String buildMarkdown(String title, String improvedContent) {
    return "# " + title + "\n\n" + improvedContent + "\n";
  }
}
