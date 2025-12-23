package com.example.bookreview.service;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.service.MissingApiKeyException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[\\\\/:*?\"<>|#%]");
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
        // NOTE: MongoDB multi-document transactions require a replica set. In single-node dev/test
        // environments @Transactional will not enforce atomicity but still provides declarative intent.
        log.info("Starting review creation for title='{}'", request.title());
        validateTitleForUpload(request.title());

        IntegrationStatus.Status openAiStatus = IntegrationStatus.Status.SUCCESS;
        IntegrationStatus.Status currencyStatus = IntegrationStatus.Status.SUCCESS;
        IntegrationStatus.Status driveStatus = IntegrationStatus.Status.SUCCESS;
        StringBuilder warnings = new StringBuilder();

        AiReviewResult aiResult = fallbackAiResult(request);
        try {
            aiResult = openAiService.generateImprovedReview(request.title(), request.originalContent());
            log.debug("AI review generated with tokenCount={} and usdCost={}", aiResult.totalTokens(), aiResult.usdCost());
        } catch (MissingApiKeyException ex) {
            openAiStatus = IntegrationStatus.Status.SKIPPED;
            appendWarning(warnings, "OpenAI key missing: " + ex.getMessage());
            log.warn("OpenAI key missing, skipping AI improvement and using fallback content");
        } catch (Exception ex) {
            openAiStatus = IntegrationStatus.Status.FAILED;
            appendWarning(warnings, "OpenAI failed: " + ex.getMessage());
            log.warn("OpenAI content generation failed, continuing with fallback content", ex);
        }

        long tokenCount = aiResult.totalTokens();
        BigDecimal usdCost = aiResult.usdCost() == null ? BigDecimal.ZERO : aiResult.usdCost();
        BigDecimal krwCost = null;
        try {
            krwCost = convertToKrw(usdCost);
        } catch (Exception ex) {
            currencyStatus = IntegrationStatus.Status.FAILED;
            appendWarning(warnings, "Currency conversion failed: " + ex.getMessage());
            log.warn("Currency conversion failed, continuing without KRW cost", ex);
        }

        String markdown = buildMarkdown(request.title(), aiResult.improvedContent());
        String fileId = null;
        try {
            fileId = uploadToDrive(request.title(), markdown);
        } catch (Exception ex) {
            driveStatus = IntegrationStatus.Status.FAILED;
            appendWarning(warnings, "Google Drive upload failed: " + ex.getMessage());
            log.warn("Google Drive upload failed, continuing without file link", ex);
        }

        IntegrationStatus integrationStatus = new IntegrationStatus(openAiStatus, currencyStatus, driveStatus,
                warnings.toString());
        Review review = new Review(
                null,
                request.title(),
                request.originalContent(),
                aiResult.improvedContent(),
                tokenCount,
                usdCost,
                krwCost,
                fileId,
                integrationStatus,
                null
        );

        try {
            Review saved = reviewRepository.save(review);
            log.info("Review persisted with id={}", saved.id());
            return saved;
        } catch (RuntimeException ex) {
            log.error("Persisting review failed, initiating rollback for fileId={}", fileId, ex);
            rollbackGoogleFile(fileId);
            throw ex;
        }
    }

    private BigDecimal convertToKrw(BigDecimal usdCost) {
        if (usdCost == null) {
            return null;
        }
        BigDecimal krwCost = currencyService.convertUsdToKrw(usdCost);
        log.debug("Converted cost to KRW: {}", krwCost);
        return krwCost;
    }

    private String uploadToDrive(String filename, String markdown) {
        String fileId = googleDriveService.uploadMarkdown(filename, markdown);
        log.info("Markdown uploaded to Google Drive with fileId={}", fileId);
        return fileId;
    }

    private AiReviewResult fallbackAiResult(ReviewRequest request) {
        String content = "[IMPROVEMENT_SKIPPED]\n" + request.originalContent();
        return new AiReviewResult(content, "skipped", 0, 0, 0, BigDecimal.ZERO);
    }

    private void appendWarning(StringBuilder warnings, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!warnings.isEmpty()) {
            warnings.append(" | ");
        }
        warnings.append(message);
    }

    private void rollbackGoogleFile(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        try {
            googleDriveService.deleteFile(fileId);
        } catch (Exception rollbackEx) {
            log.warn("Failed to rollback Google Drive file for id={}", fileId, rollbackEx);
        }
    }

    private String buildMarkdown(String title, String improvedContent) {
        return "# " + title + "\n\n" + improvedContent + "\n";
    }

    private void validateTitleForUpload(String title) {
        if (INVALID_FILE_CHARS.matcher(title).find()) {
            throw new IllegalArgumentException("제목에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
    }

}
