package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsIngestionServiceImplTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private NewsSourceProviderSelector newsSourceProviderSelector;

    @Mock
    private MacroAiService macroAiService;

    @Mock
    private Executor ingestionExecutor;

    @InjectMocks
    private NewsIngestionServiceImpl newsIngestionService;

    @Test
    @DisplayName("ingestExternalItem should preserve a real summary")
    void ingestExternalItem_preservesRealSummary() {
        ExternalNewsItem item = new ExternalNewsItem(
                "external-1",
                "Reuters",
                "Fed keeps rates unchanged",
                "Officials signaled a cautious stance while watching inflation data.",
                "https://example.com/news-1",
                Instant.parse("2026-03-13T00:00:00Z")
        );
        given(newsEventRepository.findByExternalId("external-1")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.findByUrl("https://example.com/news-1")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.save(any(NewsEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        NewsEvent saved = newsIngestionService.ingestExternalItem(item);

        assertThat(saved.summary()).isEqualTo("Officials signaled a cautious stance while watching inflation data.");
        verifyNoInteractions(macroAiService, newsSourceProviderSelector, ingestionExecutor);
    }

    @Test
    @DisplayName("ingestExternalItem should persist blank summary as empty")
    void ingestExternalItem_persistsBlankSummaryAsEmpty() {
        ExternalNewsItem item = new ExternalNewsItem(
                "external-2",
                "Reuters",
                "Fed keeps rates unchanged",
                "   ",
                "https://example.com/news-2",
                Instant.parse("2026-03-13T00:00:00Z")
        );
        given(newsEventRepository.findByExternalId("external-2")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.findByUrl("https://example.com/news-2")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.save(any(NewsEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        NewsEvent saved = newsIngestionService.ingestExternalItem(item);

        assertThat(saved.summary()).isEmpty();
    }

    @Test
    @DisplayName("ingestExternalItem should persist title-equal summary as empty")
    void ingestExternalItem_persistsTitleEqualSummaryAsEmpty() {
        ExternalNewsItem item = new ExternalNewsItem(
                "external-3",
                "Reuters",
                "Fed keeps rates unchanged",
                "Fed keeps rates unchanged",
                "https://example.com/news-3",
                Instant.parse("2026-03-13T00:00:00Z")
        );
        given(newsEventRepository.findByExternalId("external-3")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.findByUrl("https://example.com/news-3")).willReturn(java.util.Optional.empty());
        given(newsEventRepository.save(any(NewsEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        NewsEvent saved = newsIngestionService.ingestExternalItem(item);

        assertThat(saved.summary()).isEmpty();
    }

    @Test
    @DisplayName("deleteById should delete existing news item")
    void deleteById_deletesExistingItem() {
        given(newsEventRepository.existsById("news-1")).willReturn(true);

        boolean deleted = newsIngestionService.deleteById("news-1");

        assertThat(deleted).isTrue();
        verify(newsEventRepository).deleteById("news-1");
    }

    @Test
    @DisplayName("deleteById should return false when item is missing")
    void deleteById_returnsFalseWhenMissing() {
        given(newsEventRepository.existsById("missing-news")).willReturn(false);

        boolean deleted = newsIngestionService.deleteById("missing-news");

        assertThat(deleted).isFalse();
        verify(newsEventRepository, never()).deleteById("missing-news");
    }

    @Test
    @DisplayName("ingestTopHeadlines should delegate headline loading to the selector")
    void ingestTopHeadlines_delegatesHeadlineLoadingToSelector() {
        given(newsSourceProviderSelector.fetchTopHeadlines(3)).willReturn(java.util.List.of());

        newsIngestionService.ingestTopHeadlines(3);

        verify(newsSourceProviderSelector).fetchTopHeadlines(3);
        verify(newsEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("ingestTopHeadlines should keep the selected source summary when freshness removes everything")
    void ingestTopHeadlines_logsSelectedSourcesWhenFreshnessRemovesEverything() {
        ExternalNewsItem staleItem = new ExternalNewsItem(
                "external-stale",
                "NAVER",
                "Old headline",
                "Old headline summary",
                "https://example.com/stale",
                Instant.parse("2026-03-01T00:00:00Z"));
        given(newsSourceProviderSelector.fetchTopHeadlines(3)).willReturn(List.of(staleItem));

        List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(3);

        assertThat(ingested).isEmpty();
        assertThat(newsIngestionService.buildSelectionSummaryLog(
                java.util.Map.of("NAVER", 1),
                java.util.Map.of(),
                "freshness-gate-removed-all",
                1,
                0,
                1)).contains("selected sourceSummary={NAVER=1}")
                .contains("keptSourceSummary={}")
                .contains("finalCause=freshness-gate-removed-all");
        verify(newsSourceProviderSelector).fetchTopHeadlines(3);
        verifyNoInteractions(newsEventRepository, macroAiService, ingestionExecutor);
    }

    @Test
    @DisplayName("retryFailedAnalyses should reserve and submit only eligible failed items")
    void retryFailedAnalyses_reservesAndSubmitsEligibleItems() {
        Instant now = Instant.parse("2026-03-24T10:00:00Z");
        ReflectionTestUtils.setField(newsIngestionService, "clock", java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(newsIngestionService, "maxAnalysisRetries", 2);
        ReflectionTestUtils.setField(newsIngestionService, "analysisRetryMinDelayMinutes", 60L);
        NewsEvent eligible = failedEvent("eligible", 0, now.minusSeconds(7200));
        NewsEvent recentFailure = failedEvent("recent", 0, now.minusSeconds(600));
        NewsEvent exhausted = failedEvent("exhausted", 2, now.minusSeconds(7200));
        given(newsEventRepository.findByStatus(NewsStatus.FAILED)).willReturn(List.of(eligible, recentFailure, exhausted));
        given(newsEventRepository.save(any(NewsEvent.class))).willAnswer(invocation -> invocation.getArgument(0));
        willAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).given(ingestionExecutor).execute(any(Runnable.class));

        int submitted = newsIngestionService.retryFailedAnalyses();

        assertThat(submitted).isEqualTo(1);
        verify(newsEventRepository).save(any(NewsEvent.class));
        verify(macroAiService).interpretAndSave("eligible");
        verify(macroAiService, never()).interpretAndSave("recent");
        verify(macroAiService, never()).interpretAndSave("exhausted");
    }

    @Test
    @DisplayName("retryFailedAnalyses should return zero when no failed item is eligible")
    void retryFailedAnalyses_returnsZeroWhenNoEligibleItems() {
        Instant now = Instant.parse("2026-03-24T10:00:00Z");
        ReflectionTestUtils.setField(newsIngestionService, "clock", java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(newsIngestionService, "maxAnalysisRetries", 2);
        ReflectionTestUtils.setField(newsIngestionService, "analysisRetryMinDelayMinutes", 60L);
        given(newsEventRepository.findByStatus(NewsStatus.FAILED))
                .willReturn(List.of(failedEvent("recent", 0, now.minusSeconds(600))));

        int submitted = newsIngestionService.retryFailedAnalyses();

        assertThat(submitted).isZero();
        verify(newsEventRepository, never()).save(any(NewsEvent.class));
        verify(macroAiService, never()).interpretAndSave(any());
    }

    private NewsEvent failedEvent(String id, Integer retryCount, Instant lastAttemptAt) {
        return new NewsEvent(
                id,
                "external-" + id,
                "Title " + id,
                "Summary",
                "Reuters",
                "https://example.com/" + id,
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-13T00:01:00Z"),
                NewsStatus.FAILED,
                null,
                retryCount,
                lastAttemptAt
        );
    }
}
