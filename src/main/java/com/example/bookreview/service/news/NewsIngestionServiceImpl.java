package com.example.bookreview.service.news;

import com.example.bookreview.dto.domain.AnalysisResult;
import com.example.bookreview.dto.domain.NewsEvent;
import com.example.bookreview.dto.domain.NewsStatus;
import com.example.bookreview.dto.internal.ExternalNewsItem;
import com.example.bookreview.dto.request.AdminIngestionRequest;
import com.example.bookreview.repository.NewsEventRepository;
import com.example.bookreview.service.macro.MacroAiService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestionServiceImpl implements NewsIngestionService {

    private static final String DEFAULT_INGESTED_BY = "admin-manual";
    private static final String EXTERNAL_INGESTED_BY = "external-api";

    private final NewsEventRepository newsEventRepository;
    private final MacroAiService macroAiService;
    private final NewsApiService newsApiService;

    @Override
    @Transactional
    public String ingestOne(AdminIngestionRequest request) {
        String externalId = buildDeterministicExternalId(request.source(), request.title(),
                request.publishedAt(), "");
        log.info("Starting news ingestion: source='{}', title='{}', externalId='{}'", request.source(),
                request.title(), externalId);

        return newsEventRepository.findByExternalId(externalId)
                .map(original -> {
                    log.info("Duplicate detected for externalId='{}', originalId={}", externalId,
                            original.id());
                    NewsEvent duplicate = NewsEvent.builder()
                            .externalId(externalId)
                            .source(request.source())
                            .title(request.title())
                            .summary(request.summary())
                            .content(request.content())
                            .url(request.url())
                            .publishedAt(request.publishedAt())
                            .ingestedAt(LocalDateTime.now())
                            .status(NewsStatus.DUPLICATE)
                            .duplicateOfId(original.id())
                            .ingestedBy(resolveIngestedBy(request.ingestedBy()))
                            .build();
                    NewsEvent saved = newsEventRepository.save(duplicate);
                    log.info("NewsEvent final persisted status={} id={}", saved.status(), saved.id());
                    return saved.id();
                })
                .orElseGet(() -> {
                    NewsEvent ingested = NewsEvent.builder()
                            .externalId(externalId)
                            .source(request.source())
                            .title(request.title())
                            .summary(request.summary())
                            .content(request.content())
                            .url(request.url())
                            .publishedAt(request.publishedAt())
                            .ingestedAt(LocalDateTime.now())
                            .status(NewsStatus.INGESTED)
                            .ingestedBy(resolveIngestedBy(request.ingestedBy()))
                            .build();
                    NewsEvent savedIngested = newsEventRepository.save(ingested);
                    log.info("NewsEvent saved as INGESTED with id={}", savedIngested.id());
                    return analyzeAndFinalize(savedIngested).id();
                });
    }

    @Override
    @Transactional
    public int ingestLatestFromApi(int pageSize) {
        log.info("Starting external ingestion batch with pageSize={}", pageSize);
        List<ExternalNewsItem> fetchedItems = newsApiService.fetchLatestNews(pageSize);

        int duplicateCount = 0;
        int createdCount = 0;
        int analyzedCount = 0;
        int failedCount = 0;

        for (ExternalNewsItem item : fetchedItems) {
            String externalId = resolveExternalId(item);
            if (newsEventRepository.findByExternalId(externalId).isPresent()) {
                duplicateCount++;
                continue;
            }

            NewsEvent ingested = NewsEvent.builder()
                    .externalId(externalId)
                    .source(defaultText(item.source(), "External RSS"))
                    .title(defaultText(item.title(), "Untitled"))
                    .summary(defaultText(item.summary(), ""))
                    .content(defaultText(item.content(), ""))
                    .url(defaultText(item.url(), ""))
                    .publishedAt(item.publishedAt() == null ? LocalDateTime.now() : item.publishedAt())
                    .ingestedAt(LocalDateTime.now())
                    .status(NewsStatus.INGESTED)
                    .ingestedBy(EXTERNAL_INGESTED_BY)
                    .build();
            NewsEvent savedIngested = newsEventRepository.save(ingested);
            NewsEvent finalized = analyzeAndFinalize(savedIngested);

            createdCount++;
            if (finalized.status() == NewsStatus.ANALYZED) {
                analyzedCount++;
            } else if (finalized.status() == NewsStatus.FAILED) {
                failedCount++;
            }
        }

        log.info(
                "Completed external ingestion batch fetched={} created={} duplicates={} analyzed={} failed={}",
                fetchedItems.size(), createdCount, duplicateCount, analyzedCount, failedCount);
        return createdCount;
    }

    private NewsEvent analyzeAndFinalize(NewsEvent savedIngested) {
        try {
            log.info("Starting AI interpretation for newsEventId={}", savedIngested.id());
            AnalysisResult analysisResult = macroAiService.interpretNewsEvent(savedIngested);
            NewsEvent analyzed = copyWithAnalysisAndStatus(savedIngested, analysisResult,
                    NewsStatus.ANALYZED);
            NewsEvent savedAnalyzed = newsEventRepository.save(analyzed);
            log.info("AI interpretation success for newsEventId={}", savedAnalyzed.id());
            log.info("NewsEvent final persisted status={} id={}", savedAnalyzed.status(),
                    savedAnalyzed.id());
            return savedAnalyzed;
        } catch (Exception ex) {
            log.error("AI interpretation failed for newsEventId={}", savedIngested.id(), ex);
            NewsEvent failed = copyWithAnalysisAndStatus(savedIngested, null, NewsStatus.FAILED);
            NewsEvent savedFailed = newsEventRepository.save(failed);
            log.info("NewsEvent final persisted status={} id={}", savedFailed.status(),
                    savedFailed.id());
            return savedFailed;
        }
    }

    private NewsEvent copyWithAnalysisAndStatus(NewsEvent base, AnalysisResult analysisResult,
            NewsStatus status) {
        return NewsEvent.builder()
                .id(base.id())
                .externalId(base.externalId())
                .source(base.source())
                .title(base.title())
                .summary(base.summary())
                .content(base.content())
                .url(base.url())
                .publishedAt(base.publishedAt())
                .ingestedAt(base.ingestedAt())
                .status(status)
                .analysisResult(analysisResult)
                .duplicateOfId(base.duplicateOfId())
                .ingestedBy(base.ingestedBy())
                .build();
    }

    private String resolveExternalId(ExternalNewsItem item) {
        if (item != null && StringUtils.hasText(item.providerItemId())) {
            return item.providerItemId().trim();
        }
        String source = item == null ? "" : item.source();
        String title = item == null ? "" : item.title();
        LocalDateTime publishedAt = item == null ? null : item.publishedAt();
        String url = item == null ? "" : item.url();
        return buildDeterministicExternalId(source, title, publishedAt, url);
    }

    private String resolveIngestedBy(String ingestedBy) {
        if (ingestedBy == null || ingestedBy.isBlank()) {
            return DEFAULT_INGESTED_BY;
        }
        return ingestedBy;
    }

    private String buildDeterministicExternalId(String source, String title,
            LocalDateTime publishedAt, String url) {
        String seed = (defaultText(source, "") + "|" + defaultText(title, "") + "|"
                + (publishedAt == null ? "" : publishedAt.toString()) + "|" + defaultText(url, ""))
                .trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}