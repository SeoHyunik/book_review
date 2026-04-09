package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestionServiceImpl implements NewsIngestionService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final NewsEventRepository newsEventRepository;
    private final NewsSourceProviderSelector newsSourceProviderSelector;
    private final MacroAiService macroAiService;

    @Qualifier("ingestionExecutor")
    private final Executor ingestionExecutor;

    @Value("${app.news.naver.max-age-hours:12}")
    private long naverMaxAgeHours;

    @Value("${app.news.naver.fallback-max-age-hours:24}")
    private long naverFallbackMaxAgeHours;

    @Value("${app.news.global.max-age-hours:24}")
    private long globalMaxAgeHours;

    @Value("${app.news.global.fallback-max-age-hours:36}")
    private long globalFallbackMaxAgeHours;

    @Value("${app.ingestion.analysis-retry.max-retries:2}")
    private int maxAnalysisRetries;

    @Value("${app.ingestion.analysis-retry.min-delay-minutes:60}")
    private long analysisRetryMinDelayMinutes;

    private Clock clock = DEFAULT_CLOCK;

    @Override
    @Transactional
    public NewsEvent ingestExternalItem(ExternalNewsItem item) {
        if (item == null) {
            throw new IllegalArgumentException("ExternalNewsItem must not be null");
        }

        String resolvedExternalId = resolveExternalId(item);
        log.info("[INGEST] start externalId={} source='{}' title='{}'", resolvedExternalId,
                defaultText(item.source(), ""), defaultText(item.title(), ""));

        Optional<NewsEvent> existing = findDuplicate(item, resolvedExternalId);
        if (existing.isPresent()) {
            log.info("[INGEST] duplicate detected existingId={} externalId={}", existing.get().id(),
                    resolvedExternalId);
            return existing.get();
        }

        Instant now = Instant.now();
        String normalizedTitle = defaultText(item.title(), "Untitled");
        NewsEvent event = new NewsEvent(
                null,
                resolvedExternalId,
                normalizedTitle,
                normalizeSummary(item.summary(), normalizedTitle),
                defaultText(item.source(), "External"),
                defaultText(item.url(), ""),
                item.publishedAt() == null ? now : item.publishedAt(),
                now,
                NewsStatus.INGESTED,
                null,
                null,
                null
        );

        NewsEvent saved = newsEventRepository.save(event);
        log.info("[INGEST] completed id={} status={}", saved.id(), saved.status());
        return saved;
    }

    @Override
    @Transactional
    public List<NewsEvent> ingestTopHeadlines(int limit) {
        log.info("[INGEST] batch start limit={}", limit);
        List<ExternalNewsItem> externalItems = loadScheduledHeadlineFeed(limit);
        List<NewsEvent> results = new ArrayList<>();
        List<String> interpretationTargets = new ArrayList<>();

        for (ExternalNewsItem item : externalItems) {
            boolean duplicateBeforeIngest = findDuplicate(item, resolveExternalId(item)).isPresent();
            NewsEvent ingested = ingestExternalItem(item);
            results.add(ingested);

            if (!duplicateBeforeIngest && isAsyncInterpretationTarget(ingested)) {
                interpretationTargets.add(ingested.id());
            }
        }

        submitAsyncInterpretations(interpretationTargets);
        log.info("[INGEST] batch completed requested={} processed={} asyncSubmitted={}",
                limit, results.size(), interpretationTargets.size());
        return results;
    }

    @Override
    @Transactional
    public int retryFailedAnalyses() {
        Instant now = Instant.now(clock);
        Instant retryCutoff = now.minus(Duration.ofMinutes(resolveAnalysisRetryMinDelayMinutes()));
        List<NewsEvent> eligibleFailedItems = newsEventRepository.findByStatus(NewsStatus.FAILED).stream()
                .filter(event -> isEligibleForAnalysisRetry(event, retryCutoff))
                .map(event -> reserveAnalysisRetry(event, now))
                .toList();

        if (eligibleFailedItems.isEmpty()) {
            log.debug("[INGEST-RETRY] skipped reason=no-eligible-failed-items cutoff={} maxRetries={}",
                    retryCutoff, resolveMaxAnalysisRetries());
            return 0;
        }

        submitAsyncInterpretations(eligibleFailedItems.stream().map(NewsEvent::id).toList());
        log.info("[INGEST-RETRY] submitted eligible={} cutoff={} maxRetries={}",
                eligibleFailedItems.size(), retryCutoff, resolveMaxAnalysisRetries());
        return eligibleFailedItems.size();
    }

    @Override
    @Transactional
    public NewsEvent ingestManual(AdminIngestionRequest request) {
        log.info("[INGEST] manual start source='{}' title='{}'", defaultText(request.source(), ""),
                defaultText(request.title(), ""));
        ExternalNewsItem item = new ExternalNewsItem(
                null,
                request.source(),
                request.title(),
                request.summary(),
                request.url(),
                toInstant(request.publishedAt())
        );
        NewsEvent saved = ingestExternalItem(item);
        log.info("[INGEST] manual completed id={} status={}", saved.id(), saved.status());
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "newsDetail", key = "#id", beforeInvocation = true)
    public boolean deleteById(String id) {
        if (!StringUtils.hasText(id) || !newsEventRepository.existsById(id)) {
            log.info("[ADMIN] delete skipped missing id={}", id);
            return false;
        }
        newsEventRepository.deleteById(id);
        log.info("[ADMIN] delete completed id={}", id);
        return true;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "newsDetail", allEntries = true, beforeInvocation = true)
    public int deleteByIds(List<String> ids) {
        List<String> requestedIds = ids == null ? List.of() : ids;
        List<String> sanitizedIds = requestedIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (sanitizedIds.isEmpty()) {
            log.info("[ADMIN] bulk delete skipped reason=no-valid-ids requested={}", requestedIds.size());
            return 0;
        }

        List<String> existingIds = StreamSupport.stream(newsEventRepository.findAllById(sanitizedIds).spliterator(), false)
                .map(NewsEvent::id)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (existingIds.isEmpty()) {
            log.info("[ADMIN] bulk delete skipped reason=no-existing-ids requested={} sanitized={}",
                    requestedIds.size(), sanitizedIds.size());
            return 0;
        }

        newsEventRepository.deleteAllById(existingIds);
        log.info("[ADMIN] bulk delete completed requested={} sanitized={} deleted={}",
                requestedIds.size(), sanitizedIds.size(), existingIds.size());
        return existingIds.size();
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            log.info("[CLEANUP] expired delete skipped reason=no-cutoff");
            return 0;
        }

        List<String> expiredIds = newsEventRepository.findByIngestedAtBefore(cutoff).stream()
                .map(NewsEvent::id)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (expiredIds.isEmpty()) {
            log.debug("[CLEANUP] expired delete skipped reason=no-expired-news cutoff={}", cutoff);
            return 0;
        }

        int deletedCount = deleteByIds(expiredIds);
        log.info("[CLEANUP] expired delete completed cutoff={} requested={} deleted={}",
                cutoff, expiredIds.size(), deletedCount);
        return deletedCount;
    }

    private void submitAsyncInterpretations(List<String> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }

        Runnable submitTask = () -> {
            for (String id : eventIds) {
                ingestionExecutor.execute(() -> {
                    log.info("[INTERPRET-ASYNC] submitted id={}", id);
                    macroAiService.interpretAndSave(id);
                });
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }

        submitTask.run();
    }

    private boolean isAsyncInterpretationTarget(NewsEvent event) {
        return event != null
                && StringUtils.hasText(event.id())
                && event.status() == NewsStatus.INGESTED
                && event.analysisResult() == null;
    }

    boolean isEligibleForAnalysisRetry(NewsEvent event, Instant retryCutoff) {
        if (event == null
                || !StringUtils.hasText(event.id())
                || event.status() != NewsStatus.FAILED
                || event.analysisResult() != null) {
            return false;
        }

        int retryCount = event.analysisRetryCount() == null ? 0 : event.analysisRetryCount();
        if (retryCount >= resolveMaxAnalysisRetries()) {
            return false;
        }

        Instant lastAttemptAt = event.analysisLastAttemptAt() != null
                ? event.analysisLastAttemptAt()
                : event.ingestedAt();
        return lastAttemptAt == null || !lastAttemptAt.isAfter(retryCutoff);
    }

    private NewsEvent reserveAnalysisRetry(NewsEvent event, Instant attemptedAt) {
        NewsEvent reserved = new NewsEvent(
                event.id(),
                event.externalId(),
                event.title(),
                event.summary(),
                event.source(),
                event.url(),
                event.publishedAt(),
                event.ingestedAt(),
                event.status(),
                event.analysisResult(),
                (event.analysisRetryCount() == null ? 0 : event.analysisRetryCount()) + 1,
                attemptedAt
        );
        return newsEventRepository.save(reserved);
    }

    private Optional<NewsEvent> findDuplicate(ExternalNewsItem item, String resolvedExternalId) {
        if (StringUtils.hasText(resolvedExternalId)) {
            Optional<NewsEvent> byExternalId = newsEventRepository.findByExternalId(resolvedExternalId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }

        if (StringUtils.hasText(item.url())) {
            Optional<NewsEvent> byUrl = newsEventRepository.findByUrl(item.url());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }

        return Optional.empty();
    }

    private List<ExternalNewsItem> loadScheduledHeadlineFeed(int limit) {
        List<ExternalNewsItem> selected = newsSourceProviderSelector.fetchTopHeadlines(limit);
        List<ExternalNewsItem> freshOnly = selected.stream()
                .filter(this::isFreshEnoughForBatch)
                .toList();
        int selectedCount = selected.size();
        int keptCount = freshOnly.size();
        int removedCount = selectedCount - keptCount;
        String finalCause = selectedCount == 0
                ? "selector-returned-empty"
                : "freshness-gate-removed-all";
        if (selectedCount == 0 || keptCount == 0) {
            log.warn("[INGEST] zero-result summary stage={} reason={} selected={} kept={} removed={} selectedSourceSummary={}",
                    selectedCount == 0 ? "pre-filter" : "post-filter",
                    finalCause, selectedCount, keptCount, removedCount, summarizeSources(selected));
        }
        if (selected.isEmpty()) {
            log.warn("[INGEST] final freshness gate stage=pre-filter reason=selector-returned-empty finalCause={} selected=0 kept=0 removed=0",
                    finalCause);
        } else if (freshOnly.isEmpty()) {
            log.warn("[INGEST] final freshness gate stage=post-filter reason=removed-all finalCause={} selected={} kept=0 removed={}",
                    finalCause, selectedCount, removedCount);
        } else if (keptCount != selectedCount) {
            log.info("[INGEST] final freshness gate stage=post-filter reason=partial-filter selected={} kept={} removed={}",
                    selectedCount, keptCount, removedCount);
        }
        log.info("[INGEST] selected sourceSummary={} finalCause={} selected={} kept={} removed={}",
                summarizeSources(freshOnly), finalCause, selectedCount, keptCount, removedCount);
        return freshOnly;
    }

    private String resolveExternalId(ExternalNewsItem item) {
        if (StringUtils.hasText(item.externalId())) {
            return item.externalId().trim();
        }
        String seed = defaultText(item.source(), "") + "|"
                + defaultText(item.title(), "") + "|"
                + (item.publishedAt() == null ? "" : item.publishedAt().toString());
        return sha256(seed);
    }

    private String sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeSummary(String summary, String title) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        String normalizedSummary = summary.trim();
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "";
        return normalizedSummary.equals(normalizedTitle) ? "" : normalizedSummary;
    }

    private Map<String, Integer> summarizeSources(List<ExternalNewsItem> items) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        if (items == null) {
            return summary;
        }
        for (ExternalNewsItem item : items) {
            String source = item == null ? "UNKNOWN" : defaultText(item.source(), "UNKNOWN");
            summary.merge(source, 1, Integer::sum);
        }
        return summary;
    }

    private boolean isFreshEnoughForBatch(ExternalNewsItem item) {
        if (item == null || item.publishedAt() == null) {
            return false;
        }
        return !item.publishedAt().isBefore(Instant.now().minus(resolveMaxAge(item)));
    }

    private java.time.Duration resolveMaxAge(ExternalNewsItem item) {
        String source = item == null ? "" : defaultText(item.source(), "");
        boolean domesticSource = "NAVER".equalsIgnoreCase(source);
        long hours = domesticSource ? naverFallbackMaxAgeHours : globalFallbackMaxAgeHours;
        return java.time.Duration.ofHours(hours > 0 ? hours : (domesticSource ? 24L : 36L));
    }

    private int resolveMaxAnalysisRetries() {
        return Math.max(0, maxAnalysisRetries);
    }

    private long resolveAnalysisRetryMinDelayMinutes() {
        return analysisRetryMinDelayMinutes > 0 ? analysisRetryMinDelayMinutes : 60L;
    }
}
