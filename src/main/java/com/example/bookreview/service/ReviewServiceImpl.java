package com.example.bookreview.service;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.AiReviewResult;
import com.example.bookreview.dto.ReviewRequest;
import com.example.bookreview.repository.ReviewRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private static final int MAX_FILENAME_BYTES = 255;

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
        log.info("[SERVICE] Starting review creation for title='{}'", request.getTitle());
        validateTitleForUpload(request.getTitle());

        AiReviewResult aiResult = openAiService.generateImprovedReview(request.getTitle(), request.getOriginalContent());
        long tokenCount = aiResult.totalTokens();
        BigDecimal usdCost = aiResult.usdCost();
        log.debug("[SERVICE] AI review generated with tokenCount={} and usdCost={}", tokenCount, usdCost);
        BigDecimal krwCost = currencyService.convertUsdToKrw(usdCost);
        log.debug("[SERVICE] Converted cost to KRW: {}", krwCost);

        String markdown = buildMarkdown(request.getTitle(), aiResult.improvedContent());
        String filename = replaceExtension(slugify(request.getTitle()), ".md");
        String fileId = googleDriveService.uploadMarkdown(filename, markdown);
        log.info("[SERVICE] Markdown uploaded to Google Drive with fileId={}", fileId);

        Review review = new Review(
                null,
                request.getTitle(),
                request.getOriginalContent(),
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

    private void validateTitleForUpload(String title) {
        if (INVALID_FILE_CHARS.matcher(title).find()) {
            throw new IllegalArgumentException("제목에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
    }

    private String slugify(String input) {
        String normalized = Optional.ofNullable(input).orElse("").trim();
        String withHyphens = normalized.replaceAll("\\s+", "-");
        String encoded = URLEncoder.encode(withHyphens, StandardCharsets.UTF_8).replace("+", "-");
        String safeEncoded = truncateEncoded(encoded);
        return safeEncoded;
    }

    private String truncateEncoded(String encoded) {
        if (encoded.length() <= MAX_FILENAME_BYTES) {
            return encoded;
        }
        String truncated = encoded.substring(0, MAX_FILENAME_BYTES);
        int percentIndex = truncated.lastIndexOf('%');
        if (percentIndex != -1 && MAX_FILENAME_BYTES - percentIndex < 3) {
            truncated = truncated.substring(0, percentIndex);
        }
        return truncated;
    }

    private String replaceExtension(String filename, String newExtension) {
        String normalizedExtension = newExtension.startsWith(".") ? newExtension : "." + newExtension;
        int lastDot = filename.lastIndexOf('.');
        String basename = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        return basename + normalizedExtension;
    }
}
