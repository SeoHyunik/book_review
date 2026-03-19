package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.MarketSummarySnapshot;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.MarketSummarySnapshotRepository;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MarketSummarySnapshotServiceTest {

    @Mock
    private MarketSummarySnapshotRepository marketSummarySnapshotRepository;

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private AiMarketSummaryService aiMarketSummaryService;

    @Mock
    private NewsQueryService newsQueryService;

    @InjectMocks
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketSummarySnapshotService, "clock",
                Clock.fixed(Instant.parse("2026-03-17T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        ReflectionTestUtils.setField(marketSummarySnapshotService, "snapshotEnabled", true);
        ReflectionTestUtils.setField(marketSummarySnapshotService, "snapshotReadEnabled", true);
        ReflectionTestUtils.setField(marketSummarySnapshotService, "snapshotMaxAgeMinutes", 180);
    }

    @Test
    @DisplayName("snapshot generation success should save snapshot")
    void refreshSnapshot_savesGeneratedSnapshot() {
        FeaturedMarketSummaryDto generated = summaryDto(Instant.parse("2026-03-17T03:00:00Z"));
        given(aiMarketSummaryService.generateCurrentSummary()).willReturn(Optional.of(generated));
        given(aiMarketSummaryService.getConfiguredModel()).willReturn("gpt-4o");
        given(marketSummarySnapshotRepository.save(org.mockito.ArgumentMatchers.any(MarketSummarySnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var result = marketSummarySnapshotService.refreshSnapshot();

        assertThat(result).isPresent();
        assertThat(result.get().headlineEn()).isEqualTo("AI market snapshot");
        verify(marketSummarySnapshotRepository).save(org.mockito.ArgumentMatchers.any(MarketSummarySnapshot.class));
    }

    @Test
    @DisplayName("latest valid snapshot should be returned when still fresh")
    void getLatestValidSummary_returnsFreshSnapshot() {
        MarketSummarySnapshot snapshot = snapshot(Instant.parse("2026-03-17T02:00:00Z"));
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc())
                .willReturn(Optional.of(snapshot));

        var result = marketSummarySnapshotService.getLatestValidSummary();

        assertThat(result).isPresent();
        assertThat(result.get().headlineEn()).isEqualTo("Stored snapshot");
    }

    @Test
    @DisplayName("stale snapshot should be ignored")
    void getLatestValidSummary_ignoresStaleSnapshot() {
        MarketSummarySnapshot snapshot = snapshot(Instant.parse("2026-03-16T20:00:00Z"));
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc())
                .willReturn(Optional.of(snapshot));

        assertThat(marketSummarySnapshotService.getLatestValidSummary()).isEmpty();
    }

    @Test
    @DisplayName("snapshot generation failure should not save anything")
    void refreshSnapshot_returnsEmptyWhenGenerationFails() {
        given(aiMarketSummaryService.generateCurrentSummary()).willReturn(Optional.empty());

        assertThat(marketSummarySnapshotService.refreshSnapshot()).isEmpty();
        verifyNoInteractions(marketSummarySnapshotRepository);
    }

    @Test
    @DisplayName("scheduled refresh should run when no previous valid snapshot exists")
    void evaluateScheduledRefresh_runsWhenNoPreviousSnapshotExists() {
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc()).willReturn(Optional.empty());
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED)).willReturn(List.of(analyzedNews(
                "news-1", Instant.parse("2026-03-17T02:45:00Z"), Instant.parse("2026-03-17T02:50:00Z"))));

        var result = marketSummarySnapshotService.evaluateScheduledRefresh();

        assertThat(result.shouldRefresh()).isTrue();
        assertThat(result.reason()).isEqualTo("no-previous-valid-snapshot");
    }

    @Test
    @DisplayName("scheduled refresh should skip when no newer analyzed news exists")
    void evaluateScheduledRefresh_skipsWhenNoNewerAnalyzedNewsExists() {
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc())
                .willReturn(Optional.of(snapshot(Instant.parse("2026-03-17T03:00:00Z"))));
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED)).willReturn(List.of(
                analyzedNews("news-1", Instant.parse("2026-03-17T02:00:00Z"), Instant.parse("2026-03-17T02:10:00Z")),
                analyzedNews("news-2", Instant.parse("2026-03-17T02:20:00Z"), Instant.parse("2026-03-17T02:30:00Z"))
        ));

        var result = marketSummarySnapshotService.evaluateScheduledRefresh();

        assertThat(result.shouldRefresh()).isFalse();
        assertThat(result.reason()).isEqualTo("no-new-analyzed-news-since-latest-valid-snapshot");
    }

    @Test
    @DisplayName("scheduled refresh should run when newer analyzed news exists")
    void evaluateScheduledRefresh_runsWhenNewerAnalyzedNewsExists() {
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc())
                .willReturn(Optional.of(snapshot(Instant.parse("2026-03-17T02:30:00Z"))));
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED)).willReturn(List.of(
                analyzedNews("news-1", Instant.parse("2026-03-17T02:00:00Z"), Instant.parse("2026-03-17T02:10:00Z")),
                analyzedNews("news-2", Instant.parse("2026-03-17T02:45:00Z"), Instant.parse("2026-03-17T02:50:00Z"))
        ));

        var result = marketSummarySnapshotService.evaluateScheduledRefresh();

        assertThat(result.shouldRefresh()).isTrue();
        assertThat(result.reason()).isEqualTo("new-analyzed-news-detected");
    }

    @Test
    @DisplayName("scheduled refresh should skip safely when analyzed news basis timestamps are missing")
    void evaluateScheduledRefresh_skipsSafelyWhenAnalyzedNewsBasisMissing() {
        given(marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc())
                .willReturn(Optional.of(snapshot(Instant.parse("2026-03-17T02:30:00Z"))));
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED)).willReturn(List.of(
                new NewsEvent(
                        "news-1",
                        null,
                        "title",
                        "summary",
                        "source",
                        "https://example.com/news-1",
                        Instant.parse("2026-03-17T02:00:00Z"),
                        null,
                        NewsStatus.ANALYZED,
                        new AnalysisResult("test-model", null, null, null, null, null, List.of(), List.of())
                )
        ));

        var result = marketSummarySnapshotService.evaluateScheduledRefresh();

        assertThat(result.shouldRefresh()).isFalse();
        assertThat(result.reason()).isEqualTo("no-analyzed-news-since-latest-valid-snapshot");
    }

    private FeaturedMarketSummaryDto summaryDto(Instant generatedAt) {
        return new FeaturedMarketSummaryDto(
                "AI market snapshot ko",
                "AI market snapshot",
                "Summary ko",
                "Summary en",
                generatedAt,
                3,
                3,
                Instant.parse("2026-03-17T00:30:00Z"),
                Instant.parse("2026-03-17T02:30:00Z"),
                SignalSentiment.POSITIVE,
                List.of("USD", "Oil"),
                List.of("news-1", "news-2"),
                "view ko",
                "view en",
                0.8d,
                true,
                null
        );
    }

    @Test
    @DisplayName("snapshot detail should map supporting news and keep order")
    void getSnapshotDetail_mapsSupportingNews() {
        MarketSummarySnapshot snapshot = snapshot(Instant.parse("2026-03-17T02:00:00Z"));
        given(marketSummarySnapshotRepository.findById("snapshot-1")).willReturn(Optional.of(snapshot));
        given(newsQueryService.getNewsItemsByIds(List.of("news-1", "news-2"))).willReturn(List.of(
                new NewsListItemDto(
                        "news-1", "title-1", "display-1", "Yonhap", Instant.parse("2026-03-17T02:10:00Z"),
                        Instant.parse("2026-03-17T02:12:00Z"), com.example.macronews.domain.NewsStatus.ANALYZED,
                        true, true, com.example.macronews.domain.ImpactDirection.UP, SignalSentiment.POSITIVE,
                        "KOSPI UP", "summary", 10
                ),
                new NewsListItemDto(
                        "news-2", "title-2", null, "Reuters", Instant.parse("2026-03-17T01:50:00Z"),
                        Instant.parse("2026-03-17T01:55:00Z"), com.example.macronews.domain.NewsStatus.ANALYZED,
                        true, true, com.example.macronews.domain.ImpactDirection.DOWN, SignalSentiment.NEGATIVE,
                        "USD UP", "summary", 9
                )
        ));

        var result = marketSummarySnapshotService.getSnapshotDetail("snapshot-1");

        assertThat(result).isPresent();
        assertThat(result.get().supportingNews()).hasSize(2);
        assertThat(result.get().supportingNews().get(0).id()).isEqualTo("news-1");
        assertThat(result.get().supportingNews().get(0).title()).isEqualTo("display-1");
        assertThat(result.get().supportingNews().get(1).title()).isEqualTo("title-2");
    }

    @Test
    @DisplayName("snapshot detail should ignore missing supporting news safely")
    void getSnapshotDetail_handlesMissingSupportingNews() {
        MarketSummarySnapshot snapshot = snapshot(Instant.parse("2026-03-17T02:00:00Z"));
        given(marketSummarySnapshotRepository.findById("snapshot-1")).willReturn(Optional.of(snapshot));
        given(newsQueryService.getNewsItemsByIds(List.of("news-1", "news-2"))).willReturn(List.of());

        var result = marketSummarySnapshotService.getSnapshotDetail("snapshot-1");

        assertThat(result).isPresent();
        assertThat(result.get().supportingNews()).isEmpty();
    }

    private MarketSummarySnapshot snapshot(Instant generatedAt) {
        return new MarketSummarySnapshot(
                "snapshot-1",
                generatedAt,
                3,
                3,
                Instant.parse("2026-03-17T00:30:00Z"),
                Instant.parse("2026-03-17T02:30:00Z"),
                "Stored snapshot ko",
                "Stored snapshot",
                "Stored summary ko",
                "Stored summary en",
                "Stored view ko",
                "Stored view en",
                SignalSentiment.NEGATIVE,
                List.of("USD", "Volatility"),
                List.of("news-1", "news-2"),
                0.7d,
                true,
                true,
                "gpt-4o"
        );
    }

    private NewsEvent analyzedNews(String id, Instant publishedAt, Instant analyzedAt) {
        return new NewsEvent(
                id,
                null,
                "Title " + id,
                "Summary " + id,
                "Source",
                "https://example.com/" + id,
                publishedAt,
                analyzedAt,
                NewsStatus.ANALYZED,
                new AnalysisResult(
                        "test-model",
                        analyzedAt,
                        "headline ko",
                        "headline en",
                        "summary ko",
                        "summary en",
                        List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.UP, 0.8d)),
                        List.of()
                )
        );
    }
}
