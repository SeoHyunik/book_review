package com.example.macronews.config;

import com.example.macronews.service.news.NewsIngestionService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledExpiredNewsCleanupJob {

    private static final long EXPIRATION_HOURS = 48L;

    private final NewsIngestionService newsIngestionService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong runSequence = new AtomicLong(0);

    @Scheduled(cron = "${app.news.cleanup.cron:0 0 * * * *}")
    public void deleteExpiredNews() {
        long runId = runSequence.incrementAndGet();
        if (!running.compareAndSet(false, true)) {
            log.warn("[NEWS_CLEANUP] runId={} skipped reason=already-running", runId);
            return;
        }

        Instant cutoff = Instant.now().minus(EXPIRATION_HOURS, ChronoUnit.HOURS);
        try {
            int deletedCount = newsIngestionService.deleteExpiredBefore(cutoff);
            if (deletedCount > 0) {
                log.info("[NEWS_CLEANUP] runId={} completed cutoff={} deleted={}", runId, cutoff, deletedCount);
            } else {
                log.debug("[NEWS_CLEANUP] runId={} completed cutoff={} deleted=0", runId, cutoff);
            }
        } catch (RuntimeException ex) {
            log.warn("[NEWS_CLEANUP] runId={} failed cutoff={}", runId, cutoff, ex);
        } finally {
            running.set(false);
        }
    }
}
