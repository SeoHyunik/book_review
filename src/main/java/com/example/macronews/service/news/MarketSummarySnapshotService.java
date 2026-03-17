package com.example.macronews.service.news;

import com.example.macronews.domain.MarketSummarySnapshot;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.repository.MarketSummarySnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSummarySnapshotService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final MarketSummarySnapshotRepository marketSummarySnapshotRepository;
    private final AiMarketSummaryService aiMarketSummaryService;

    @Value("${app.featured.market-summary.snapshot-enabled:true}")
    private boolean snapshotEnabled;

    @Value("${app.featured.market-summary.snapshot-read-enabled:true}")
    private boolean snapshotReadEnabled;

    @Value("${app.featured.market-summary.snapshot-max-age-minutes:180}")
    private int snapshotMaxAgeMinutes;

    private Clock clock = DEFAULT_CLOCK;

    public Optional<FeaturedMarketSummaryDto> getLatestValidSummary() {
        if (!snapshotEnabled || !snapshotReadEnabled) {
            return Optional.empty();
        }

        return marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc()
                .filter(this::isFresh)
                .map(this::toDto);
    }

    public Optional<MarketSummarySnapshot> refreshSnapshot() {
        if (!snapshotEnabled) {
            return Optional.empty();
        }

        Optional<FeaturedMarketSummaryDto> generated = aiMarketSummaryService.generateCurrentSummary();
        if (generated.isEmpty()) {
            return Optional.empty();
        }

        MarketSummarySnapshot snapshot = new MarketSummarySnapshot(
                null,
                generated.get().generatedAt(),
                generated.get().windowHours(),
                generated.get().sourceCount(),
                generated.get().fromPublishedAt(),
                generated.get().toPublishedAt(),
                generated.get().headlineKo(),
                generated.get().headlineEn(),
                generated.get().summaryKo(),
                generated.get().summaryEn(),
                generated.get().marketViewKo(),
                generated.get().marketViewEn(),
                generated.get().dominantSentiment(),
                generated.get().keyDrivers(),
                generated.get().supportingNewsIds(),
                generated.get().confidence(),
                true,
                true,
                aiMarketSummaryService.getConfiguredModel()
        );
        return Optional.of(marketSummarySnapshotRepository.save(snapshot));
    }

    boolean isFresh(MarketSummarySnapshot snapshot) {
        if (snapshot == null || snapshot.generatedAt() == null) {
            return false;
        }
        return !snapshot.generatedAt()
                .plus(resolveMaxAge())
                .isBefore(Instant.now(clock));
    }

    FeaturedMarketSummaryDto toDto(MarketSummarySnapshot snapshot) {
        return new FeaturedMarketSummaryDto(
                snapshot.headlineKo(),
                snapshot.headlineEn(),
                snapshot.summaryKo(),
                snapshot.summaryEn(),
                snapshot.generatedAt(),
                snapshot.sourceCount(),
                snapshot.windowHours(),
                snapshot.fromPublishedAt(),
                snapshot.toPublishedAt(),
                snapshot.dominantSentiment(),
                snapshot.keyDrivers(),
                snapshot.supportingNewsIds(),
                snapshot.marketViewKo(),
                snapshot.marketViewEn(),
                snapshot.confidence(),
                snapshot.aiSynthesized()
        );
    }

    private Duration resolveMaxAge() {
        return Duration.ofMinutes(snapshotMaxAgeMinutes > 0 ? snapshotMaxAgeMinutes : 180L);
    }
}
