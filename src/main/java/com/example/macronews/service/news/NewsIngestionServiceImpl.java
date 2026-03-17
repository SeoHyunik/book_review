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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
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

    private final NewsEventRepository newsEventRepository;
    private final NewsSourceProviderSelector newsSourceProviderSelector;
    private final MacroAiService macroAiService;

    @Qualifier("ingestionExecutor")
    private final Executor ingestionExecutor;

    @Value("${app.news.naver.max-age-hours:12}")
    private long naverMaxAgeHours;

    @Value("${app.news.global.max-age-hours:24}")
    private long globalMaxAgeHours;

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
        NewsEvent event = new NewsEvent(
                null,
                resolvedExternalId,
                defaultText(item.title(), "Untitled"),
                defaultText(item.summary(), ""),
                defaultText(item.source(), "External"),
                defaultText(item.url(), ""),
                item.publishedAt() == null ? now : item.publishedAt(),
                now,
                NewsStatus.INGESTED,
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
        if (freshOnly.size() != selected.size()) {
            log.info("[INGEST] final freshness gate removed={} kept={}",
                    selected.size() - freshOnly.size(), freshOnly.size());
        }
        log.info("[INGEST] selected sourceSummary={}", summarizeSources(freshOnly));
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
        long hours = "NAVER".equalsIgnoreCase(source) ? naverMaxAgeHours : globalMaxAgeHours;
        return java.time.Duration.ofHours(hours > 0 ? hours : ("NAVER".equalsIgnoreCase(source) ? 12L : 24L));
    }
}
