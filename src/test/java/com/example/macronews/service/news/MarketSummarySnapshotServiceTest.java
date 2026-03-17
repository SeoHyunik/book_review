package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.MarketSummarySnapshot;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.repository.MarketSummarySnapshotRepository;
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
    private AiMarketSummaryService aiMarketSummaryService;

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
                true
        );
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
}
