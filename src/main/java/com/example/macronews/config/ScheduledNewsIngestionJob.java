package com.example.macronews.config;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.service.news.NewsApiService;
import com.example.macronews.service.news.NewsIngestionService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final NewsApiService newsApiService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.ingestion.scheduler.page-size:10}")
    private int pageSize;

    @Scheduled(cron = "${app.ingestion.scheduler.cron:0 0 * * * *}")
    public void ingestTopHeadlines() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[SCHEDULER] skipping ingestion because a local run is already in progress");
            return;
        }

        try {
            if (!newsApiService.isConfigured()) {
                log.info("[SCHEDULER] skipping ingestion because news.api.key is not configured");
                return;
            }

            int resolvedPageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
            log.info("[SCHEDULER] starting automatic top-headline ingestion pageSize={}", resolvedPageSize);
            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(resolvedPageSize);
            log.info("[SCHEDULER] completed automatic ingestion processed={}", ingested.size());
        } catch (RuntimeException ex) {
            log.error("[SCHEDULER] automatic ingestion failed", ex);
        } finally {
            running.set(false);
        }
    }
}
