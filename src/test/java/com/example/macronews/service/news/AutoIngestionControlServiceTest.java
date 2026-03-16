package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AutoIngestionControlServiceTest {

    private AutoIngestionControlService autoIngestionControlService;

    @BeforeEach
    void setUp() {
        autoIngestionControlService = new AutoIngestionControlService(false);
        ReflectionTestUtils.setField(autoIngestionControlService, "clock",
                Clock.fixed(Instant.parse("2026-03-16T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("control service should toggle scheduler state safely")
    void schedulerToggle_updatesEnabledState() {
        assertThat(autoIngestionControlService.isSchedulerEnabled()).isFalse();
        assertThat(autoIngestionControlService.enableScheduler()).isTrue();
        assertThat(autoIngestionControlService.enableScheduler()).isFalse();
        assertThat(autoIngestionControlService.disableScheduler()).isTrue();
        assertThat(autoIngestionControlService.disableScheduler()).isFalse();
    }

    @Test
    @DisplayName("scheduled runs should respect disabled scheduler state")
    void beginScheduledRun_returnsDisabledWhenSchedulerOff() {
        assertThat(autoIngestionControlService.beginScheduledRun(5))
                .isEqualTo(AutoIngestionRunCommandResult.SCHEDULER_DISABLED);
    }

    @Test
    @DisplayName("manual run should mark progress and block duplicates until completion")
    void beginManualRun_blocksDuplicateRuns() {
        assertThat(autoIngestionControlService.beginManualRun(5))
                .isEqualTo(AutoIngestionRunCommandResult.STARTED);
        assertThat(autoIngestionControlService.beginManualRun(5))
                .isEqualTo(AutoIngestionRunCommandResult.ALREADY_RUNNING);
        assertThat(autoIngestionControlService.getStatus().runInProgress()).isTrue();
    }

    @Test
    @DisplayName("completed runs should expose latest batch summary")
    void completeRun_updatesLatestOutcomeAndBatchStatus() {
        autoIngestionControlService.beginManualRun(5);

        autoIngestionControlService.completeRun(new AutoIngestionBatchStatusDto(
                5, 2, 1, 1, 0, 0, true, List.of()
        ));

        assertThat(autoIngestionControlService.getStatus().latestOutcome())
                .isEqualTo(AutoIngestionRunOutcome.COMPLETED);
        assertThat(autoIngestionControlService.getLatestBatchStatus()).isPresent();
        assertThat(autoIngestionControlService.getStatus().runInProgress()).isFalse();
    }

    @Test
    @DisplayName("failed runs should clear stored batch status and expose failure state")
    void failRun_marksFailure() {
        autoIngestionControlService.beginManualRun(3);
        autoIngestionControlService.failRun(3);

        assertThat(autoIngestionControlService.getStatus().latestOutcome())
                .isEqualTo(AutoIngestionRunOutcome.FAILED);
        assertThat(autoIngestionControlService.getLatestBatchStatus()).isEmpty();
        assertThat(autoIngestionControlService.getStatus().runInProgress()).isFalse();
    }
}
