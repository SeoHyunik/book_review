package com.example.macronews.service.news;

import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionControlStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AutoIngestionControlService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final AtomicBoolean schedulerEnabled;
    private final AtomicBoolean runInProgress = new AtomicBoolean(false);
    private final AtomicReference<AutoIngestionRunOutcome> latestOutcome =
            new AtomicReference<>(AutoIngestionRunOutcome.IDLE);
    private final AtomicReference<Instant> latestStartedAt = new AtomicReference<>();
    private final AtomicReference<Instant> latestCompletedAt = new AtomicReference<>();
    private final AtomicReference<Integer> latestRequestedCount = new AtomicReference<>();
    private final AtomicReference<Integer> latestReturnedCount = new AtomicReference<>();
    private final AtomicReference<Integer> latestAnalyzedCount = new AtomicReference<>();
    private final AtomicReference<Integer> latestPendingCount = new AtomicReference<>();
    private final AtomicReference<Integer> latestFailedCount = new AtomicReference<>();
    private final AtomicReference<AutoIngestionBatchStatusDto> latestBatchStatus = new AtomicReference<>();

    private Clock clock = DEFAULT_CLOCK;

    public AutoIngestionControlService(@Value("${app.ingestion.scheduler.enabled:false}") boolean initialEnabled) {
        this.schedulerEnabled = new AtomicBoolean(initialEnabled);
    }

    public AutoIngestionControlStatusDto getStatus() {
        return new AutoIngestionControlStatusDto(
                schedulerEnabled.get(),
                runInProgress.get(),
                latestOutcome.get(),
                latestStartedAt.get(),
                latestCompletedAt.get(),
                latestRequestedCount.get(),
                latestReturnedCount.get(),
                latestAnalyzedCount.get(),
                latestPendingCount.get(),
                latestFailedCount.get()
        );
    }

    public Optional<AutoIngestionBatchStatusDto> getLatestBatchStatus() {
        return Optional.ofNullable(latestBatchStatus.get());
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled.get();
    }

    public boolean enableScheduler() {
        return schedulerEnabled.compareAndSet(false, true);
    }

    public boolean disableScheduler() {
        return schedulerEnabled.compareAndSet(true, false);
    }

    public AutoIngestionRunCommandResult beginScheduledRun(int requestedCount) {
        if (!schedulerEnabled.get()) {
            return AutoIngestionRunCommandResult.SCHEDULER_DISABLED;
        }
        return beginRun(requestedCount);
    }

    public AutoIngestionRunCommandResult beginManualRun(int requestedCount) {
        return beginRun(requestedCount);
    }

    public void completeRun(AutoIngestionBatchStatusDto batchStatus) {
        latestBatchStatus.set(batchStatus);
        latestReturnedCount.set(batchStatus.returnedCount());
        latestAnalyzedCount.set(batchStatus.analyzedCount());
        latestPendingCount.set(batchStatus.pendingCount());
        latestFailedCount.set(batchStatus.failedCount());
        latestCompletedAt.set(now());
        latestOutcome.set(resolveOutcome(batchStatus));
        runInProgress.set(false);
    }

    public void failRun(int requestedCount) {
        latestRequestedCount.set(requestedCount);
        latestReturnedCount.set(0);
        latestAnalyzedCount.set(0);
        latestPendingCount.set(0);
        latestFailedCount.set(0);
        latestBatchStatus.set(null);
        latestCompletedAt.set(now());
        latestOutcome.set(AutoIngestionRunOutcome.FAILED);
        runInProgress.set(false);
    }

    private AutoIngestionRunCommandResult beginRun(int requestedCount) {
        if (!runInProgress.compareAndSet(false, true)) {
            return AutoIngestionRunCommandResult.ALREADY_RUNNING;
        }
        latestOutcome.set(AutoIngestionRunOutcome.IN_PROGRESS);
        latestStartedAt.set(now());
        latestCompletedAt.set(null);
        latestRequestedCount.set(requestedCount);
        latestReturnedCount.set(null);
        latestAnalyzedCount.set(null);
        latestPendingCount.set(null);
        latestFailedCount.set(null);
        latestBatchStatus.set(null);
        return AutoIngestionRunCommandResult.STARTED;
    }

    private AutoIngestionRunOutcome resolveOutcome(AutoIngestionBatchStatusDto batchStatus) {
        if (batchStatus.returnedCount() == 0) {
            return AutoIngestionRunOutcome.NO_RESULTS;
        }
        if (batchStatus.failedCount() > 0) {
            return AutoIngestionRunOutcome.COMPLETED_WITH_FAILURES;
        }
        return AutoIngestionRunOutcome.COMPLETED;
    }

    private Instant now() {
        return Instant.now(clock);
    }

}
