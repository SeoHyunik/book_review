package com.example.bookreview.service;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.OpenAiResult;
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
    log.debug("Fetching all reviews from repository");
    return reviewRepository.findAll();
  }

  @Override
  public Optional<Review> getReview(String id) {
    log.debug("Fetching review by id={}", id);
    return reviewRepository.findById(id);
  }

    @Override
    @Transactional
    public Review createReview(ReviewRequest request) {
        log.info("Starting review creation for title='{}'", request.title());
        OpenAiResult aiResult = openAiService.improveReview(request.originalContent());
        long tokenCount = (long) aiResult.getPromptTokens() + aiResult.getCompletionTokens();
        BigDecimal usdCost = BigDecimal.ZERO;
        log.debug("AI review generated with tokenCount={} and usdCost={}", tokenCount, usdCost);
        BigDecimal krwCost = currencyService.convertUsdToKrw(usdCost);
        log.debug("Converted cost to KRW: {}", krwCost);

    String markdown = buildMarkdown(request.title(), aiResult.getImprovedContent());
    String fileId = googleDriveService.uploadMarkdown(request.title() + ".md", markdown);
    log.info("Markdown uploaded to Google Drive with fileId={}", fileId);

        Review review = new Review(
            null,
            request.title(),
            request.originalContent(),
            aiResult.getImprovedContent(),
            tokenCount,
            usdCost,
            krwCost,
            fileId,
            LocalDateTime.now()
        );

    Review saved = reviewRepository.save(review);
    log.info("Review persisted with id={}", saved.id());
    return saved;
  }

  private String buildMarkdown(String title, String improvedContent) {
    return "# " + title + "\n\n" + improvedContent + "\n";
  }
}
