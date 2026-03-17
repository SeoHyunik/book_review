package com.example.macronews.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.service.news.MarketSummarySnapshotService;
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
}
