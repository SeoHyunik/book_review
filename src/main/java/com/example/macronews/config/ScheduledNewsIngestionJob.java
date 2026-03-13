package com.example.macronews.config;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ingestion.scheduler", name = "enabled", havingValue = "true")
public class ScheduledNewsIngestionJob {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final NewsIngestionService newsIngestionService;
    private final NewsSourceProviderSelector newsSourceProviderSelector;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong runSequence = new AtomicLong(0);

    @Value("${app.ingestion.scheduler.page-size:10}")
    private int pageSize;

    @Scheduled(cron = "${app.ingestion.scheduler.cron:0 0 * * * *}")
    public void ingestTopHeadlines() {
        long runId = runSequence.incrementAndGet();
        if (!running.compareAndSet(false, true)) {
            log.warn("[SCHEDULER] runId={} skipped reason=already-running", runId);
            return;
        }

        try {
            if (!newsSourceProviderSelector.isConfigured()) {
                log.info("[SCHEDULER] runId={} skipped reason=news-source-not-configured", runId);
                return;
            }

            int resolvedPageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
            if (pageSize <= 0) {
                log.warn("[SCHEDULER] runId={} invalid-page-size configured={} fallback={}", runId, pageSize,
                        resolvedPageSize);
            }
            log.info("[SCHEDULER] runId={} started pageSize={}", runId, resolvedPageSize);
            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(resolvedPageSize);
            log.info("[SCHEDULER] runId={} completed returned={} analyzed={} pending={} failed={} duplicates={}",
                    runId,
                    ingested.size(),
                    countByStatus(ingested, NewsStatus.ANALYZED),
                    countByStatus(ingested, NewsStatus.INGESTED),
                    countByStatus(ingested, NewsStatus.FAILED),
                    countByStatus(ingested, NewsStatus.DUPLICATE));
        } catch (RuntimeException ex) {
            log.error("[SCHEDULER] runId={} failed", runId, ex);
        } finally {
            running.set(false);
        }
    }

    private long countByStatus(List<NewsEvent> ingested, NewsStatus status) {
        return ingested.stream()
                .filter(event -> event != null && event.status() == status)
                .count();
    }
}
