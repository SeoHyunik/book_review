package com.example.macronews.config;

import com.example.macronews.service.news.MarketSummarySnapshotService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledMarketSummarySnapshotJob {

    private final MarketSummarySnapshotService marketSummarySnapshotService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong runSequence = new AtomicLong(0);

    @Value("${app.featured.market-summary.snapshot-enabled:true}")
    private boolean snapshotEnabled;

    @Value("${app.featured.market-summary.snapshot-refresh-enabled:true}")
    private boolean refreshEnabled;

    @Scheduled(cron = "${app.featured.market-summary.snapshot-refresh-cron:0 10 */3 * * *}")
    public void refreshSnapshot() {
        long runId = runSequence.incrementAndGet();
        if (!snapshotEnabled || !refreshEnabled) {
            log.debug("[MARKET_SUMMARY_SCHEDULER] runId={} skipped reason=disabled", runId);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("[MARKET_SUMMARY_SCHEDULER] runId={} skipped reason=already-running", runId);
            return;
        }

        try {
            var refreshDecision = marketSummarySnapshotService.evaluateScheduledRefresh();
            if (!refreshDecision.shouldRefresh()) {
                log.info("[MARKET_SUMMARY_SCHEDULER] runId={} skipped reason={} latestSnapshotGeneratedAt={} latestAnalyzedNewsBasis={}",
                        runId,
                        refreshDecision.reason(),
                        refreshDecision.latestSnapshotGeneratedAt(),
                        refreshDecision.latestAnalyzedNewsBasis());
                return;
            }

            var snapshot = marketSummarySnapshotService.refreshSnapshot();
            if (snapshot.isPresent()) {
                log.info("[MARKET_SUMMARY_SCHEDULER] runId={} completed generatedAt={} sourceCount={}",
                        runId, snapshot.get().generatedAt(), snapshot.get().sourceCount());
            } else {
                log.info("[MARKET_SUMMARY_SCHEDULER] runId={} skipped reason=no-eligible-analyzed-items-or-ai-unavailable", runId);
            }
        } catch (RuntimeException ex) {
            log.warn("[MARKET_SUMMARY_SCHEDULER] runId={} failed", runId, ex);
        } finally {
            running.set(false);
        }
    }
}
