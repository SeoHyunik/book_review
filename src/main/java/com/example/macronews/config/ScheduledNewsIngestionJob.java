package com.example.macronews.config;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.service.news.AutoIngestionControlService;
import com.example.macronews.service.news.AutoIngestionRunCommandResult;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.notification.AutoIngestionEmailNotificationService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledNewsIngestionJob {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MINIMUM_SCHEDULED_TARGET = 5;

    private final NewsIngestionService newsIngestionService;
    private final NewsSourceProviderSelector newsSourceProviderSelector;
    private final NewsQueryService newsQueryService;
    private final AutoIngestionControlService autoIngestionControlService;
    private final AutoIngestionEmailNotificationService autoIngestionEmailNotificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong runSequence = new AtomicLong(0);

    @Value("${app.ingestion.scheduler.page-size:10}")
    private int pageSize;

    @Scheduled(cron = "${app.ingestion.scheduler.cron:0 0 * * * *}")
    public void ingestTopHeadlines() {
        long runId = runSequence.incrementAndGet();
        int resolvedPageSize = resolveScheduledPageSize(runId);

        if (!autoIngestionControlService.isSchedulerEnabled()) {
            log.info("[SCHEDULER] runId={} skipped reason=scheduler-disabled", runId);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("[SCHEDULER] runId={} skipped reason=already-running", runId);
            return;
        }

        try {
            if (!newsSourceProviderSelector.isConfigured()) {
                log.info("[SCHEDULER] runId={} skipped reason=news-source-not-configured", runId);
                return;
            }

            AutoIngestionRunCommandResult startResult = autoIngestionControlService.beginScheduledRun(resolvedPageSize);
            if (startResult == AutoIngestionRunCommandResult.SCHEDULER_DISABLED) {
                log.info("[SCHEDULER] runId={} skipped reason=scheduler-disabled", runId);
                return;
            }
            if (startResult == AutoIngestionRunCommandResult.ALREADY_RUNNING) {
                log.warn("[SCHEDULER] runId={} skipped reason=auto-ingestion-already-running", runId);
                return;
            }

            log.info("[SCHEDULER] runId={} started pageSize={}", runId, resolvedPageSize);
            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(resolvedPageSize);
            AutoIngestionBatchStatusDto batchStatus = newsQueryService.getAutoIngestionBatchStatus(
                    resolvedPageSize,
                    ingested.size(),
                    ingested.stream().map(NewsEvent::id).toList());
            autoIngestionControlService.completeRun(batchStatus);
            autoIngestionEmailNotificationService.sendRunResult(autoIngestionControlService.getStatus(), batchStatus);
            log.info("[SCHEDULER] runId={} completed returned={} analyzed={} pending={} failed={} duplicates={}",
                    runId,
                    ingested.size(),
                    countByStatus(ingested, NewsStatus.ANALYZED),
                    countByStatus(ingested, NewsStatus.INGESTED),
                    countByStatus(ingested, NewsStatus.FAILED),
                    countByStatus(ingested, NewsStatus.DUPLICATE));
        } catch (RuntimeException ex) {
            autoIngestionControlService.failRun(resolvedPageSize);
            autoIngestionEmailNotificationService.sendRunResult(autoIngestionControlService.getStatus(), null);
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

    private int resolveScheduledPageSize(long runId) {
        int requestedPageSize = pageSize;
        int normalizedPageSize = requestedPageSize > 0 ? requestedPageSize : DEFAULT_PAGE_SIZE;
        if (requestedPageSize <= 0) {
            log.warn("[SCHEDULER] runId={} invalid-page-size configured={} fallback={}",
                    runId, requestedPageSize, normalizedPageSize);
        }
        if (normalizedPageSize < MINIMUM_SCHEDULED_TARGET) {
            log.info("[SCHEDULER] runId={} normalized-page-size requested={} normalized={} minimum={}",
                    runId, normalizedPageSize, MINIMUM_SCHEDULED_TARGET, MINIMUM_SCHEDULED_TARGET);
            return MINIMUM_SCHEDULED_TARGET;
        }
        return normalizedPageSize;
    }
}
