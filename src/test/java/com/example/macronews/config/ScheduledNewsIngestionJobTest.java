package com.example.macronews.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionControlStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import com.example.macronews.service.news.AutoIngestionControlService;
import com.example.macronews.service.news.AutoIngestionRunCommandResult;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.notification.AutoIngestionEmailNotificationService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduledNewsIngestionJobTest {

    @Mock
    private NewsIngestionService newsIngestionService;

    @Mock
    private NewsSourceProviderSelector newsSourceProviderSelector;

    @Mock
    private NewsQueryService newsQueryService;

    @Mock
    private AutoIngestionControlService autoIngestionControlService;

    @Mock
    private AutoIngestionEmailNotificationService autoIngestionEmailNotificationService;

    @InjectMocks
    private ScheduledNewsIngestionJob scheduledNewsIngestionJob;

    @Test
    @DisplayName("Scheduled ingestion should skip when scheduler is disabled")
    void ingestTopHeadlines_skipsWhenSchedulerIsDisabled() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(false);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(autoIngestionControlService).isSchedulerEnabled();
        verifyNoInteractions(newsSourceProviderSelector, newsIngestionService, newsQueryService);
        verifyNoInteractions(autoIngestionEmailNotificationService);
    }

    @Test
    @DisplayName("Scheduled ingestion should skip when News API is not configured")
    void ingestTopHeadlines_skipsWhenNewsApiIsNotConfigured() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);
        given(newsSourceProviderSelector.isConfigured()).willReturn(false);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(autoIngestionControlService).isSchedulerEnabled();
        verify(newsSourceProviderSelector).isConfigured();
        verifyNoInteractions(newsIngestionService);
        verifyNoInteractions(autoIngestionEmailNotificationService);
    }

    @Test
    @DisplayName("Scheduled ingestion should use configured page size when enabled")
    void ingestTopHeadlines_usesConfiguredPageSize() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);
        given(newsSourceProviderSelector.isConfigured()).willReturn(true);
        given(autoIngestionControlService.beginScheduledRun(12)).willReturn(AutoIngestionRunCommandResult.STARTED);
        List<NewsEvent> ingested = List.of(sampleNewsEvent("event-1"));
        given(newsIngestionService.ingestTopHeadlines(12)).willReturn(ingested);
        AutoIngestionBatchStatusDto batchStatus = new AutoIngestionBatchStatusDto(12, 1, 1, 0, 0, 1, false, List.of());
        given(newsQueryService.getAutoIngestionBatchStatus(12, 1, List.of("event-1")))
                .willReturn(batchStatus);
        given(autoIngestionControlService.getStatus()).willReturn(new AutoIngestionControlStatusDto(
                true, false, AutoIngestionRunOutcome.COMPLETED,
                Instant.parse("2026-03-16T00:00:00Z"), Instant.parse("2026-03-16T00:01:00Z"),
                12, 1, 0, 1, 0));

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(autoIngestionControlService).beginScheduledRun(12);
        verify(newsSourceProviderSelector).isConfigured();
        verify(newsIngestionService).ingestTopHeadlines(12);
        verify(autoIngestionControlService).completeRun(org.mockito.ArgumentMatchers.any(AutoIngestionBatchStatusDto.class));
        verify(autoIngestionEmailNotificationService)
                .sendRunResult(org.mockito.ArgumentMatchers.any(AutoIngestionControlStatusDto.class),
                        org.mockito.ArgumentMatchers.eq(batchStatus));
    }

    @Test
    @DisplayName("Scheduled ingestion should keep running flag clear after failures")
    void ingestTopHeadlines_clearsRunningFlagAfterFailure() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);
        given(newsSourceProviderSelector.isConfigured()).willReturn(true);
        given(autoIngestionControlService.beginScheduledRun(12)).willReturn(AutoIngestionRunCommandResult.STARTED);
        given(newsIngestionService.ingestTopHeadlines(12)).willThrow(new IllegalStateException("boom"));
        given(autoIngestionControlService.getStatus()).willReturn(new AutoIngestionControlStatusDto(
                true, false, AutoIngestionRunOutcome.FAILED,
                Instant.parse("2026-03-16T00:00:00Z"), Instant.parse("2026-03-16T00:01:00Z"),
                12, 0, 0, 0, 0));

        scheduledNewsIngestionJob.ingestTopHeadlines();

        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(scheduledNewsIngestionJob, "running");
        verify(newsIngestionService).ingestTopHeadlines(12);
        verify(autoIngestionControlService).failRun(12);
        verify(autoIngestionEmailNotificationService)
                .sendRunResult(org.mockito.ArgumentMatchers.any(AutoIngestionControlStatusDto.class),
                        org.mockito.ArgumentMatchers.isNull());
        org.assertj.core.api.Assertions.assertThat(running).isNotNull();
        org.assertj.core.api.Assertions.assertThat(running.get()).isFalse();
    }

    @Test
    @DisplayName("Scheduled ingestion should fall back to default page size when configured value is invalid")
    void ingestTopHeadlines_fallsBackToDefaultPageSizeWhenInvalid() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 0);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);
        given(newsSourceProviderSelector.isConfigured()).willReturn(true);
        given(autoIngestionControlService.beginScheduledRun(10)).willReturn(AutoIngestionRunCommandResult.STARTED);
        given(newsIngestionService.ingestTopHeadlines(10)).willReturn(List.of());
        given(newsQueryService.getAutoIngestionBatchStatus(10, 0, List.of()))
                .willReturn(new AutoIngestionBatchStatusDto(10, 0, 0, 0, 0, 0, true, List.of()));

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsIngestionService).ingestTopHeadlines(10);
    }

    @Test
    @DisplayName("Scheduled ingestion should skip overlapping local runs")
    void ingestTopHeadlines_skipsWhenLocalRunAlreadyInProgress() {
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(scheduledNewsIngestionJob, "running");
        running.set(true);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsSourceProviderSelector, never()).isConfigured();
        verifyNoInteractions(newsIngestionService);
        verifyNoInteractions(autoIngestionEmailNotificationService);
    }

    @Test
    @DisplayName("Scheduled ingestion should skip when auto-ingestion control already has a run in progress")
    void ingestTopHeadlines_skipsWhenControlServiceReportsAlreadyRunning() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(autoIngestionControlService.isSchedulerEnabled()).willReturn(true);
        given(newsSourceProviderSelector.isConfigured()).willReturn(true);
        given(autoIngestionControlService.beginScheduledRun(12)).willReturn(AutoIngestionRunCommandResult.ALREADY_RUNNING);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsIngestionService, never()).ingestTopHeadlines(12);
        verifyNoInteractions(autoIngestionEmailNotificationService);
    }

    private NewsEvent sampleNewsEvent(String id) {
        return new NewsEvent(
                id,
                "external-" + id,
                "Title " + id,
                "Summary",
                "Reuters",
                "https://example.com/" + id,
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-13T00:01:00Z"),
                null,
                null
        );
    }
}
