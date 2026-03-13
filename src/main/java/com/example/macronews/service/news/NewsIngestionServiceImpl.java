package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.macro.MacroAiService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DOMESTIC_START_HOUR = 5;
    private static final int DEFAULT_DOMESTIC_END_HOUR = 22;

    private final NewsEventRepository newsEventRepository;
    private final NewsApiService newsApiService;
    private final MacroAiService macroAiService;

    @Qualifier("ingestionExecutor")
    private final Executor ingestionExecutor;

    @Value("${app.ingestion.domestic-start-hour:5}")
    private int domesticStartHour;

    @Value("${app.ingestion.domestic-end-hour:22}")
    private int domesticEndHour;

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
        if (isDomesticWindow()) {
            log.info("[INGEST] using domestic headline window hour={} start={} end={}",
                    currentSeoulHour(), resolveHour(domesticStartHour, DEFAULT_DOMESTIC_START_HOUR),
                    resolveHour(domesticEndHour, DEFAULT_DOMESTIC_END_HOUR));
            return newsApiService.fetchDomesticTopHeadlines(limit);
        }
        log.info("[INGEST] using foreign headline window hour={} start={} end={}",
                currentSeoulHour(), resolveHour(domesticStartHour, DEFAULT_DOMESTIC_START_HOUR),
                resolveHour(domesticEndHour, DEFAULT_DOMESTIC_END_HOUR));
        return newsApiService.fetchForeignTopHeadlines(limit);
    }

    private boolean isDomesticWindow() {
        int startHour = resolveHour(domesticStartHour, DEFAULT_DOMESTIC_START_HOUR);
        int endHour = resolveHour(domesticEndHour, DEFAULT_DOMESTIC_END_HOUR);
        int currentHour = currentSeoulHour();

        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour <= endHour;
        }
        return currentHour >= startHour || currentHour <= endHour;
    }

    private int currentSeoulHour() {
        return LocalTime.now(SEOUL_ZONE_ID).getHour();
    }

    private int resolveHour(int configuredHour, int fallbackHour) {
        return configuredHour >= 0 && configuredHour <= 23 ? configuredHour : fallbackHour;
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
}
