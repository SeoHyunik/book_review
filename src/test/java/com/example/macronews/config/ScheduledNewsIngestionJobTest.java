package com.example.macronews.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.service.news.NewsApiService;
import com.example.macronews.service.news.NewsIngestionService;
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
    private NewsApiService newsApiService;

    @InjectMocks
    private ScheduledNewsIngestionJob scheduledNewsIngestionJob;

    @Test
    @DisplayName("Scheduled ingestion should skip when News API is not configured")
    void ingestTopHeadlines_skipsWhenNewsApiIsNotConfigured() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(newsApiService.isConfigured()).willReturn(false);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsApiService).isConfigured();
        verifyNoInteractions(newsIngestionService);
    }

    @Test
    @DisplayName("Scheduled ingestion should use configured page size when enabled")
    void ingestTopHeadlines_usesConfiguredPageSize() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(newsApiService.isConfigured()).willReturn(true);
        given(newsIngestionService.ingestTopHeadlines(12)).willReturn(List.of(sampleNewsEvent("event-1")));

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsApiService).isConfigured();
        verify(newsIngestionService).ingestTopHeadlines(12);
    }

    @Test
    @DisplayName("Scheduled ingestion should keep running flag clear after failures")
    void ingestTopHeadlines_clearsRunningFlagAfterFailure() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 12);
        given(newsApiService.isConfigured()).willReturn(true);
        given(newsIngestionService.ingestTopHeadlines(12)).willThrow(new IllegalStateException("boom"));

        scheduledNewsIngestionJob.ingestTopHeadlines();

        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(scheduledNewsIngestionJob, "running");
        verify(newsIngestionService).ingestTopHeadlines(12);
        org.assertj.core.api.Assertions.assertThat(running).isNotNull();
        org.assertj.core.api.Assertions.assertThat(running.get()).isFalse();
    }

    @Test
    @DisplayName("Scheduled ingestion should fall back to default page size when configured value is invalid")
    void ingestTopHeadlines_fallsBackToDefaultPageSizeWhenInvalid() {
        ReflectionTestUtils.setField(scheduledNewsIngestionJob, "pageSize", 0);
        given(newsApiService.isConfigured()).willReturn(true);
        given(newsIngestionService.ingestTopHeadlines(10)).willReturn(List.of());

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsIngestionService).ingestTopHeadlines(10);
    }

    @Test
    @DisplayName("Scheduled ingestion should skip overlapping local runs")
    void ingestTopHeadlines_skipsWhenLocalRunAlreadyInProgress() {
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(scheduledNewsIngestionJob, "running");
        running.set(true);

        scheduledNewsIngestionJob.ingestTopHeadlines();

        verify(newsApiService, never()).isConfigured();
        verifyNoInteractions(newsIngestionService);
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
