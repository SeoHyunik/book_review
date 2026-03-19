package com.example.macronews.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.example.macronews.service.news.MarketSummarySnapshotService;
import java.time.Instant;
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
class ScheduledMarketSummarySnapshotJobTest {

    @Mock
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @InjectMocks
    private ScheduledMarketSummarySnapshotJob scheduledMarketSummarySnapshotJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduledMarketSummarySnapshotJob, "snapshotEnabled", true);
        ReflectionTestUtils.setField(scheduledMarketSummarySnapshotJob, "refreshEnabled", true);
    }

    @Test
    @DisplayName("scheduled refresh should delegate when enabled")
    void refreshSnapshot_delegatesWhenEnabled() {
        given(marketSummarySnapshotService.evaluateScheduledRefresh())
                .willReturn(MarketSummarySnapshotService.ScheduledRefreshDecision.run(
                        "new-analyzed-news-detected",
                        Instant.parse("2026-03-17T02:00:00Z"),
                        Instant.parse("2026-03-17T02:30:00Z")));
        given(marketSummarySnapshotService.refreshSnapshot()).willReturn(Optional.empty());

        scheduledMarketSummarySnapshotJob.refreshSnapshot();

        verify(marketSummarySnapshotService).refreshSnapshot();
    }

    @Test
    @DisplayName("scheduled refresh should skip when disabled")
    void refreshSnapshot_skipsWhenDisabled() {
        ReflectionTestUtils.setField(scheduledMarketSummarySnapshotJob, "refreshEnabled", false);

        scheduledMarketSummarySnapshotJob.refreshSnapshot();

        verify(marketSummarySnapshotService, never()).refreshSnapshot();
    }

    @Test
    @DisplayName("scheduled refresh should skip when no newer analyzed news exists")
    void refreshSnapshot_skipsWhenNoNewAnalyzedNewsExists() {
        given(marketSummarySnapshotService.evaluateScheduledRefresh())
                .willReturn(MarketSummarySnapshotService.ScheduledRefreshDecision.skip(
                        "no-new-analyzed-news-since-latest-valid-snapshot",
                        Instant.parse("2026-03-17T02:00:00Z"),
                        Instant.parse("2026-03-17T01:45:00Z")));

        scheduledMarketSummarySnapshotJob.refreshSnapshot();

        verify(marketSummarySnapshotService, never()).refreshSnapshot();
    }
}
