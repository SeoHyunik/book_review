package com.example.bookreview.service.review;

import com.example.bookreview.dto.domain.Review;
import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.DeleteReviewResult;
import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.exception.MissingApiKeyException;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.security.CurrentUserService;
import com.example.bookreview.service.currency.CurrencyService;
import com.example.bookreview.service.google.GoogleDriveService;
import com.example.bookreview.service.openai.OpenAiService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[\\\\/:*?\"<>|#%]");
    private final ReviewRepository reviewRepository;
    private final OpenAiService openAiService;
    private final CurrencyService currencyService;
    private final GoogleDriveService googleDriveService;
    private final CurrentUserService currentUserService;

    @Override
    public List<Review> getReviews() {
        if (currentUserService.isAdmin()) {
            log.debug("Fetching all reviews from repository as admin");
            return reviewRepository.findAll();
        }
        String currentUserId = currentUserService.getCurrentUserIdOrThrow();
        log.debug("Fetching reviews for ownerUserId={}", currentUserId);
        return reviewRepository.findByOwnerUserId(currentUserId);
    }

    @Override
    public Optional<Review> getReview(String id) {
        if (currentUserService.isAdmin()) {
            log.debug("Fetching review by id={} as admin", id);
            return reviewRepository.findById(id);
        }
        String currentUserId = currentUserService.getCurrentUserIdOrThrow();
        log.debug("Fetching review by id={} for ownerUserId={}", id, currentUserId);
        return reviewRepository.findByIdAndOwnerUserId(id, currentUserId);
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
            aiResult = openAiService.generateImprovedReview(request.title(),
                    request.originalContent());
            if (!aiResult.fromAi()) {
                openAiStatus = IntegrationStatus.Status.FAILED;
                appendOpenAiWarning(warnings, aiResult.reason());
                log.warn("OpenAI rate-limited or unavailable; using fallback content");
            }
        } catch (MissingApiKeyException ex) {
            openAiStatus = IntegrationStatus.Status.SKIPPED;
            appendWarningIfMissing(warnings, "OpenAI 설정을 찾을 수 없어 개선 단계를 건너뛰었습니다.");
            log.warn("OpenAI key missing, skipping AI improvement and using fallback content");
        } catch (Exception ex) {
            openAiStatus = IntegrationStatus.Status.FAILED;
            appendWarningIfMissing(warnings, "OpenAI 호출을 처리하지 못했습니다.");
            log.warn("OpenAI content generation failed, continuing with fallback content", ex);
        }

        long tokenCount = 0L;
        BigDecimal usdCost = BigDecimal.ZERO;
        BigDecimal krwCost = null;
        try {
            krwCost = convertToKrw(usdCost);
        } catch (Exception ex) {
            currencyStatus = IntegrationStatus.Status.FAILED;
            appendWarningIfMissing(warnings, "환율 정보를 불러오지 못했습니다.");
            log.warn("Currency conversion failed, continuing without KRW cost", ex);
        }

        String markdown = buildMarkdown(request.title(), aiResult.improvedContent());
        String fileId = null;
        try {
            fileId = uploadToDrive(request.title(), markdown);
            if (fileId == null) {
                driveStatus = IntegrationStatus.Status.SKIPPED;
                appendWarningIfMissing(warnings, "Google Drive 설정이 없어 업로드를 건너뛰었습니다.");
            }
        } catch (Exception ex) {
            driveStatus = IntegrationStatus.Status.FAILED;
            appendWarningIfMissing(warnings, "Google Drive 업로드에 실패했습니다.");
            log.warn("Google Drive upload failed, continuing without file link", ex);
        }

        IntegrationStatus integrationStatus = new IntegrationStatus(openAiStatus, currencyStatus,
                driveStatus,
                warnings.toString());
        String ownerUserId = currentUserService.getCurrentUserIdOrThrow();

        Review review = new Review(
                null,
                request.title(),
                request.originalContent(),
                aiResult.improvedContent(),
                tokenCount,
                usdCost,
                krwCost,
                fileId,
                ownerUserId,
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

    @Override
    @Transactional
    public DeleteReviewResult deleteReview(String id) {
        log.info("Deleting review id={}", id);
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review not found"));

        boolean driveDeleted = false;
        String googleFileId = review.googleFileId();
        StringBuilder warnings = new StringBuilder();

        if (googleFileId != null && !googleFileId.isBlank()) {
            try {
                googleDriveService.deleteFile(googleFileId);
                driveDeleted = true;
            } catch (Exception ex) {
                log.warn("Failed to delete Google Drive file for review id={} fileId={}.", id,
                        googleFileId, ex);
                appendWarningIfMissing(warnings, "Google Drive file could not be removed.");
            }
        }

        reviewRepository.deleteById(id);
        log.info("Review id={} deleted successfully", id);
        return DeleteReviewResult.builder()
                .deleted(true)
                .driveDeleted(driveDeleted)
                .warnings(warnings.isEmpty() ? List.of() : List.of(warnings.toString()))
                .build();
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
        Optional<String> fileId = googleDriveService.uploadMarkdown(filename, markdown);
        if (fileId.isPresent()) {
            log.info("Markdown uploaded to Google Drive with fileId={}", fileId.get());
            return fileId.get();
        }
        log.warn("Google Drive upload skipped because credentials are missing.");
        return null;
    }

    private AiReviewResult fallbackAiResult(ReviewRequest request) {
        String content = "[IMPROVEMENT_SKIPPED]\n" + request.originalContent();
        return new AiReviewResult(content, false, "fallback", "SKIPPED");
    }

    private void appendWarningIfMissing(StringBuilder warnings, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (warnings.toString().contains(message)) {
            return;
        }
        if (!warnings.isEmpty()) {
            warnings.append("\n");
        }
        warnings.append(message);
    }

    private void appendOpenAiWarning(StringBuilder warnings, String reason) {
        String message = switch (reason) {
            case "RATE_LIMIT" -> "OpenAI 요청이 지나치게 많습니다. 호출 빈도를 줄여주세요.";
            case "INSUFFICIENT_QUOTA" -> "OpenAI 크레딧이 부족합니다. 요금제를 확인하세요.";
            case "INVALID_KEY" -> "유효하지 않은 OpenAI API 키입니다. 설정을 확인하세요.";
            default -> "OpenAI 응답을 처리하지 못했습니다.";
        };
        appendWarningIfMissing(warnings, message);
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
